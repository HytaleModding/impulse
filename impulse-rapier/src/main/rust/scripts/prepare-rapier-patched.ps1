#!/usr/bin/env pwsh

$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    throw $Message
}

foreach ($Name in @(
    'RAPIER_VERSION',
    'PATCHED_RAPIER_PATH',
    'RAPIER_PATCH_PATH'
)) {
    if (-not [Environment]::GetEnvironmentVariable($Name)) {
        Fail "$Name is required"
    }
}

$rapierVersion = $env:RAPIER_VERSION
$patchedRapierPath = $env:PATCHED_RAPIER_PATH
$rapierPatchPath = $env:RAPIER_PATCH_PATH

if ($env:CARGO_HOME) {
    $registryRoot = Join-Path $env:CARGO_HOME 'registry/src'
} elseif ($env:USERPROFILE) {
    $registryRoot = Join-Path $env:USERPROFILE '.cargo/registry/src'
} else {
    $registryRoot = Join-Path $env:HOME '.cargo/registry/src'
}

function Find-RapierSource([string]$Root, [string]$Version) {
    if (-not (Test-Path -LiteralPath $Root)) {
        return $null
    }

    Get-ChildItem -LiteralPath $Root -Directory -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq "rapier3d-$Version" } |
        Sort-Object FullName |
        Select-Object -First 1
}

$baseDirItem = Find-RapierSource $registryRoot $rapierVersion
if (-not $baseDirItem) {
    $fetchDir = Join-Path ([System.IO.Path]::GetTempPath()) `
        ("impulse-rapier-fetch-" + [Guid]::NewGuid().ToString("N"))
    $fetchManifest = Join-Path $fetchDir 'Cargo.toml'
    New-Item -ItemType Directory -Force -Path (Join-Path $fetchDir 'src') | Out-Null
    New-Item -ItemType File -Force -Path (Join-Path $fetchDir 'src/lib.rs') | Out-Null

    $fetchContent = @"
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$rapierVersion", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
"@
    [System.IO.File]::WriteAllText($fetchManifest, $fetchContent, [System.Text.UTF8Encoding]::new($false))

    if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) {
        Fail "cargo not found in PATH."
    }
    try {
        Push-Location $fetchDir
        & cargo fetch --manifest-path $fetchManifest
        if ($LASTEXITCODE -ne 0) {
            Fail "cargo fetch failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
        Remove-Item -LiteralPath $fetchDir -Recurse -Force -ErrorAction SilentlyContinue
    }

    $baseDirItem = Find-RapierSource $registryRoot $rapierVersion
}

if (-not $baseDirItem) {
    Fail "Unable to locate rapier3d-$rapierVersion in $registryRoot"
}

$baseDir = $baseDirItem.FullName
$patchedParent = Split-Path -Parent $patchedRapierPath
New-Item -ItemType Directory -Force -Path $patchedParent | Out-Null
$tmpDir = Join-Path $patchedParent (".rapier3d-$rapierVersion." + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

try {
    Get-ChildItem -LiteralPath $baseDir -Force |
        Copy-Item -Destination $tmpDir -Recurse -Force

    if (Get-Command git -ErrorAction SilentlyContinue) {
        & git -C $tmpDir apply $rapierPatchPath
        if ($LASTEXITCODE -ne 0) {
            Fail "git apply failed with exit code $LASTEXITCODE"
        }
    } elseif (Get-Command patch -ErrorAction SilentlyContinue) {
        & patch -d $tmpDir -p1 -i $rapierPatchPath
        if ($LASTEXITCODE -ne 0) {
            Fail "patch failed with exit code $LASTEXITCODE"
        }
    } else {
        Fail "Neither git nor patch found in PATH."
    }

    if (-not (Test-Path -LiteralPath (Join-Path $tmpDir 'Cargo.toml'))) {
        Fail "Cargo.toml missing in staged Rapier source: $tmpDir"
    }

    if (Test-Path -LiteralPath $patchedRapierPath) {
        Remove-Item -LiteralPath $patchedRapierPath -Recurse -Force
    }
    Move-Item -LiteralPath $tmpDir -Destination $patchedRapierPath
} catch {
    if (Test-Path -LiteralPath $tmpDir) {
        Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue
    }
    throw
}
