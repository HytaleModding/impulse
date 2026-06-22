#include "internal/impulse_jolt_joints.h"

#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/Constraints/DistanceConstraint.h>
#include <Jolt/Physics/Constraints/FixedConstraint.h>
#include <Jolt/Physics/Constraints/HingeConstraint.h>
#include <Jolt/Physics/Constraints/PointConstraint.h>
#include <Jolt/Physics/Constraints/SliderConstraint.h>

#include <algorithm>
#include <cmath>

namespace ImpulseJolt {

float FiniteOr(float value, float fallback) {
    return std::isfinite(value) ? value : fallback;
}

float NonNegative(float value) {
    return std::max(0.0F, FiniteOr(value, 0.0F));
}

JPH::RVec3 LocalPoint(float x, float y, float z) {
    return JPH::RVec3(FiniteOr(x, 0.0F), FiniteOr(y, 0.0F), FiniteOr(z, 0.0F));
}

JPH::Vec3 NormalizedAxis(float x, float y, float z) {
    x = FiniteOr(x, 0.0F);
    y = FiniteOr(y, 1.0F);
    z = FiniteOr(z, 0.0F);
    const float lengthSquared = x * x + y * y + z * z;
    if (!std::isfinite(lengthSquared) || lengthSquared <= MinAxisLengthSquared) {
        return JPH::Vec3::sAxisY();
    }
    const float inverseLength = 1.0F / std::sqrt(lengthSquared);
    return JPH::Vec3(x * inverseLength, y * inverseLength, z * inverseLength);
}

JPH::Vec3 NormalForAxis(JPH::Vec3Arg axis) {
    const JPH::Vec3 reference = std::fabs(axis.GetY()) < 0.9F
        ? JPH::Vec3::sAxisY()
        : JPH::Vec3::sAxisX();
    JPH::Vec3 normal = axis.Cross(reference);
    if (normal.LengthSq() <= MinAxisLengthSquared) {
        normal = axis.Cross(JPH::Vec3::sAxisZ());
    }
    return normal.LengthSq() <= MinAxisLengthSquared
        ? JPH::Vec3::sAxisX()
        : normal.Normalized();
}

void ConfigureSpring(JPH::SpringSettings& settings, float stiffness, float damping) {
    const float clampedStiffness = NonNegative(stiffness);
    if (clampedStiffness <= 0.0F) {
        return;
    }
    settings.mMode = JPH::ESpringMode::StiffnessAndDamping;
    settings.mStiffness = clampedStiffness;
    settings.mDamping = NonNegative(damping);
}

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
    float motorMaxForce) {
    const JPH::RVec3 anchorA = LocalPoint(anchorAX, anchorAY, anchorAZ);
    const JPH::RVec3 anchorB = LocalPoint(anchorBX, anchorBY, anchorBZ);
    const JPH::Vec3 axis = NormalizedAxis(axisXValue, axisYValue, axisZValue);
    const JPH::Vec3 normal = NormalForAxis(axis);
    const float orderedLower = std::min(FiniteOr(lowerLimit, 0.0F),
        FiniteOr(upperLimit, 0.0F));
    const float orderedUpper = std::max(FiniteOr(lowerLimit, 0.0F),
        FiniteOr(upperLimit, 0.0F));
    const bool explicitLimits = orderedLower < orderedUpper;

    switch (jointType) {
        case JointFixed: {
            JPH::FixedConstraintSettings settings;
            settings.mSpace = JPH::EConstraintSpace::LocalToBodyCOM;
            settings.mAutoDetectPoint = false;
            settings.mPoint1 = anchorA;
            settings.mPoint2 = anchorB;
            return settings.Create(bodyA, bodyB);
        }
        case JointPoint: {
            JPH::PointConstraintSettings settings;
            settings.mSpace = JPH::EConstraintSpace::LocalToBodyCOM;
            settings.mPoint1 = anchorA;
            settings.mPoint2 = anchorB;
            return settings.Create(bodyA, bodyB);
        }
        case JointHinge: {
            JPH::HingeConstraintSettings settings;
            settings.mSpace = JPH::EConstraintSpace::LocalToBodyCOM;
            settings.mPoint1 = anchorA;
            settings.mPoint2 = anchorB;
            settings.mHingeAxis1 = settings.mHingeAxis2 = axis;
            settings.mNormalAxis1 = settings.mNormalAxis2 = normal;
            if (explicitLimits) {
                settings.mLimitsMin = std::clamp(orderedLower, -JPH::JPH_PI, 0.0F);
                settings.mLimitsMax = std::clamp(orderedUpper, 0.0F, JPH::JPH_PI);
            }
            JPH::HingeConstraint* constraint =
                static_cast<JPH::HingeConstraint*>(settings.Create(bodyA, bodyB));
            if (constraint != nullptr && motorEnabled != 0) {
                if (motorMaxForce > 0.0F) {
                    constraint->GetMotorSettings().SetTorqueLimit(motorMaxForce);
                }
                constraint->SetMotorState(JPH::EMotorState::Velocity);
                constraint->SetTargetAngularVelocity(FiniteOr(motorTargetVelocity, 0.0F));
            }
            return constraint;
        }
        case JointSlider: {
            JPH::SliderConstraintSettings settings;
            settings.mSpace = JPH::EConstraintSpace::LocalToBodyCOM;
            settings.mAutoDetectPoint = false;
            settings.mPoint1 = anchorA;
            settings.mPoint2 = anchorB;
            settings.mSliderAxis1 = settings.mSliderAxis2 = axis;
            settings.mNormalAxis1 = settings.mNormalAxis2 = normal;
            if (explicitLimits) {
                settings.mLimitsMin = orderedLower;
                settings.mLimitsMax = orderedUpper;
            }
            JPH::SliderConstraint* constraint =
                static_cast<JPH::SliderConstraint*>(settings.Create(bodyA, bodyB));
            if (constraint != nullptr && motorEnabled != 0) {
                if (motorMaxForce > 0.0F) {
                    constraint->GetMotorSettings().SetForceLimit(motorMaxForce);
                }
                constraint->SetMotorState(JPH::EMotorState::Velocity);
                constraint->SetTargetVelocity(FiniteOr(motorTargetVelocity, 0.0F));
            }
            return constraint;
        }
        case JointSpring: {
            JPH::DistanceConstraintSettings settings;
            settings.mSpace = JPH::EConstraintSpace::LocalToBodyCOM;
            settings.mPoint1 = anchorA;
            settings.mPoint2 = anchorB;
            const float rest = NonNegative(restLength);
            if (rest > 0.0F) {
                settings.mMinDistance = rest;
                settings.mMaxDistance = rest;
            }
            ConfigureSpring(settings.mLimitsSpringSettings, stiffness, damping);
            return settings.Create(bodyA, bodyB);
        }
        default:
            return nullptr;
    }
}

} // namespace ImpulseJolt
