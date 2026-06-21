use crate::settings::{MouseButton, WindowClickTarget};
use std::mem::size_of;
use winapi::ctypes::c_int;
use winapi::shared::basetsd::ULONG_PTR;
use winapi::shared::minwindef::{FALSE, LPARAM, WPARAM};
use winapi::shared::windef::{HWND, POINT};
use winapi::um::errhandlingapi::GetLastError;
use winapi::um::winbase::{
    FormatMessageW, FORMAT_MESSAGE_FROM_SYSTEM, FORMAT_MESSAGE_IGNORE_INSERTS,
};
use winapi::um::winuser::{
    ChildWindowFromPointEx, GetCursorPos, GetSystemMetrics, INPUT_u, IsWindow, PostMessageW,
    ScreenToClient, SendInput, WindowFromPoint, CWP_SKIPDISABLED, CWP_SKIPINVISIBLE, INPUT,
    INPUT_KEYBOARD, INPUT_MOUSE, KEYBDINPUT, KEYEVENTF_KEYUP, LPINPUT, MK_LBUTTON, MK_MBUTTON,
    MK_RBUTTON, MOUSEEVENTF_ABSOLUTE, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP,
    MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP, MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN,
    MOUSEEVENTF_RIGHTUP, MOUSEEVENTF_VIRTUALDESK, MOUSEINPUT, SM_CXVIRTUALSCREEN,
    SM_CYVIRTUALSCREEN, SM_XVIRTUALSCREEN, SM_YVIRTUALSCREEN, WM_LBUTTONDBLCLK, WM_LBUTTONDOWN,
    WM_LBUTTONUP, WM_MBUTTONDBLCLK, WM_MBUTTONDOWN, WM_MBUTTONUP, WM_MOUSEMOVE, WM_NULL,
    WM_RBUTTONDBLCLK, WM_RBUTTONDOWN, WM_RBUTTONUP,
};

const INPUT_EXTRA_VALUE: ULONG_PTR = 100;

pub fn cursor_position() -> Result<(i32, i32), String> {
    let mut point = POINT { x: 0, y: 0 };
    let ok = unsafe { GetCursorPos(&mut point) };
    if ok == FALSE {
        Err(last_error_message())
    } else {
        Ok((point.x, point.y))
    }
}

pub fn click(button: MouseButton) -> Result<(), String> {
    mouse_down(button)?;
    mouse_up(button)
}

pub fn click_at(x: i32, y: i32, button: MouseButton) -> Result<(), String> {
    move_to(x, y)?;
    click(button)
}

pub fn key_down(vk: u32) -> Result<(), String> {
    send_keyboard_event(vk, 0)
}

pub fn key_up(vk: u32) -> Result<(), String> {
    send_keyboard_event(vk, KEYEVENTF_KEYUP)
}

pub fn move_to(x: i32, y: i32) -> Result<(), String> {
    let left = unsafe { GetSystemMetrics(SM_XVIRTUALSCREEN) };
    let top = unsafe { GetSystemMetrics(SM_YVIRTUALSCREEN) };
    let width = unsafe { GetSystemMetrics(SM_CXVIRTUALSCREEN) };
    let height = unsafe { GetSystemMetrics(SM_CYVIRTUALSCREEN) };

    if width <= 0 || height <= 0 {
        return Err("Invalid virtual screen size".to_string());
    }

    let dx = (x - left) * 65535 / width;
    let dy = (y - top) * 65535 / height;
    send_mouse_event(
        MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK,
        0,
        dx,
        dy,
    )
}

pub fn capture_window_target() -> Result<WindowClickTarget, String> {
    let mut screen_point = POINT { x: 0, y: 0 };
    let ok = unsafe { GetCursorPos(&mut screen_point) };
    if ok == FALSE {
        return Err(last_error_message());
    }

    let root = unsafe { WindowFromPoint(screen_point) };
    if root.is_null() {
        return Err("未找到鼠标下方的目标窗口".to_string());
    }

    let mut client_point = screen_point;
    let mut target = root;
    if unsafe { ScreenToClient(root, &mut client_point) } != FALSE {
        let child = unsafe {
            ChildWindowFromPointEx(root, client_point, CWP_SKIPINVISIBLE | CWP_SKIPDISABLED)
        };
        if !child.is_null() {
            target = child;
        }
    }

    let mut target_client_point = screen_point;
    if unsafe { ScreenToClient(target, &mut target_client_point) } == FALSE {
        return Err(last_error_message());
    }

    let result = WindowClickTarget {
        hwnd: target as isize,
        client_x: target_client_point.x,
        client_y: target_client_point.y,
        screen_x: screen_point.x,
        screen_y: screen_point.y,
    };
    validate_window_target(result)?;
    Ok(result)
}

pub fn validate_window_target(target: WindowClickTarget) -> Result<(), String> {
    let hwnd = target.hwnd as HWND;
    if hwnd.is_null() || unsafe { IsWindow(hwnd) } == FALSE {
        return Err("后台目标窗口已经不存在，请重新设置后台目标。".to_string());
    }

    if post_message(hwnd, WM_NULL, 0, 0).is_err() {
        return Err("无法向后台目标窗口发送消息，请确认目标窗口未以更高权限运行。".to_string());
    }

    Ok(())
}

pub fn background_click(target: WindowClickTarget, button: MouseButton) -> Result<(), String> {
    validate_window_target(target)?;
    post_mouse_message(target, WM_MOUSEMOVE, 0)?;

    let (down, up, modifier) = button_messages(button);
    post_mouse_message(target, down, modifier)?;
    post_mouse_message(target, up, 0)
}

pub fn background_double_click(
    target: WindowClickTarget,
    button: MouseButton,
) -> Result<(), String> {
    validate_window_target(target)?;
    post_mouse_message(target, WM_MOUSEMOVE, 0)?;

    let (down, up, modifier) = button_messages(button);
    let dblclk = double_click_message(button);
    post_mouse_message(target, down, modifier)?;
    post_mouse_message(target, up, 0)?;
    std::thread::sleep(std::time::Duration::from_millis(40));
    post_mouse_message(target, dblclk, modifier)?;
    post_mouse_message(target, up, 0)
}

fn mouse_down(button: MouseButton) -> Result<(), String> {
    send_mouse_event(
        match button {
            MouseButton::Left => MOUSEEVENTF_LEFTDOWN,
            MouseButton::Right => MOUSEEVENTF_RIGHTDOWN,
            MouseButton::Middle => MOUSEEVENTF_MIDDLEDOWN,
        },
        0,
        0,
        0,
    )
}

fn mouse_up(button: MouseButton) -> Result<(), String> {
    send_mouse_event(
        match button {
            MouseButton::Left => MOUSEEVENTF_LEFTUP,
            MouseButton::Right => MOUSEEVENTF_RIGHTUP,
            MouseButton::Middle => MOUSEEVENTF_MIDDLEUP,
        },
        0,
        0,
        0,
    )
}

fn send_mouse_event(flags: u32, data: u32, dx: i32, dy: i32) -> Result<(), String> {
    let mut input_union: INPUT_u = unsafe { std::mem::zeroed() };
    unsafe {
        *input_union.mi_mut() = MOUSEINPUT {
            dx,
            dy,
            mouseData: data,
            dwFlags: flags,
            time: 0,
            dwExtraInfo: INPUT_EXTRA_VALUE,
        };
    }

    let mut input = INPUT {
        type_: INPUT_MOUSE,
        u: input_union,
    };

    let sent = unsafe { SendInput(1, &mut input as LPINPUT, size_of::<INPUT>() as c_int) };
    if sent == 0 {
        Err(last_error_message())
    } else {
        Ok(())
    }
}

fn send_keyboard_event(vk: u32, flags: u32) -> Result<(), String> {
    let mut input_union: INPUT_u = unsafe { std::mem::zeroed() };
    unsafe {
        *input_union.ki_mut() = KEYBDINPUT {
            wVk: vk as u16,
            wScan: 0,
            dwFlags: flags,
            time: 0,
            dwExtraInfo: INPUT_EXTRA_VALUE,
        };
    }

    let mut input = INPUT {
        type_: INPUT_KEYBOARD,
        u: input_union,
    };

    let sent = unsafe { SendInput(1, &mut input as LPINPUT, size_of::<INPUT>() as c_int) };
    if sent == 0 {
        Err(last_error_message())
    } else {
        Ok(())
    }
}

fn button_messages(button: MouseButton) -> (u32, u32, WPARAM) {
    match button {
        MouseButton::Left => (WM_LBUTTONDOWN, WM_LBUTTONUP, MK_LBUTTON),
        MouseButton::Right => (WM_RBUTTONDOWN, WM_RBUTTONUP, MK_RBUTTON),
        MouseButton::Middle => (WM_MBUTTONDOWN, WM_MBUTTONUP, MK_MBUTTON),
    }
}

fn double_click_message(button: MouseButton) -> u32 {
    match button {
        MouseButton::Left => WM_LBUTTONDBLCLK,
        MouseButton::Right => WM_RBUTTONDBLCLK,
        MouseButton::Middle => WM_MBUTTONDBLCLK,
    }
}

fn post_mouse_message(
    target: WindowClickTarget,
    message: u32,
    modifier: WPARAM,
) -> Result<(), String> {
    post_message(
        target.hwnd as HWND,
        message,
        modifier,
        make_lparam(target.client_x, target.client_y),
    )
}

fn post_message(hwnd: HWND, message: u32, wparam: WPARAM, lparam: LPARAM) -> Result<(), String> {
    let ok = unsafe { PostMessageW(hwnd, message, wparam, lparam) };
    if ok == FALSE {
        Err(last_error_message())
    } else {
        Ok(())
    }
}

fn make_lparam(x: i32, y: i32) -> LPARAM {
    ((y as u16 as u32) << 16 | (x as u16 as u32)) as LPARAM
}

fn last_error_message() -> String {
    unsafe {
        let errno = GetLastError();
        if errno == 0 {
            return "Unknown Windows input error".to_string();
        }

        let mut buffer = vec![0u16; 512];
        let copied = FormatMessageW(
            FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
            std::ptr::null(),
            errno,
            0,
            buffer.as_mut_ptr(),
            buffer.len() as u32,
            std::ptr::null_mut(),
        );

        if copied == 0 {
            return format!("Windows error {}", errno);
        }

        let message = String::from_utf16_lossy(&buffer[..copied as usize]);
        message.trim().to_string()
    }
}
