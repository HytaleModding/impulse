#!/usr/bin/env bash
set -euo pipefail

: "${RAPIER_VERSION:?RAPIER_VERSION is required}"
: "${PATCHED_RAPIER_PATH:?PATCHED_RAPIER_PATH is required}"
: "${PATCHED_CARGO_CONFIG_PATH:?PATCHED_CARGO_CONFIG_PATH is required}"
: "${RAPIER_PATCH_PATH:?RAPIER_PATCH_PATH is required}"

registry_root="${CARGO_HOME:-${HOME}/.cargo}/registry/src"

find_rapier_source() {
  if [ -d "$registry_root" ]; then
    find "$registry_root" -path "*/rapier3d-$RAPIER_VERSION" -type d | sort | sed -n '1p'
  fi
  return 0
}

base_dir="$(find_rapier_source)"
if [ -z "$base_dir" ]; then
  fetch_dir="$(dirname "$PATCHED_CARGO_CONFIG_PATH")/fetch"
  fetch_manifest="$fetch_dir/Cargo.toml"
  mkdir -p "$fetch_dir/src"
  touch "$fetch_dir/src/lib.rs"

  cat > "$fetch_manifest" <<EOF
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$RAPIER_VERSION", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
EOF

  cargo fetch --manifest-path "$fetch_manifest"
  base_dir="$(find_rapier_source)"
fi

if [ -z "$base_dir" ]; then
  echo "Unable to locate rapier3d-$RAPIER_VERSION in $registry_root" >&2
  exit 1
fi

rm -rf "$PATCHED_RAPIER_PATH"
mkdir -p "$(dirname "$PATCHED_RAPIER_PATH")"
cp -a "$base_dir" "$PATCHED_RAPIER_PATH"
patch -d "$PATCHED_RAPIER_PATH" -p1 < "$RAPIER_PATCH_PATH"

# normalize path to forward slashes so TOML doesn't see backslash escapes
normalized_path="${PATCHED_RAPIER_PATH//\\//}"

mkdir -p "$(dirname "$PATCHED_CARGO_CONFIG_PATH")"
cat > "$PATCHED_CARGO_CONFIG_PATH" <<EOF
[patch.crates-io]
rapier3d = { path = "$normalized_path" }EOF
EOF