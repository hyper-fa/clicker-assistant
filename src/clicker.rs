use crate::input;
use crate::settings::{ClickBackend, ClickMode, ClickPosition, ClickSettings};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

pub struct Clicker {
    running: Arc<AtomicBool>,
    worker: Mutex<Option<JoinHandle<()>>>,
}

impl Clicker {
    pub fn new() -> Self {
        Self {
            running: Arc::new(AtomicBool::new(false)),
            worker: Mutex::new(None),
        }
    }

    pub fn is_running(&self) -> bool {
        self.running.load(Ordering::SeqCst)
    }

    pub fn start(&self, settings: ClickSettings) {
        if self.is_running() {
            return;
        }

        self.running.store(true, Ordering::SeqCst);
        let running = Arc::clone(&self.running);
        let handle = thread::spawn(move || run_click_loop(settings, running));
        *self.worker.lock().expect("clicker worker mutex") = Some(handle);
    }

    pub fn stop(&self) {
        self.running.store(false, Ordering::SeqCst);
        if let Some(handle) = self.worker.lock().expect("clicker worker mutex").take() {
            let _ = handle.join();
        }
    }
}

impl Drop for Clicker {
    fn drop(&mut self) {
        self.running.store(false, Ordering::SeqCst);
        if let Ok(mut worker) = self.worker.lock() {
            if let Some(handle) = worker.take() {
                let _ = handle.join();
            }
        }
    }
}

fn run_click_loop(settings: ClickSettings, running: Arc<AtomicBool>) {
    let interval = Duration::from_millis(settings.interval_ms.max(1));

    while running.load(Ordering::SeqCst) {
        perform_click(settings);
        sleep_interruptible(interval, &running);
    }
}

fn perform_click(settings: ClickSettings) {
    if let ClickBackend::Background(target) = settings.backend {
        let _ = if settings.mode == ClickMode::Double {
            input::background_double_click(target, settings.button)
        } else {
            input::background_click(target, settings.button)
        };
        return;
    }

    if let ClickPosition::Fixed { x, y } = settings.position {
        let _ = input::move_to(x, y);
    }

    let _ = input::click(settings.button);

    if settings.mode == ClickMode::Double {
        std::thread::sleep(Duration::from_millis(40));
        let _ = input::click(settings.button);
    }
}

fn sleep_interruptible(duration: Duration, running: &AtomicBool) {
    let started = Instant::now();
    while running.load(Ordering::SeqCst) && started.elapsed() < duration {
        let remaining = duration.saturating_sub(started.elapsed());
        std::thread::sleep(remaining.min(Duration::from_millis(10)));
    }
}
