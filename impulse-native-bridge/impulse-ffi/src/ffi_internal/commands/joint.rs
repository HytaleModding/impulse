use ffi_dispatch_macros::auto_dispatch_from_enum;

#[repr(u32)]
#[derive(Debug, PartialEq, Copy, Clone)]
#[auto_dispatch_from_enum("src/ffi_engine.rs", "eng_joint_")]
enum JointCommand {
    GetType = 0,
    GetBodyA = 1,
    GetBodyB = 2,
    IsEnabled = 3,
    SetEnabled = 4,
    GetAnchorA = 5,
    GetAnchorB = 6,
    GetAxis = 7,
    GetLowerLimit = 8,
    GetUpperLimit = 9,
    SetLimits = 10,
    IsMotorEnabled = 11,
    SetMotorEnabled = 12,
    GetMotorTargetVelocity = 13,
    GetMotorMaxForce = 14,
    SetMotor = 15,
    GetSpringRestLength = 16,
    GetSpringStiffness = 17,
    GetSpringDamping = 18,
}
