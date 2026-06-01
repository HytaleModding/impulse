use crate::dispatch::dispatch_command;

use crate::dispatch::engine_state;
use std::ffi::CStr;
use std::os::raw::c_char;

/// Load the backend engine DLL from the given path.
///
/// Must be called once before any `flush_buffer` call. `path` must be a
/// valid, null-terminated UTF-8 string pointing to the engine shared library
/// file (for example, `engine.dll` on Windows).
#[unsafe(no_mangle)]
pub extern "C" fn init_backend(path: *const c_char) {
    // SAFETY: caller guarantees `path` is a valid null-terminated C string.
    let path_str = unsafe { CStr::from_ptr(path) }
        .to_str()
        .expect("init_engine: path is not valid UTF-8");
    engine_state::init(path_str);
}

#[unsafe(no_mangle)]
pub extern "C" fn flush_buffer(buffer_ptr: *mut u64) {
    let command_count = unsafe { *buffer_ptr };
    let mut offset = 1;

    for _ in 0..command_count {
        offset += dispatch_command(buffer_ptr, offset);
    }
}
