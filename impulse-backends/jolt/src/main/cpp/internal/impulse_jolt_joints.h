#pragma once

#include "internal/impulse_jolt_space.h"

#include <Jolt/Physics/Body/Body.h>
#include <Jolt/Physics/Constraints/Constraint.h>

namespace ImpulseJolt {

JPH::Ref<JPH::Constraint> CreateJointConstraint(int jointType,
    JPH::Body& bodyA,
    JPH::Body& bodyB,
    float anchorAX,
    float anchorAY,
    float anchorAZ,
    float anchorBX,
    float anchorBY,
    float anchorBZ,
    float axisXValue,
    float axisYValue,
    float axisZValue,
    float restLength,
    float stiffness,
    float damping,
    float lowerLimit,
    float upperLimit,
    int motorEnabled,
    float motorTargetVelocity,
    float motorMaxForce);

} // namespace ImpulseJolt
