use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum MouseButton {
    Left,
    Right,
    Middle,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClickMode {
    Single,
    Double,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClickPosition {
    CurrentCursor,
    Fixed { x: i32, y: i32 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct WindowClickTarget {
    pub hwnd: isize,
    pub client_x: i32,
    pub client_y: i32,
    pub screen_x: i32,
    pub screen_y: i32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ClickBackend {
    Foreground,
    Background(WindowClickTarget),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ClickSettings {
    pub button: MouseButton,
    pub mode: ClickMode,
    pub position: ClickPosition,
    pub backend: ClickBackend,
    pub interval_ms: u64,
}

impl Default for ClickSettings {
    fn default() -> Self {
        Self {
            button: MouseButton::Left,
            mode: ClickMode::Single,
            position: ClickPosition::CurrentCursor,
            backend: ClickBackend::Foreground,
            interval_ms: 100,
        }
    }
}
