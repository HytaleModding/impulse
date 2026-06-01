use crate::dispatch::engine_state::engine::*;
use dispatch_macros::auto_dispatch_from_enum;
#[repr(u32)]
#[derive(Debug, PartialEq, Copy, Clone)]
#[auto_dispatch_from_enum("generated/engine_ffi_signs.rs", "eng_space_")]
enum SpaceCommand {
    Step = 0,
    GetGravity = 1,
    SetGravity = 2,
    RemoveBody = 3,
    GetRuntimeStats = 4,
    GetStepPhaseStats = 5,
    ResetStepPhaseStats = 6,
    CreateStaticPlane = 7,
    CreateBox = 8,
    CreateSphere = 9,
    CreateCapsule = 10,
    CreateCylinder = 11,
    CreateCone = 12,
    RaycastClosest = 13,
    GetContacts = 14,
    CreateFixedJoint = 15,
    CreatePointJoint = 16,
    CreateHingeJoint = 17,
    CreateSliderJoint = 18,
    CreateSpringJoint = 19,
    RemoveJoint = 20,
    GetJointCount = 21,
    Destroy = 22,
}
