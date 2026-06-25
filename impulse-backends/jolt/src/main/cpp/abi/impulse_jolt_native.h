#pragma once

#include <cstdint>

#if defined(_WIN32)
#define IMPULSE_JOLT_EXPORT __declspec(dllexport)
#else
#define IMPULSE_JOLT_EXPORT __attribute__((visibility("default")))
#endif

namespace ImpulseJolt {

inline constexpr int ShapeBox = 1;
inline constexpr int ShapeSphere = 2;
inline constexpr int ShapeCapsule = 3;
inline constexpr int ShapeCylinder = 4;
inline constexpr int ShapeCone = 5;
inline constexpr int ShapePlane = 6;

inline constexpr int BodyStatic = 1;
inline constexpr int BodyDynamic = 2;
inline constexpr int BodyKinematic = 3;

inline constexpr int JointFixed = 1;
inline constexpr int JointPoint = 2;
inline constexpr int JointHinge = 3;
inline constexpr int JointSlider = 4;
inline constexpr int JointSpring = 5;

inline constexpr int AxisX = 1;
inline constexpr int AxisY = 2;
inline constexpr int AxisZ = 3;

inline constexpr float MinShapeSize = 0.001F;
inline constexpr float MinAxisLengthSquared = 1.0e-6F;
inline constexpr std::uint32_t MaxBodies = 131072;
inline constexpr std::uint32_t MaxBodyPairs = 65536;
inline constexpr std::uint32_t MaxContactConstraints = 10240;
inline constexpr std::uint32_t DefaultCollisionGroup = 1;
inline constexpr int RayHitFloatCount = 8;
inline constexpr int ContactBodyHandleCount = 2;
inline constexpr int ContactFloatCount = 11;

} // namespace ImpulseJolt
