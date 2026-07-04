use std::{env, fs, path::PathBuf, process::Command};

fn main() {
    println!("cargo:rerun-if-changed=assets/app.ico");
    println!("cargo:rerun-if-changed=assets/app.rc");

    if env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("windows") {
        return;
    }

    let out_dir = PathBuf::from(env::var_os("OUT_DIR").expect("OUT_DIR is not set"));
    let res_path = out_dir.join("app.res");
    let rc_exe = find_rc_exe().expect("failed to find Windows SDK rc.exe");

    let status = Command::new(&rc_exe)
        .current_dir("assets")
        .arg("/nologo")
        .arg(format!("/fo{}", res_path.display()))
        .arg("app.rc")
        .status()
        .expect("failed to run Windows SDK rc.exe");

    if !status.success() {
        panic!("rc.exe failed with status {status}");
    }

    println!("cargo:rustc-link-arg-bins={}", res_path.display());
}

fn find_rc_exe() -> Option<PathBuf> {
    if command_exists("rc.exe") {
        return Some(PathBuf::from("rc.exe"));
    }

    let kits_bin = PathBuf::from(r"C:\Program Files (x86)\Windows Kits\10\bin");
    let mut candidates = Vec::new();
    if let Ok(versions) = fs::read_dir(kits_bin) {
        for version in versions.flatten() {
            for arch in ["x64", "x86", "arm64"] {
                let candidate = version.path().join(arch).join("rc.exe");
                if candidate.is_file() {
                    candidates.push(candidate);
                }
            }
        }
    }

    candidates.sort();
    candidates.pop()
}

fn command_exists(command: &str) -> bool {
    Command::new(command)
        .arg("/?")
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status()
        .is_ok()
}
