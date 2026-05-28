#!/usr/bin/env bash
set -euo pipefail

: "${RAPIER_VERSION:?RAPIER_VERSION is required}"
: "${PATCHED_RAPIER_PATH:?PATCHED_RAPIER_PATH is required}"
: "${RAPIER_PATCH_PATH:?RAPIER_PATCH_PATH is required}"

registry_root="${CARGO_HOME:-${HOME}/.cargo}/registry/src"
fetch_dir=""
tmp_dir=""

cleanup() {
  if [ -n "$fetch_dir" ]; then
    rm -rf "$fetch_dir"
  fi
  if [ -n "$tmp_dir" ]; then
    rm -rf "$tmp_dir"
  fi
}
trap cleanup EXIT

find_rapier_source() {
  if [ -d "$registry_root" ]; then
    find "$registry_root" -path "*/rapier3d-$RAPIER_VERSION" -type d | sort | sed -n '1p'
  fi
  return 0
}

base_dir="$(find_rapier_source)"
if [ -z "$base_dir" ]; then
  fetch_dir="$(mktemp -d)"
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

  (cd "$fetch_dir" && cargo fetch --manifest-path "$fetch_manifest")
  rm -rf "$fetch_dir"
  fetch_dir=""
  base_dir="$(find_rapier_source)"
fi

if [ -z "$base_dir" ]; then
  echo "Unable to locate rapier3d-$RAPIER_VERSION in $registry_root" >&2
  exit 1
fi

patched_parent="$(dirname "$PATCHED_RAPIER_PATH")"
mkdir -p "$patched_parent"
tmp_dir="$(mktemp -d "$patched_parent/.rapier3d-$RAPIER_VERSION.XXXXXX")"

cp -a "$base_dir"/. "$tmp_dir"/

if command -v git >/dev/null 2>&1; then
  git -C "$tmp_dir" apply "$RAPIER_PATCH_PATH"
elif command -v patch >/dev/null 2>&1; then
  patch -d "$tmp_dir" -p1 -i "$RAPIER_PATCH_PATH"
else
  echo "Neither git nor patch found in PATH." >&2
  exit 1
fi

if [ ! -f "$tmp_dir/Cargo.toml" ]; then
  echo "Cargo.toml missing in staged Rapier source: $tmp_dir" >&2
  exit 1
fi

rm -rf "$PATCHED_RAPIER_PATH"
mv "$tmp_dir" "$PATCHED_RAPIER_PATH"
tmp_dir=""
