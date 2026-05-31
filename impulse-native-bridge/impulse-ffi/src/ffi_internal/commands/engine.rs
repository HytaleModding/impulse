use ffi_dispatch_macros::auto_dispatch_from_enum;

#[repr(u32)]
#[derive(Debug, PartialEq, Copy, Clone)]
#[auto_dispatch_from_enum("src/ffi_engine.rs", "eng_engine_")]
enum EngineCommand {
    CreateSpace = 0,
    CreateSpaceWithId = 1,
}
