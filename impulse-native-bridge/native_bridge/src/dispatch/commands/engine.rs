use crate::dispatch::engine_state::engine::*;
use dispatch_macros::auto_dispatch_from_enum;
#[repr(u32)]
#[derive(Debug, PartialEq, Copy, Clone)]
#[auto_dispatch_from_enum("generated/engine_ffi_signs.rs", "eng_engine_")]
enum EngineCommand {
    CreateSpace = 0,
    CreateSpaceWithId = 1,
}
