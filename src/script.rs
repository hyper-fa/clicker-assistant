use crate::input;
use crate::settings::MouseButton;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};
use winapi::um::minwinbase::SYSTEMTIME;
use winapi::um::sysinfoapi::GetLocalTime;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Script {
    pub version: u32,
    pub name: String,
    pub events: Vec<ScriptEvent>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ScriptEvent {
    MouseClick {
        at_ms: u64,
        x: i32,
        y: i32,
        button: MouseButton,
    },
    KeyDown {
        at_ms: u64,
        vk: u32,
    },
    KeyUp {
        at_ms: u64,
        vk: u32,
    },
}

impl Script {
    pub fn event_count(&self) -> usize {
        self.events.len()
    }
}

pub struct ScriptPlayer {
    running: Arc<AtomicBool>,
    worker: Mutex<Option<JoinHandle<()>>>,
}

impl ScriptPlayer {
    pub fn new() -> Self {
        Self {
            running: Arc::new(AtomicBool::new(false)),
            worker: Mutex::new(None),
        }
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    pub fn start<F>(&self, script: Script, loop_enabled: bool, on_finished: F)
    where
        F: FnOnce() + Send + 'static,
    {
        if self.is_running() {
            return;
        }

        self.running.store(true, Ordering::SeqCst);
        let running = Arc::clone(&self.running);
        let handle = thread::spawn(move || {
            run_script_loop(script, loop_enabled, running);
            on_finished();
        });
        *self.worker.lock().expect("script player mutex") = Some(handle);
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
        if let Some(handle) = self.worker.lock().expect("script player mutex").take() {
            let _ = handle.join();
        }
    }

    pub fn detach_finished_worker(&self) {
        if !self.is_running() {
            if let Some(handle) = self.worker.lock().expect("script player mutex").take() {
                let _ = handle.join();
            }
        }
    }
}

impl Drop for ScriptPlayer {
    fn drop(&mut self) {
        self.running.store(false, Ordering::SeqCst);
        if let Ok(mut worker) = self.worker.lock() {
            if let Some(handle) = worker.take() {
                let _ = handle.join();
            }
        }
    }
}

pub fn default_scripts_dir() -> PathBuf {
    match std::env::current_exe()
        .ok()
        .and_then(|path| path.parent().map(Path::to_path_buf))
    {
        Some(dir) => dir.join("scripts"),
        None => PathBuf::from("scripts"),
    }
}

pub fn default_script_path() -> PathBuf {
    default_scripts_dir().join(format!("script-{}.json", timestamp_name()))
}

pub fn save_script(script: &Script, path: &Path) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("创建脚本目录失败: {}", e))?;
    }

    let json =
        serde_json::to_string_pretty(script).map_err(|e| format!("序列化脚本失败: {}", e))?;
    fs::write(path, json).map_err(|e| format!("保存脚本失败: {}", e))
}

pub fn load_script(path: &Path) -> Result<Script, String> {
    let raw = fs::read_to_string(path).map_err(|e| format!("读取脚本失败: {}", e))?;
    let script: Script = serde_json::from_str(&raw).map_err(|e| format!("解析脚本失败: {}", e))?;
    if script.version != 1 {
        return Err(format!("不支持的脚本版本: {}", script.version));
    }
    Ok(script)
}

fn run_script_loop(script: Script, loop_enabled: bool, running: Arc<AtomicBool>) {
    while running.load(Ordering::SeqCst) {
        run_script_once(&script, &running);
        if !loop_enabled {
            break;
        }
    }

    running.store(false, Ordering::SeqCst);
}

fn run_script_once(script: &Script, running: &AtomicBool) {
    let started = Instant::now();

    for event in &script.events {
        if !running.load(Ordering::SeqCst) {
            break;
        }

        let at_ms = event_at_ms(event);
        let elapsed = started.elapsed().as_millis() as u64;
        if at_ms > elapsed {
            sleep_interruptible(Duration::from_millis(at_ms - elapsed), running);
        }
        if !running.load(Ordering::SeqCst) {
            break;
        }

        perform_event(event);
    }
}

fn perform_event(event: &ScriptEvent) {
    match *event {
        ScriptEvent::MouseClick { x, y, button, .. } => {
            let _ = input::click_at(x, y, button);
        }
        ScriptEvent::KeyDown { vk, .. } => {
            let _ = input::key_down(vk);
        }
        ScriptEvent::KeyUp { vk, .. } => {
            let _ = input::key_up(vk);
        }
    }
}

fn event_at_ms(event: &ScriptEvent) -> u64 {
    match *event {
        ScriptEvent::MouseClick { at_ms, .. }
        | ScriptEvent::KeyDown { at_ms, .. }
        | ScriptEvent::KeyUp { at_ms, .. } => at_ms,
    }
}

fn sleep_interruptible(duration: Duration, running: &AtomicBool) {
    let started = Instant::now();
    while running.load(Ordering::SeqCst) && started.elapsed() < duration {
        let remaining = duration.saturating_sub(started.elapsed());
        thread::sleep(remaining.min(Duration::from_millis(10)));
    }
}

fn timestamp_name() -> String {
    let mut time: SYSTEMTIME = unsafe { std::mem::zeroed() };
    unsafe {
        GetLocalTime(&mut time);
    }
    format!(
        "{:04}{:02}{:02}-{:02}{:02}{:02}",
        time.wYear, time.wMonth, time.wDay, time.wHour, time.wMinute, time.wSecond
    )
}
