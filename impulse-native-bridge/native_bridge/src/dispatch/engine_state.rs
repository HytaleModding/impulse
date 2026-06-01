use std::ptr;
use std::sync::atomic::{AtomicPtr, Ordering};
use std::sync::Once;

#[path = "../../generated/engine_ffi.rs"]
pub mod engine;
use engine::Engine;

static ENGINE: AtomicPtr<Engine> = AtomicPtr::new(ptr::null_mut());
static INIT: Once = Once::new();

/// Load the engine DLL from `path` and make it available to all dispatch
/// calls.  Must be called exactly once before `process_buffer`.  Panics if
/// the library cannot be opened.
pub fn init(path: &str) {
    INIT.call_once(|| {
        // SAFETY: `path` points to a valid DLL that exports the expected
        // symbols.  We immediately store the pointer and never free it, so
        // the returned reference is valid for the lifetime of the process.
        let e = Box::new(unsafe { Engine::new(path) }.expect("Failed to load engine DLL"));
        ENGINE.store(Box::into_raw(e), Ordering::Release);
    });
}

/// Return a reference to the loaded engine.  Panics if `init` has not been
/// called yet.
///
/// The returned reference is `'static` because the `Engine` is heap-allocated
/// and intentionally leaked for the duration of the process.
pub fn engine() -> &'static Engine {
    let ptr = ENGINE.load(Ordering::Acquire);
    assert!(
        !ptr.is_null(),
        "Engine not initialized – call init_engine() before process_buffer()"
    );
    // SAFETY: non-null only after `init`, which stores a valid leaked Box.
    // Engine is never freed, so the reference is valid for 'static.
    unsafe { &*ptr }
}
