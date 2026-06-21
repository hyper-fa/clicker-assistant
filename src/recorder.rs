use crate::script::{Script, ScriptEvent};
use crate::settings::MouseButton;
use std::ptr;
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread::{self, JoinHandle};
use std::time::Instant;
use winapi::shared::minwindef::{LPARAM, LRESULT, WPARAM};
use winapi::shared::windef::HHOOK;
use winapi::um::libloaderapi::GetModuleHandleW;
use winapi::um::winuser::{
    CallNextHookEx, DispatchMessageW, GetMessageW, PostThreadMessageW, SetWindowsHookExW,
    TranslateMessage, UnhookWindowsHookEx, HC_ACTION, KBDLLHOOKSTRUCT, MSG, MSLLHOOKSTRUCT, VK_F8,
    WH_KEYBOARD_LL, WH_MOUSE_LL, WM_KEYDOWN, WM_KEYUP, WM_LBUTTONUP, WM_MBUTTONUP, WM_QUIT,
    WM_RBUTTONUP, WM_SYSKEYDOWN, WM_SYSKEYUP,
};

const SCRIPT_VERSION: u32 = 1;

static ACTIVE_CAPTURE: OnceLock<Mutex<Option<Arc<CaptureState>>>> = OnceLock::new();

pub struct ScriptRecorder {
    capture: Option<Arc<CaptureState>>,
    worker: Option<JoinHandle<()>>,
}

struct CaptureState {
    events: Mutex<Vec<ScriptEvent>>,
    start: Instant,
    ready: AtomicBool,
    failed: Mutex<Option<String>>,
    thread_id: AtomicU32,
}

impl ScriptRecorder {
    pub fn new() -> Self {
        Self {
            capture: None,
            worker: None,
        }
    }

    pub fn is_recording(&self) -> bool {
        self.capture.is_some()
    }

    pub fn start(&mut self) -> Result<(), String> {
        if self.is_recording() {
            return Ok(());
        }

        let state = Arc::new(CaptureState {
            events: Mutex::new(Vec::new()),
            start: Instant::now(),
            ready: AtomicBool::new(false),
            failed: Mutex::new(None),
            thread_id: AtomicU32::new(0),
        });

        set_active_capture(Some(Arc::clone(&state)))?;
        let thread_state = Arc::clone(&state);
        let worker = thread::spawn(move || recorder_thread(thread_state));

        for _ in 0..50 {
            if state.ready.load(Ordering::SeqCst) {
                self.capture = Some(state);
                self.worker = Some(worker);
                return Ok(());
            }
            if let Some(message) = state.failed.lock().ok().and_then(|slot| slot.clone()) {
                let _ = worker.join();
                set_active_capture(None)?;
                return Err(message);
            }
            thread::sleep(std::time::Duration::from_millis(20));
        }

        let thread_id = state.thread_id.load(Ordering::SeqCst);
        if thread_id != 0 {
            unsafe {
                PostThreadMessageW(thread_id, WM_QUIT, 0, 0);
            }
        }
        let _ = worker.join();
        set_active_capture(None)?;
        Err("启动录制超时".to_string())
    }

    pub fn stop(&mut self, name: String) -> Result<Script, String> {
        self.stop_with_filter(name, None)
    }

    pub fn stop_dropping_recent_mouse_clicks(
        &mut self,
        name: String,
        recent_window_ms: u64,
    ) -> Result<Script, String> {
        self.stop_with_filter(name, Some(recent_window_ms))
    }

    fn stop_with_filter(
        &mut self,
        name: String,
        drop_recent_mouse_window_ms: Option<u64>,
    ) -> Result<Script, String> {
        let Some(state) = self.capture.take() else {
            return Err("当前没有正在录制的脚本".to_string());
        };
        let stopped_at_ms = elapsed_ms(&state);

        let thread_id = state.thread_id.load(Ordering::SeqCst);
        if thread_id != 0 {
            unsafe {
                PostThreadMessageW(thread_id, WM_QUIT, 0, 0);
            }
        }

        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
        set_active_capture(None)?;

        let mut events = state
            .events
            .lock()
            .map(|events| events.clone())
            .map_err(|_| "读取录制事件失败".to_string())?;

        if let Some(window_ms) = drop_recent_mouse_window_ms {
            while let Some(ScriptEvent::MouseClick { at_ms, .. }) = events.last() {
                if stopped_at_ms.saturating_sub(*at_ms) <= window_ms {
                    events.pop();
                } else {
                    break;
                }
            }
        }

        Ok(Script {
            version: SCRIPT_VERSION,
            name,
            events,
        })
    }

    pub fn event_count(&self) -> usize {
        self.capture
            .as_ref()
            .and_then(|state| state.events.lock().ok().map(|events| events.len()))
            .unwrap_or(0)
    }
}

impl Drop for ScriptRecorder {
    fn drop(&mut self) {
        if self.is_recording() {
            let _ = self.stop("unsaved".to_string());
        }
    }
}

fn recorder_thread(state: Arc<CaptureState>) {
    state.thread_id.store(
        unsafe { winapi::um::processthreadsapi::GetCurrentThreadId() },
        Ordering::SeqCst,
    );

    let module = unsafe { GetModuleHandleW(ptr::null()) };
    let keyboard_hook =
        unsafe { SetWindowsHookExW(WH_KEYBOARD_LL, Some(keyboard_proc), module, 0) };
    let mouse_hook = unsafe { SetWindowsHookExW(WH_MOUSE_LL, Some(mouse_proc), module, 0) };

    if keyboard_hook.is_null() || mouse_hook.is_null() {
        if !keyboard_hook.is_null() {
            unsafe {
                UnhookWindowsHookEx(keyboard_hook);
            }
        }
        if !mouse_hook.is_null() {
            unsafe {
                UnhookWindowsHookEx(mouse_hook);
            }
        }
        if let Ok(mut failed) = state.failed.lock() {
            *failed = Some("安装录制钩子失败，请尝试以管理员权限运行。".to_string());
        }
        return;
    }

    state.ready.store(true, Ordering::SeqCst);
    let mut message: MSG = unsafe { std::mem::zeroed() };
    while unsafe { GetMessageW(&mut message, ptr::null_mut(), 0, 0) } > 0 {
        unsafe {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }

    unsafe {
        UnhookWindowsHookEx(keyboard_hook);
        UnhookWindowsHookEx(mouse_hook);
    }
}

unsafe extern "system" fn mouse_proc(code: i32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    if code == HC_ACTION {
        let mouse = *(lparam as *const MSLLHOOKSTRUCT);
        let button = match wparam as u32 {
            WM_LBUTTONUP => Some(MouseButton::Left),
            WM_RBUTTONUP => Some(MouseButton::Right),
            WM_MBUTTONUP => Some(MouseButton::Middle),
            _ => None,
        };

        if let Some(button) = button {
            with_active_capture(|state| {
                push_event(
                    state,
                    ScriptEvent::MouseClick {
                        at_ms: elapsed_ms(state),
                        x: mouse.pt.x,
                        y: mouse.pt.y,
                        button,
                    },
                );
            });
        }
    }

    CallNextHookEx(ptr::null_mut() as HHOOK, code, wparam, lparam)
}

unsafe extern "system" fn keyboard_proc(code: i32, wparam: WPARAM, lparam: LPARAM) -> LRESULT {
    if code == HC_ACTION {
        let keyboard = *(lparam as *const KBDLLHOOKSTRUCT);
        let vk = keyboard.vkCode;
        if vk != VK_F8 as u32 {
            let event = match wparam as u32 {
                WM_KEYDOWN | WM_SYSKEYDOWN => Some(ScriptEvent::KeyDown { at_ms: 0, vk }),
                WM_KEYUP | WM_SYSKEYUP => Some(ScriptEvent::KeyUp { at_ms: 0, vk }),
                _ => None,
            };

            if let Some(event) = event {
                with_active_capture(|state| {
                    let at_ms = elapsed_ms(state);
                    let event = match event {
                        ScriptEvent::KeyDown { vk, .. } => ScriptEvent::KeyDown { at_ms, vk },
                        ScriptEvent::KeyUp { vk, .. } => ScriptEvent::KeyUp { at_ms, vk },
                        other => other,
                    };
                    push_event(state, event);
                });
            }
        }
    }

    CallNextHookEx(ptr::null_mut() as HHOOK, code, wparam, lparam)
}

fn set_active_capture(capture: Option<Arc<CaptureState>>) -> Result<(), String> {
    let storage = ACTIVE_CAPTURE.get_or_init(|| Mutex::new(None));
    let mut slot = storage.lock().map_err(|_| "录制状态锁定失败".to_string())?;
    *slot = capture;
    Ok(())
}

fn with_active_capture<F: FnOnce(&CaptureState)>(f: F) {
    if let Some(storage) = ACTIVE_CAPTURE.get() {
        if let Ok(slot) = storage.lock() {
            if let Some(state) = slot.as_deref() {
                f(state);
            }
        }
    }
}

fn push_event(state: &CaptureState, event: ScriptEvent) {
    if let Ok(mut events) = state.events.lock() {
        events.push(event);
    }
}

fn elapsed_ms(state: &CaptureState) -> u64 {
    state.start.elapsed().as_millis() as u64
}
