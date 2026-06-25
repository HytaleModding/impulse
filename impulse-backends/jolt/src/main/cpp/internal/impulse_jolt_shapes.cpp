#include "internal/impulse_jolt_shapes.h"

#include <Jolt/Geometry/Plane.h>
#include <Jolt/Math/Quat.h>
#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/Shape/CapsuleShape.h>
#include <Jolt/Physics/Collision/Shape/CylinderShape.h>
#include <Jolt/Physics/Collision/Shape/PlaneShape.h>
#include <Jolt/Physics/Collision/Shape/RotatedTranslatedShape.h>
#include <Jolt/Physics/Collision/Shape/SphereShape.h>
#include <Jolt/Physics/Collision/Shape/TaperedCylinderShape.h>

#include <algorithm>
#include <cstdint>

namespace ImpulseJolt {

float Positive(float value) {
    return std::max(value, MinShapeSize);
}

JPH::EMotionType MotionType(int bodyType) {
    switch (bodyType) {
        case BodyStatic:
            return JPH::EMotionType::Static;
        case BodyKinematic:
            return JPH::EMotionType::Kinematic;
        default:
            return JPH::EMotionType::Dynamic;
    }
}

JPH::ObjectLayer ObjectLayer(int collisionGroup, int collisionMask) {
    constexpr std::uint32_t maskBits = JPH::ObjectLayerPairFilterMask::cMask;
    std::uint32_t group = static_cast<std::uint32_t>(collisionGroup) & maskBits;
    std::uint32_t mask = static_cast<std::uint32_t>(collisionMask) & maskBits;
    if (group == 0) {
        group = DefaultCollisionGroup;
    }
    if (mask == 0) {
        mask = maskBits;
    }
    return JPH::ObjectLayerPairFilterMask::sGetObjectLayer(group, mask);
}

float CenterOfMassOffsetY(int shapeType,
    float halfExtentY,
    float radius,
    float halfHeight,
    int axis) {
    switch (shapeType) {
        case ShapeBox:
            return halfExtentY;
        case ShapeSphere:
            return radius;
        case ShapeCapsule:
            return axis == AxisY ? radius + halfHeight : radius;
        case ShapeCylinder:
            return axis == AxisY ? halfHeight : radius;
        case ShapeCone:
            return axis == AxisY ? halfHeight : radius;
        default:
            return 0.0F;
    }
}

JPH::Quat AxisRotation(int axis) {
    switch (axis) {
        case AxisX:
            return JPH::Quat::sRotation(JPH::Vec3::sAxisZ(), -0.5F * JPH::JPH_PI);
        case AxisZ:
            return JPH::Quat::sRotation(JPH::Vec3::sAxisX(), 0.5F * JPH::JPH_PI);
        default:
            return JPH::Quat::sIdentity();
    }
}

JPH::ShapeRefC RotatedForAxis(JPH::ShapeRefC shape, int axis) {
    if (axis == AxisY || shape == nullptr) {
        return shape;
    }
    return new JPH::RotatedTranslatedShape(JPH::Vec3::sZero(), AxisRotation(axis), shape);
}

JPH::ShapeRefC CreateShape(int shapeType,
    float halfExtentX,
    float halfExtentY,
    float halfExtentZ,
    float radius,
    float halfHeight,
    int axis,
    float groundY) {
    switch (shapeType) {
        case ShapeBox:
            return new JPH::BoxShape(JPH::Vec3(Positive(halfExtentX),
                Positive(halfExtentY),
                Positive(halfExtentZ)));
        case ShapeSphere:
            return new JPH::SphereShape(Positive(radius));
        case ShapeCapsule:
            return RotatedForAxis(
                new JPH::CapsuleShape(Positive(halfHeight), Positive(radius)),
                axis);
        case ShapeCylinder:
            return RotatedForAxis(
                new JPH::CylinderShape(Positive(halfHeight), Positive(radius)),
                axis);
        case ShapeCone: {
            JPH::TaperedCylinderShapeSettings settings(Positive(halfHeight),
                0.0F,
                Positive(radius));
            JPH::Shape::ShapeResult result = settings.Create();
            if (result.HasError()) {
                return nullptr;
            }
            return RotatedForAxis(result.Get(), axis);
        }
        case ShapePlane:
            return new JPH::PlaneShape(
                JPH::Plane::sFromPointAndNormal(JPH::Vec3(0.0F, groundY, 0.0F),
                    JPH::Vec3::sAxisY()));
        default:
            return nullptr;
    }
}

} // namespace ImpulseJolt
