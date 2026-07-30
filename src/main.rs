#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod clicker;
mod input;
mod recorder;
mod script;
mod settings;

use clicker::Clicker;
use native_windows_gui as nwg;
use recorder::ScriptRecorder;
use script::{Script, ScriptPlayer};
use settings::{
    ClickBackend, ClickMode, ClickPosition, ClickSettings, MouseButton, WindowClickTarget,
};
use std::cell::{Cell, RefCell};
use std::path::PathBuf;
use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;
use winapi::shared::minwindef::{LPARAM, LRESULT, UINT, WPARAM};
use winapi::shared::windef::HWND;
use winapi::um::minwinbase::SYSTEMTIME;
use winapi::um::shellscalingapi::{SetProcessDpiAwareness, PROCESS_PER_MONITOR_DPI_AWARE};
use winapi::um::sysinfoapi::GetLocalTime;
use winapi::um::winuser::{
    RegisterHotKey, SetProcessDPIAware, UnregisterHotKey, MOD_NOREPEAT, VK_F8, VK_F9, WM_HOTKEY,
};

const HOTKEY_RUN_ID: i32 = 0x4639;
const HOTKEY_RECORD_ID: i32 = 0x4638;
const RAW_HOTKEY_HANDLER_ID: usize = 0x1_4639;
const APP_WIDTH: i32 = 1040;
const APP_HEIGHT: i32 = 680;
const SURFACE: [u8; 3] = [244, 248, 252];
const PANEL: [u8; 3] = [232, 241, 249];
const PANEL_ALT: [u8; 3] = [222, 235, 246];
const MUTED: [u8; 3] = [71, 85, 105];
const CYAN: [u8; 3] = [8, 145, 178];
const BLUE: [u8; 3] = [37, 99, 235];

fn main() {
    unsafe {
        if SetProcessDpiAwareness(PROCESS_PER_MONITOR_DPI_AWARE) != 0 {
            SetProcessDPIAware();
        }
    }

    nwg::init().expect("Failed to initialize native-windows-gui");
    let _ = nwg::Font::set_global_family("Microsoft YaHei UI");

    let app = App::build().expect("Failed to build UI");
    app.register_hotkey();
    nwg::dispatch_thread_events();
}

struct App {
    window: nwg::Window,
    _app_icon: nwg::Icon,
    _hero_label: nwg::RichLabel,
    _hero_hint: nwg::RichLabel,
    _click_panel: nwg::Frame,
    _target_panel: nwg::Frame,
    _timing_panel: nwg::Frame,
    _script_panel: nwg::Frame,
    _control_panel: nwg::Frame,
    _click_panel_title: nwg::RichLabel,
    _target_panel_title: nwg::RichLabel,
    _timing_panel_title: nwg::RichLabel,
    _script_panel_title: nwg::RichLabel,
    _title_font: nwg::Font,
    _normal_font: nwg::Font,
    _small_font: nwg::Font,
    _mouse_label: nwg::Label,
    _click_label: nwg::Label,
    _position_label: nwg::Label,
    _interval_label: nwg::Label,
    _repeat_label: nwg::Label,
    _hotkey_label: nwg::Label,
    _mode_label: nwg::Label,
    _script_status_label: nwg::Label,
    _hour_label: nwg::Label,
    _minute_label: nwg::Label,
    _second_label: nwg::Label,
    _ms_label: nwg::Label,
    _hint_label: nwg::Label,
    _play_label: nwg::Label,
    _help_label: nwg::Label,
    position_notice: nwg::Notice,
    script_notice: nwg::Notice,
    mouse_left: nwg::RadioButton,
    mouse_right: nwg::RadioButton,
    mouse_middle: nwg::RadioButton,
    single_click: nwg::RadioButton,
    double_click: nwg::RadioButton,
    position_combo: nwg::ComboBox<&'static str>,
    position_button: nwg::Button,
    interval_combo: nwg::ComboBox<&'static str>,
    hours: nwg::TextInput,
    minutes: nwg::TextInput,
    seconds: nwg::TextInput,
    millis: nwg::TextInput,
    repeat_combo: nwg::ComboBox<&'static str>,
    run_mode_combo: nwg::ComboBox<&'static str>,
    record_button: nwg::Button,
    save_script_button: nwg::Button,
    load_script_button: nwg::Button,
    loop_script: nwg::CheckBox,
    hotkey_input: nwg::TextInput,
    game_mode: nwg::CheckBox,
    start_button: nwg::Button,
    clicker: Clicker,
    script_player: ScriptPlayer,
    script_recorder: RefCell<ScriptRecorder>,
    current_script: RefCell<Option<Script>>,
    current_script_path: RefCell<Option<PathBuf>>,
    fixed_position: Cell<Option<(i32, i32)>>,
    background_target: Cell<Option<WindowClickTarget>>,
    position_capture_pending: Arc<Mutex<Option<Result<CapturedPosition, String>>>>,
    fixed_capture_in_progress: Cell<bool>,
    hotkey_registered: Cell<bool>,
    handler: RefCell<Option<nwg::EventHandler>>,
    raw_handler: RefCell<Option<nwg::RawEventHandler>>,
}

#[derive(Debug, Clone, Copy)]
enum CapturedPosition {
    FixedPoint(i32, i32),
    BackgroundTarget(WindowClickTarget),
}

impl App {
    fn build() -> Result<Rc<Self>, nwg::NwgError> {
        let app_icon = nwg::Icon::from_bin(include_bytes!("../assets/app.ico"))?;
        let mut window = nwg::Window::default();
        nwg::Window::builder()
            .size((APP_WIDTH, APP_HEIGHT))
            .position((160, 120))
            .title("Clicker Assistant - 连点助手")
            .icon(Some(&app_icon))
            .flags(nwg::WindowFlags::WINDOW | nwg::WindowFlags::VISIBLE)
            .build(&mut window)?;

        let title_font = build_font(25)?;
        let section_font = build_font(18)?;
        let normal_font = build_font(17)?;
        let small_font = build_font(14)?;

        let mut hero_label = nwg::RichLabel::default();
        let mut hero_hint = nwg::RichLabel::default();
        let mut click_panel = nwg::Frame::default();
        let mut target_panel = nwg::Frame::default();
        let mut timing_panel = nwg::Frame::default();
        let mut script_panel = nwg::Frame::default();
        let mut control_panel = nwg::Frame::default();
        let mut click_panel_title = nwg::RichLabel::default();
        let mut target_panel_title = nwg::RichLabel::default();
        let mut timing_panel_title = nwg::RichLabel::default();
        let mut script_panel_title = nwg::RichLabel::default();

        build_rich_label(
            &mut hero_label,
            &window,
            "连点助手 COMMAND DECK",
            28,
            18,
            430,
            36,
            &title_font,
            SURFACE,
            BLUE,
        )?;
        build_rich_label(
            &mut hero_hint,
            &window,
            "F9 开始/停止 · F8 录制脚本 · 支持固定点和后台点击",
            30,
            55,
            620,
            24,
            &small_font,
            SURFACE,
            MUTED,
        )?;

        build_panel(&mut click_panel, &window, 24, 96, 480, 170)?;
        build_panel(&mut target_panel, &window, 532, 96, 480, 170)?;
        build_panel(&mut timing_panel, &window, 24, 286, 480, 250)?;
        build_panel(&mut script_panel, &window, 532, 286, 480, 250)?;
        build_panel(&mut control_panel, &window, 24, 558, 988, 110)?;

        build_rich_label(
            &mut click_panel_title,
            &click_panel,
            "01  点击设置",
            18,
            14,
            210,
            28,
            &section_font,
            PANEL,
            CYAN,
        )?;
        build_rich_label(
            &mut target_panel_title,
            &target_panel,
            "02  目标位置",
            18,
            14,
            210,
            28,
            &section_font,
            PANEL,
            CYAN,
        )?;
        build_rich_label(
            &mut timing_panel_title,
            &timing_panel,
            "03  频率控制",
            18,
            14,
            210,
            28,
            &section_font,
            PANEL,
            CYAN,
        )?;
        build_rich_label(
            &mut script_panel_title,
            &script_panel,
            "04  脚本作战台",
            18,
            14,
            230,
            28,
            &section_font,
            PANEL,
            CYAN,
        )?;

        let mut mouse_label = nwg::Label::default();
        let mut click_label = nwg::Label::default();
        let mut position_label = nwg::Label::default();
        let mut interval_label = nwg::Label::default();
        let mut repeat_label = nwg::Label::default();
        let mut hotkey_label = nwg::Label::default();
        let mut mode_label = nwg::Label::default();
        let mut script_status_label = nwg::Label::default();
        let mut hour_label = nwg::Label::default();
        let mut minute_label = nwg::Label::default();
        let mut second_label = nwg::Label::default();
        let mut ms_label = nwg::Label::default();
        let mut hint_label = nwg::Label::default();
        let mut play_label = nwg::Label::default();
        let mut help_label = nwg::Label::default();
        let mut position_notice = nwg::Notice::default();
        let mut script_notice = nwg::Notice::default();

        build_label(
            &mut mouse_label,
            &click_panel,
            "鼠标按键",
            22,
            58,
            82,
            26,
            &normal_font,
        )?;
        build_label(
            &mut click_label,
            &click_panel,
            "点击方式",
            22,
            112,
            82,
            26,
            &normal_font,
        )?;
        build_label(
            &mut position_label,
            &target_panel,
            "点击位置",
            22,
            60,
            82,
            26,
            &normal_font,
        )?;
        build_label(
            &mut interval_label,
            &timing_panel,
            "每次点击间隔",
            22,
            58,
            130,
            26,
            &normal_font,
        )?;
        build_label(
            &mut repeat_label,
            &timing_panel,
            "重复策略",
            22,
            196,
            95,
            26,
            &normal_font,
        )?;
        build_label(
            &mut hotkey_label,
            &control_panel,
            "开始/停止快捷键",
            20,
            22,
            150,
            26,
            &normal_font,
        )?;
        build_label(
            &mut mode_label,
            &script_panel,
            "执行模式",
            22,
            58,
            82,
            26,
            &normal_font,
        )?;
        build_label(
            &mut script_status_label,
            &script_panel,
            "脚本：未录制",
            22,
            106,
            430,
            28,
            &small_font,
        )?;

        build_label(
            &mut hour_label,
            &timing_panel,
            "时",
            207,
            116,
            24,
            26,
            &small_font,
        )?;
        build_label(
            &mut minute_label,
            &timing_panel,
            "分",
            307,
            116,
            24,
            26,
            &small_font,
        )?;
        build_label(
            &mut second_label,
            &timing_panel,
            "秒",
            407,
            116,
            24,
            26,
            &small_font,
        )?;
        build_label(
            &mut ms_label,
            &timing_panel,
            "毫秒",
            207,
            155,
            48,
            26,
            &small_font,
        )?;
        build_label(
            &mut hint_label,
            &timing_panel,
            "1 秒 = 1000 毫秒",
            305,
            155,
            145,
            26,
            &small_font,
        )?;
        build_label(
            &mut play_label,
            &control_panel,
            "▶",
            676,
            36,
            28,
            28,
            &title_font,
        )?;
        build_label(
            &mut help_label,
            &script_panel,
            "?",
            438,
            20,
            22,
            22,
            &small_font,
        )?;

        let mut mouse_left = nwg::RadioButton::default();
        let mut mouse_right = nwg::RadioButton::default();
        let mut mouse_middle = nwg::RadioButton::default();
        let mut single_click = nwg::RadioButton::default();
        let mut double_click = nwg::RadioButton::default();

        build_radio(
            &mut mouse_left,
            &click_panel,
            "左键",
            118,
            58,
            78,
            28,
            true,
            true,
            &normal_font,
        )?;
        build_radio(
            &mut mouse_right,
            &click_panel,
            "右键",
            216,
            58,
            78,
            28,
            false,
            false,
            &normal_font,
        )?;
        build_radio(
            &mut mouse_middle,
            &click_panel,
            "中键（滚轮）",
            314,
            58,
            130,
            28,
            false,
            false,
            &normal_font,
        )?;
        build_radio(
            &mut single_click,
            &click_panel,
            "单击",
            118,
            112,
            78,
            28,
            true,
            true,
            &normal_font,
        )?;
        build_radio(
            &mut double_click,
            &click_panel,
            "双击",
            216,
            112,
            78,
            28,
            false,
            false,
            &normal_font,
        )?;

        let mut position_combo = nwg::ComboBox::default();
        nwg::ComboBox::builder()
            .parent(&target_panel)
            .position((122, 54))
            .size((210, 44))
            .collection(vec!["鼠标光标所在位置", "固定位置"])
            .selected_index(Some(0))
            .font(Some(&normal_font))
            .build(&mut position_combo)?;

        let mut position_button = nwg::Button::default();
        nwg::Button::builder()
            .parent(&target_panel)
            .text("设置位置")
            .position((344, 57))
            .size((110, 34))
            .font(Some(&small_font))
            .enabled(true)
            .build(&mut position_button)?;

        let mut interval_combo = nwg::ComboBox::default();
        nwg::ComboBox::builder()
            .parent(&timing_panel)
            .position((158, 52))
            .size((286, 44))
            .collection(vec!["自定义间隔时间"])
            .selected_index(Some(0))
            .font(Some(&normal_font))
            .build(&mut interval_combo)?;

        let mut hours = nwg::TextInput::default();
        let mut minutes = nwg::TextInput::default();
        let mut seconds = nwg::TextInput::default();
        let mut millis = nwg::TextInput::default();

        build_input(
            &mut hours,
            &timing_panel,
            "0",
            116,
            112,
            82,
            34,
            &normal_font,
        )?;
        build_input(
            &mut minutes,
            &timing_panel,
            "0",
            216,
            112,
            82,
            34,
            &normal_font,
        )?;
        build_input(
            &mut seconds,
            &timing_panel,
            "0",
            316,
            112,
            82,
            34,
            &normal_font,
        )?;
        build_input(
            &mut millis,
            &timing_panel,
            "100",
            116,
            152,
            82,
            34,
            &normal_font,
        )?;

        let mut repeat_combo = nwg::ComboBox::default();
        nwg::ComboBox::builder()
            .parent(&timing_panel)
            .position((158, 190))
            .size((286, 44))
            .collection(vec!["重复点击直到手动停止"])
            .selected_index(Some(0))
            .font(Some(&normal_font))
            .build(&mut repeat_combo)?;

        let mut run_mode_combo = nwg::ComboBox::default();
        nwg::ComboBox::builder()
            .parent(&script_panel)
            .position((118, 52))
            .size((145, 44))
            .collection(vec!["连点器", "脚本"])
            .selected_index(Some(0))
            .font(Some(&normal_font))
            .build(&mut run_mode_combo)?;

        let mut record_button = nwg::Button::default();
        nwg::Button::builder()
            .parent(&script_panel)
            .text("开始录制 (F8)")
            .position((276, 55))
            .size((126, 34))
            .font(Some(&small_font))
            .build(&mut record_button)?;

        let mut save_script_button = nwg::Button::default();
        nwg::Button::builder()
            .parent(&script_panel)
            .text("保存脚本")
            .position((118, 150))
            .size((100, 34))
            .font(Some(&small_font))
            .enabled(false)
            .build(&mut save_script_button)?;

        let mut load_script_button = nwg::Button::default();
        nwg::Button::builder()
            .parent(&script_panel)
            .text("加载脚本")
            .position((236, 150))
            .size((100, 34))
            .font(Some(&small_font))
            .build(&mut load_script_button)?;

        let mut loop_script = nwg::CheckBox::default();
        nwg::CheckBox::builder()
            .parent(&script_panel)
            .text("循环执行")
            .position((354, 153))
            .size((100, 28))
            .font(Some(&small_font))
            .background_color(Some(PANEL))
            .build(&mut loop_script)?;

        let mut hotkey_input = nwg::TextInput::default();
        nwg::TextInput::builder()
            .parent(&control_panel)
            .text("F9")
            .position((20, 54))
            .size((150, 38))
            .font(Some(&normal_font))
            .readonly(true)
            .background_color(Some([255, 255, 255]))
            .build(&mut hotkey_input)?;

        let mut game_mode = nwg::CheckBox::default();
        nwg::CheckBox::builder()
            .parent(&control_panel)
            .text("后台点击")
            .position((424, 59))
            .size((112, 28))
            .font(Some(&normal_font))
            .background_color(Some(PANEL_ALT))
            .check_state(nwg::CheckBoxState::Checked)
            .build(&mut game_mode)?;

        let mut start_button = nwg::Button::default();
        nwg::Button::builder()
            .parent(&control_panel)
            .text("开始连点 (F9)")
            .position((710, 28))
            .size((248, 62))
            .font(Some(&normal_font))
            .build(&mut start_button)?;
        nwg::Notice::builder()
            .parent(&window)
            .build(&mut position_notice)?;
        nwg::Notice::builder()
            .parent(&window)
            .build(&mut script_notice)?;

        let app = Rc::new(Self {
            window,
            _app_icon: app_icon,
            _hero_label: hero_label,
            _hero_hint: hero_hint,
            _click_panel: click_panel,
            _target_panel: target_panel,
            _timing_panel: timing_panel,
            _script_panel: script_panel,
            _control_panel: control_panel,
            _click_panel_title: click_panel_title,
            _target_panel_title: target_panel_title,
            _timing_panel_title: timing_panel_title,
            _script_panel_title: script_panel_title,
            _title_font: title_font,
            _normal_font: normal_font,
            _small_font: small_font,
            _mouse_label: mouse_label,
            _click_label: click_label,
            _position_label: position_label,
            _interval_label: interval_label,
            _repeat_label: repeat_label,
            _hotkey_label: hotkey_label,
            _mode_label: mode_label,
            _script_status_label: script_status_label,
            _hour_label: hour_label,
            _minute_label: minute_label,
            _second_label: second_label,
            _ms_label: ms_label,
            _hint_label: hint_label,
            _play_label: play_label,
            _help_label: help_label,
            position_notice,
            script_notice,
            mouse_left,
            mouse_right,
            mouse_middle,
            single_click,
            double_click,
            position_combo,
            position_button,
            interval_combo,
            hours,
            minutes,
            seconds,
            millis,
            repeat_combo,
            run_mode_combo,
            record_button,
            save_script_button,
            load_script_button,
            loop_script,
            hotkey_input,
            game_mode,
            start_button,
            clicker: Clicker::new(),
            script_player: ScriptPlayer::new(),
            script_recorder: RefCell::new(ScriptRecorder::new()),
            current_script: RefCell::new(None),
            current_script_path: RefCell::new(None),
            fixed_position: Cell::new(None),
            background_target: Cell::new(None),
            position_capture_pending: Arc::new(Mutex::new(None)),
            fixed_capture_in_progress: Cell::new(false),
            hotkey_registered: Cell::new(false),
            handler: RefCell::new(None),
            raw_handler: RefCell::new(None),
        });

        App::bind_events(&app);
        app.sync_mode_ui();
        app.window.set_focus();
        Ok(app)
    }

    fn bind_events(app: &Rc<Self>) {
        let events_app = Rc::clone(app);
        let handler =
            nwg::full_bind_event_handler(&app.window.handle, move |event, _event_data, handle| {
                if handle == events_app.window.handle && event == nwg::Event::OnWindowClose {
                    events_app.shutdown();
                    nwg::stop_thread_dispatch();
                } else if handle == events_app.start_button.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.toggle_running();
                } else if handle == events_app.record_button.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.toggle_recording_from_button();
                } else if handle == events_app.save_script_button.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.save_current_script();
                } else if handle == events_app.load_script_button.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.load_script_from_dialog();
                } else if handle == events_app.run_mode_combo.handle
                    && event == nwg::Event::OnComboxBoxSelection
                {
                    events_app.sync_mode_ui();
                } else if handle == events_app.position_button.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.capture_fixed_position();
                } else if handle == events_app.position_combo.handle
                    && event == nwg::Event::OnComboxBoxSelection
                {
                    events_app.sync_position_controls();
                } else if handle == events_app.game_mode.handle
                    && event == nwg::Event::OnButtonClick
                {
                    events_app.sync_position_controls();
                } else if handle == events_app.position_notice.handle
                    && event == nwg::Event::OnNotice
                {
                    events_app.finish_fixed_position_capture();
                } else if handle == events_app.script_notice.handle && event == nwg::Event::OnNotice
                {
                    events_app.finish_script_playback();
                }
            });
        *app.handler.borrow_mut() = Some(handler);

        let raw_app = Rc::clone(app);
        let raw_handler = nwg::bind_raw_event_handler(
            &app.window.handle,
            RAW_HOTKEY_HANDLER_ID,
            move |_hwnd: HWND, msg: UINT, w: WPARAM, _l: LPARAM| -> Option<LRESULT> {
                if msg == WM_HOTKEY {
                    match w as i32 {
                        HOTKEY_RUN_ID => raw_app.toggle_running(),
                        HOTKEY_RECORD_ID => raw_app.toggle_recording_from_hotkey(),
                        _ => {}
                    }
                    Some(0)
                } else {
                    None
                }
            },
        )
        .expect("bind raw hotkey event handler");
        *app.raw_handler.borrow_mut() = Some(raw_handler);
    }

    fn register_hotkey(&self) {
        let Some(hwnd) = self.window.handle.hwnd() else {
            return;
        };

        let run_ok =
            unsafe { RegisterHotKey(hwnd, HOTKEY_RUN_ID, MOD_NOREPEAT as u32, VK_F9 as u32) };
        let record_ok =
            unsafe { RegisterHotKey(hwnd, HOTKEY_RECORD_ID, MOD_NOREPEAT as u32, VK_F8 as u32) };
        self.hotkey_registered.set(run_ok != 0 || record_ok != 0);
    }

    fn unregister_hotkey(&self) {
        if !self.hotkey_registered.replace(false) {
            return;
        }

        if let Some(hwnd) = self.window.handle.hwnd() {
            unsafe {
                UnregisterHotKey(hwnd, HOTKEY_RUN_ID);
                UnregisterHotKey(hwnd, HOTKEY_RECORD_ID);
            }
        }
    }

    fn shutdown(&self) {
        self.clicker.stop();
        self.script_player.stop();
        if self.script_recorder.borrow().is_recording() {
            let _ = self
                .script_recorder
                .borrow_mut()
                .stop("unsaved".to_string());
        }
        self.unregister_hotkey();
    }

    fn toggle_running(&self) {
        if self.is_script_mode() {
            self.toggle_script_playback();
            return;
        }

        if self.script_recorder.borrow().is_recording() {
            nwg::modal_error_message(&self.window, "正在录制", "录制中不能开始连点。");
            return;
        }

        if self.clicker.is_running() {
            self.clicker.stop();
            self.set_running_ui(false);
            return;
        }

        match self.read_settings() {
            Ok(settings) => {
                self.clicker.start(settings);
                self.set_running_ui(true);
            }
            Err(message) => {
                nwg::modal_error_message(&self.window, "配置错误", &message);
            }
        }
    }

    fn toggle_script_playback(&self) {
        if self.script_recorder.borrow().is_recording() {
            nwg::modal_error_message(&self.window, "正在录制", "录制中不能执行脚本。");
            return;
        }

        if self.script_player.is_running() {
            self.script_player.stop();
            self.set_script_running_ui(false);
            return;
        }

        let Some(script) = self.current_script.borrow().clone() else {
            nwg::modal_error_message(&self.window, "脚本为空", "请先录制或加载脚本。");
            return;
        };

        if script.events.is_empty() {
            nwg::modal_error_message(&self.window, "脚本为空", "当前脚本没有可执行事件。");
            return;
        }

        let loop_enabled = self.loop_script.check_state() == nwg::CheckBoxState::Checked;
        let notice = self.script_notice.sender();
        self.script_player.start(script, loop_enabled, move || {
            notice.notice();
        });
        self.set_script_running_ui(true);
    }

    fn finish_script_playback(&self) {
        self.script_player.detach_finished_worker();
        self.set_script_running_ui(false);
    }

    fn save_current_script(&self) {
        let Some(script_value) = self.current_script.borrow().clone() else {
            nwg::modal_error_message(&self.window, "脚本为空", "请先录制或加载脚本。");
            return;
        };

        let path = script::default_script_path();
        match script::save_script(&script_value, &path) {
            Ok(()) => {
                *self.current_script_path.borrow_mut() = Some(path);
                self.update_script_status();
            }
            Err(message) => {
                nwg::modal_error_message(&self.window, "保存脚本失败", &message);
            }
        }
    }

    fn load_script_from_dialog(&self) {
        if self.clicker.is_running()
            || self.script_player.is_running()
            || self.script_recorder.borrow().is_recording()
        {
            return;
        }

        let default_folder = script::default_scripts_dir();
        let _ = std::fs::create_dir_all(&default_folder);
        let mut dialog = nwg::FileDialog::default();
        let build_result = nwg::FileDialog::builder()
            .title("加载脚本")
            .action(nwg::FileDialogAction::Open)
            .filters("JSON脚本(*.json)|任意文件(*.*)")
            .default_folder(default_folder.to_string_lossy().to_string())
            .build(&mut dialog);

        if let Err(err) = build_result {
            nwg::modal_error_message(&self.window, "打开文件对话框失败", &err.to_string());
            return;
        }

        if !dialog.run(Some(&self.window)) {
            return;
        }

        let selected = match dialog.get_selected_item() {
            Ok(path) => PathBuf::from(path),
            Err(err) => {
                nwg::modal_error_message(&self.window, "读取选择文件失败", &err.to_string());
                return;
            }
        };

        match script::load_script(&selected) {
            Ok(loaded) => {
                *self.current_script.borrow_mut() = Some(loaded);
                *self.current_script_path.borrow_mut() = Some(selected);
                self.run_mode_combo.set_selection(Some(1));
                self.update_script_status();
                self.sync_mode_ui();
            }
            Err(message) => {
                nwg::modal_error_message(&self.window, "加载脚本失败", &message);
            }
        }
    }

    fn toggle_recording_from_button(&self) {
        self.toggle_recording(true);
    }

    fn toggle_recording_from_hotkey(&self) {
        self.toggle_recording(false);
    }

    fn toggle_recording(&self, stop_from_button: bool) {
        if self.clicker.is_running() || self.script_player.is_running() {
            nwg::modal_error_message(&self.window, "正在运行", "运行中不能开始录制。");
            return;
        }

        if self.script_recorder.borrow().is_recording() {
            let name = format!("script-{}", timestamp_name());
            let result = if stop_from_button {
                self.script_recorder
                    .borrow_mut()
                    .stop_dropping_recent_mouse_clicks(name.clone(), 300)
            } else {
                self.script_recorder.borrow_mut().stop(name.clone())
            };

            match result {
                Ok(script) => {
                    *self.current_script.borrow_mut() = Some(script);
                    *self.current_script_path.borrow_mut() = None;
                    self.set_recording_ui(false);
                }
                Err(message) => {
                    self.set_recording_ui(false);
                    nwg::modal_error_message(&self.window, "停止录制失败", &message);
                }
            }
            return;
        }

        let start_result = self.script_recorder.borrow_mut().start();
        match start_result {
            Ok(()) => {
                self.run_mode_combo.set_selection(Some(1));
                self.set_recording_ui(true);
            }
            Err(message) => {
                nwg::modal_error_message(&self.window, "开始录制失败", &message);
            }
        }
    }

    fn capture_fixed_position(&self) {
        if self.clicker.is_running() || self.fixed_capture_in_progress.get() {
            return;
        }

        let background = self.is_background_mode();
        self.fixed_capture_in_progress.set(true);
        self.position_combo.set_selection(Some(1));
        self.position_combo.set_enabled(false);
        self.position_button.set_text(if background {
            "3秒后取后台目标..."
        } else {
            "3秒后取点..."
        });
        self.position_button.set_enabled(false);
        self.start_button.set_enabled(false);

        let sender = self.position_notice.sender();
        let pending = Arc::clone(&self.position_capture_pending);
        thread::spawn(move || {
            thread::sleep(Duration::from_secs(3));
            let result = if background {
                input::capture_window_target().map(CapturedPosition::BackgroundTarget)
            } else {
                input::cursor_position().map(|(x, y)| CapturedPosition::FixedPoint(x, y))
            };

            if let Ok(mut slot) = pending.lock() {
                *slot = Some(result);
            }
            sender.notice();
        });
    }

    fn finish_fixed_position_capture(&self) {
        let result = self
            .position_capture_pending
            .lock()
            .ok()
            .and_then(|mut slot| slot.take());

        self.fixed_capture_in_progress.set(false);
        self.start_button.set_enabled(!self.clicker.is_running());

        match result {
            Some(Ok(CapturedPosition::FixedPoint(x, y))) => {
                self.fixed_position.set(Some((x, y)));
                self.position_combo.set_selection(Some(1));
                self.position_button
                    .set_text(&format!("已设置 {},{}", x, y));
            }
            Some(Ok(CapturedPosition::BackgroundTarget(target))) => {
                self.background_target.set(Some(target));
                self.game_mode.set_check_state(nwg::CheckBoxState::Checked);
                self.position_combo.set_selection(Some(1));
                self.position_button
                    .set_text(&format!("后台 {},{}", target.client_x, target.client_y));
            }
            Some(Err(message)) => {
                self.sync_position_controls();
                nwg::modal_error_message(&self.window, "获取目标失败", &message);
            }
            None => self.sync_position_controls(),
        }

        self.sync_position_controls();
    }

    fn sync_position_controls(&self) {
        if self.fixed_capture_in_progress.get() {
            return;
        }

        if self.is_background_mode() {
            self.position_combo.set_selection(Some(1));
            self.position_combo.set_enabled(false);
            self.position_button.set_enabled(!self.clicker.is_running());
            if let Some(target) = self.background_target.get() {
                self.position_button
                    .set_text(&format!("后台 {},{}", target.client_x, target.client_y));
            } else {
                self.position_button.set_text("设置后台目标");
            }
            return;
        }

        self.position_combo.set_enabled(!self.clicker.is_running());
        let fixed_selected = self.position_combo.selection() == Some(1);
        self.position_button.set_enabled(!self.clicker.is_running());
        if fixed_selected {
            if let Some((x, y)) = self.fixed_position.get() {
                self.position_button
                    .set_text(&format!("已设置 {},{}", x, y));
            } else {
                self.position_button.set_text("设置位置");
            }
        } else {
            self.position_button.set_text("设置位置");
        }
    }

    fn set_running_ui(&self, running: bool) {
        self.start_button.set_text(if running {
            "停止连点 (F9)"
        } else {
            "开始连点 (F9)"
        });

        self.mouse_left.set_enabled(!running);
        self.mouse_right.set_enabled(!running);
        self.mouse_middle.set_enabled(!running);
        self.single_click.set_enabled(!running);
        self.double_click.set_enabled(!running);
        self.interval_combo.set_enabled(!running);
        self.hours.set_enabled(!running);
        self.minutes.set_enabled(!running);
        self.seconds.set_enabled(!running);
        self.millis.set_enabled(!running);
        self.repeat_combo.set_enabled(!running);
        self.hotkey_input.set_enabled(!running);
        self.game_mode.set_enabled(!running);
        self.run_mode_combo.set_enabled(!running);
        self.record_button.set_enabled(!running);
        self.save_script_button
            .set_enabled(!running && self.current_script.borrow().is_some());
        self.load_script_button.set_enabled(!running);
        self.loop_script.set_enabled(!running);
        self.sync_position_controls();
        self.sync_mode_ui();
    }

    fn set_script_running_ui(&self, running: bool) {
        self.start_button.set_text(if running {
            "停止脚本 (F9)"
        } else {
            "执行脚本 (F9)"
        });

        self.mouse_left.set_enabled(!running);
        self.mouse_right.set_enabled(!running);
        self.mouse_middle.set_enabled(!running);
        self.single_click.set_enabled(!running);
        self.double_click.set_enabled(!running);
        self.position_combo
            .set_enabled(!running && !self.is_background_mode());
        self.position_button.set_enabled(!running);
        self.interval_combo.set_enabled(!running);
        self.hours.set_enabled(!running);
        self.minutes.set_enabled(!running);
        self.seconds.set_enabled(!running);
        self.millis.set_enabled(!running);
        self.repeat_combo.set_enabled(!running);
        self.hotkey_input.set_enabled(!running);
        self.game_mode.set_enabled(!running);
        self.run_mode_combo.set_enabled(!running);
        self.record_button.set_enabled(!running);
        self.save_script_button
            .set_enabled(!running && self.current_script.borrow().is_some());
        self.load_script_button.set_enabled(!running);
        self.loop_script.set_enabled(!running);
        self.update_script_status();
        if !running {
            self.sync_position_controls();
            self.sync_mode_ui();
        }
    }

    fn set_recording_ui(&self, recording: bool) {
        self.record_button.set_text(if recording {
            "停止录制(F8)"
        } else {
            "开始录制(F8)"
        });
        self.start_button.set_enabled(!recording);
        self.save_script_button
            .set_enabled(!recording && self.current_script.borrow().is_some());
        self.load_script_button.set_enabled(!recording);
        self.run_mode_combo.set_enabled(!recording);
        self.loop_script.set_enabled(!recording);
        self.update_script_status();
        if !recording {
            self.sync_mode_ui();
        }
    }

    fn sync_mode_ui(&self) {
        if self.clicker.is_running() || self.script_player.is_running() {
            return;
        }

        if self.is_script_mode() {
            self.start_button.set_text("执行脚本 (F9)");
        } else {
            self.start_button.set_text("开始连点 (F9)");
        }

        let recording = self.script_recorder.borrow().is_recording();
        self.start_button.set_enabled(!recording);
        self.record_button
            .set_enabled(!self.clicker.is_running() && !self.script_player.is_running());
        self.save_script_button
            .set_enabled(!recording && self.current_script.borrow().is_some());
        self.load_script_button.set_enabled(!recording);
        self.loop_script.set_enabled(!recording);
        self.update_script_status();
    }

    fn update_script_status(&self) {
        if self.script_recorder.borrow().is_recording() {
            let count = self.script_recorder.borrow().event_count();
            self._script_status_label
                .set_text(&format!("脚本：录制中，{} 个事件", count));
            return;
        }

        if let Some(script) = self.current_script.borrow().as_ref() {
            let path_text = self
                .current_script_path
                .borrow()
                .as_ref()
                .and_then(|path| {
                    path.file_name()
                        .map(|name| name.to_string_lossy().to_string())
                })
                .unwrap_or_else(|| script.name.clone());
            self._script_status_label.set_text(&format!(
                "脚本：{}，{} 个事件",
                path_text,
                script.event_count()
            ));
        } else {
            self._script_status_label.set_text("脚本：未录制");
        }
    }

    fn read_settings(&self) -> Result<ClickSettings, String> {
        let button = if self.mouse_right.check_state() == nwg::RadioButtonState::Checked {
            MouseButton::Right
        } else if self.mouse_middle.check_state() == nwg::RadioButtonState::Checked {
            MouseButton::Middle
        } else {
            MouseButton::Left
        };

        let mode = if self.double_click.check_state() == nwg::RadioButtonState::Checked {
            ClickMode::Double
        } else {
            ClickMode::Single
        };

        let background = if self.is_background_mode() {
            let target = self.background_target.get().ok_or_else(|| {
                "后台目标未设置，请点击“设置后台目标”后把鼠标移到目标点。".to_string()
            })?;
            input::validate_window_target(target)?;
            ClickBackend::Background(target)
        } else {
            ClickBackend::Foreground
        };

        let position = if self.is_background_mode() {
            ClickPosition::CurrentCursor
        } else if self.position_combo.selection() == Some(1) {
            match self.fixed_position.get() {
                Some((x, y)) => ClickPosition::Fixed { x, y },
                None => {
                    return Err("固定位置未设置，请点击“设置位置”后把鼠标移到目标点。".to_string())
                }
            }
        } else {
            ClickPosition::CurrentCursor
        };

        let interval_ms = self.read_interval_ms()?;
        Ok(ClickSettings {
            button,
            mode,
            position,
            backend: background,
            interval_ms,
        })
    }

    fn read_interval_ms(&self) -> Result<u64, String> {
        let hours = parse_u64(&self.hours.text(), "小时")?;
        let minutes = parse_u64(&self.minutes.text(), "分钟")?;
        let seconds = parse_u64(&self.seconds.text(), "秒")?;
        let millis = parse_u64(&self.millis.text(), "毫秒")?;

        let total = hours
            .saturating_mul(60 * 60 * 1000)
            .saturating_add(minutes.saturating_mul(60 * 1000))
            .saturating_add(seconds.saturating_mul(1000))
            .saturating_add(millis);

        Ok(if total == 0 { 100 } else { total.max(1) })
    }

    fn is_background_mode(&self) -> bool {
        self.game_mode.check_state() == nwg::CheckBoxState::Checked
    }

    fn is_script_mode(&self) -> bool {
        self.run_mode_combo.selection() == Some(1)
    }
}

fn build_panel(
    frame: &mut nwg::Frame,
    parent: &nwg::Window,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
) -> Result<(), nwg::NwgError> {
    nwg::Frame::builder()
        .parent(parent)
        .position((x, y))
        .size((w, h))
        .flags(nwg::FrameFlags::VISIBLE | nwg::FrameFlags::BORDER)
        .build(frame)
}

fn build_rich_label<P: Into<nwg::ControlHandle>>(
    label: &mut nwg::RichLabel,
    parent: P,
    text: &str,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    font: &nwg::Font,
    background: [u8; 3],
    color: [u8; 3],
) -> Result<(), nwg::NwgError> {
    nwg::RichLabel::builder()
        .parent(parent)
        .text(text)
        .position((x, y))
        .size((w, h))
        .font(Some(font))
        .background_color(Some(background))
        .build(label)?;
    label.set_char_format(
        0..text.chars().count() as u32,
        &nwg::CharFormat {
            text_color: Some(color),
            effects: Some(nwg::CharEffects::BOLD),
            ..Default::default()
        },
    );
    Ok(())
}

fn build_label<P: Into<nwg::ControlHandle>>(
    label: &mut nwg::Label,
    parent: P,
    text: &str,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    font: &nwg::Font,
) -> Result<(), nwg::NwgError> {
    nwg::Label::builder()
        .parent(parent)
        .text(text)
        .position((x, y))
        .size((w, h))
        .font(Some(font))
        .build(label)
}

fn build_radio<P: Into<nwg::ControlHandle>>(
    radio: &mut nwg::RadioButton,
    parent: P,
    text: &str,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    checked: bool,
    starts_group: bool,
    font: &nwg::Font,
) -> Result<(), nwg::NwgError> {
    let mut flags = nwg::RadioButtonFlags::VISIBLE;
    if starts_group {
        flags |= nwg::RadioButtonFlags::GROUP;
    }

    nwg::RadioButton::builder()
        .parent(parent)
        .text(text)
        .position((x, y))
        .size((w, h))
        .flags(flags)
        .check_state(if checked {
            nwg::RadioButtonState::Checked
        } else {
            nwg::RadioButtonState::Unchecked
        })
        .font(Some(font))
        .build(radio)
}

fn build_font(size: u32) -> Result<nwg::Font, nwg::NwgError> {
    let mut font = nwg::Font::default();
    nwg::Font::builder()
        .family("Microsoft YaHei UI")
        .size(size)
        .build(&mut font)?;
    Ok(font)
}

fn build_input<P: Into<nwg::ControlHandle>>(
    input: &mut nwg::TextInput,
    parent: P,
    text: &str,
    x: i32,
    y: i32,
    w: i32,
    h: i32,
    font: &nwg::Font,
) -> Result<(), nwg::NwgError> {
    nwg::TextInput::builder()
        .parent(parent)
        .text(text)
        .position((x, y))
        .size((w, h))
        .font(Some(font))
        .build(input)
}

fn parse_u64(raw: &str, label: &str) -> Result<u64, String> {
    let value = raw.trim();
    if value.is_empty() {
        return Ok(0);
    }

    value
        .parse::<u64>()
        .map_err(|_| format!("{}必须是非负整数", label))
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
