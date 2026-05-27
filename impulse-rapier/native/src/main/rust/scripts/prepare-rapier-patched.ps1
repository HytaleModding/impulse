#!/usr/bin/env pwsh

$ErrorActionPreference = 'Stop'

function Fail([string]$msg)
{
  Write-Error $msg
  exit 1
}

if (-not $env:RAPIER_VERSION)
{
  Fail "RAPIER_VERSION is required"
}
if (-not $env:PATCHED_RAPIER_PATH)
{
  Fail "PATCHED_RAPIER_PATH is required"
}
if (-not $env:RAPIER_PATCH_PATH)
{
  Fail "RAPIER_PATCH_PATH is required"
}

$rapierVersion = $env:RAPIER_VERSION
$patchedRapierPath = $env:PATCHED_RAPIER_PATH
$rapierPatchPath = $env:RAPIER_PATCH_PATH

Write-Host "Preparing patched Rapier: version=$rapierVersion, final=$patchedRapierPath"

$registryRoot = if ($env:CARGO_HOME)
{
  Join-Path $env:CARGO_HOME 'registry\src'
}
else
{
  Join-Path $env:USERPROFILE '.cargo\registry\src'
}
Write-Host "Using cargo registry root: $registryRoot"

function Find-RapierSource
{
  param($root, $version)
  if (-not (Test-Path -Path $root))
  {
    return $null
  }

  Get-ChildItem -Path $root -Directory -Recurse -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -eq "rapier3d-$version" } |
      Sort-Object FullName |
      Select-Object -First 1
}

$baseDirObj = Find-RapierSource -root $registryRoot -version $rapierVersion
$baseDir = if ($baseDirObj)
{
  $baseDirObj.FullName
}
else
{
  $null
}

if (-not $baseDir)
{
  Write-Host "rapier3d-$rapierVersion not found; running cargo fetch..."
  $fetchDir = Join-Path (Split-Path -Parent $patchedRapierPath) '_fetch'
  $fetchManifest = Join-Path $fetchDir 'Cargo.toml'
  New-Item -ItemType Directory -Force -Path (Join-Path $fetchDir 'src') | Out-Null
  Set-Content -Path (Join-Path $fetchDir 'src\lib.rs') -Value "pub fn _fetch_marker() {}" -Encoding UTF8

  @"
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$rapierVersion", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
"@ | Out-File -FilePath $fetchManifest -Encoding utf8

  if (-not (Get-Command cargo -ErrorAction SilentlyContinue))
  {
    Fail "cargo not found in PATH."
  }

  & cargo fetch --manifest-path $fetchManifest
  if ($LASTEXITCODE -ne 0)
  {
    Fail "cargo fetch failed with exit code $LASTEXITCODE"
  }

  $baseDirObj = Find-RapierSource -root $registryRoot -version $rapierVersion
  $baseDir = if ($baseDirObj)
  {
    $baseDirObj.FullName
  }
  else
  {
    $null
  }
}

if (-not $baseDir)
{
  Fail "Unable to locate rapier3d-$rapierVersion in registry after fetch."
}

Write-Host "Found rapier source at: $baseDir"

$patchedParent = Split-Path -Parent $patchedRapierPath
$tmpDir = Join-Path $patchedParent ("_tmp_" + [System.Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

try
{
  Write-Host "Copying source to temp..."
  Copy-Item -Path (Join-Path $baseDir '*') -Destination $tmpDir -Recurse -Force

  if (Get-Command git -ErrorAction SilentlyContinue)
  {
    Write-Host "Applying patch with git apply..."
    & git -C $tmpDir apply $rapierPatchPath
    if ($LASTEXITCODE -ne 0)
    {
      Fail "git apply failed with exit code $LASTEXITCODE"
    }
  }
  elseif (Get-Command patch -ErrorAction SilentlyContinue)
  {
    Write-Host "Applying patch with patch.exe..."
    & patch -d $tmpDir -p1 -i $rapierPatchPath
    if ($LASTEXITCODE -ne 0)
    {
      Fail "patch failed with exit code $LASTEXITCODE"
    }
  }
  else
  {
    Fail "Neither git nor patch found in PATH."
  }

  if (-not (Test-Path (Join-Path $tmpDir 'Cargo.toml')))
  {
    Write-Host "Staged contents (top-level):"
    Get-ChildItem -Path $tmpDir -Force | Select-Object Name,FullName | Format-Table -AutoSize
    Fail "Cargo.toml missing in staged copy; aborting."
  }

  if (Test-Path -Path $patchedRapierPath)
  {
    Remove-Item -Recurse -Force -Path $patchedRapierPath
  }

  Move-Item -Path $tmpDir -Destination $patchedRapierPath
  Write-Host "Prepared patched rapier at: $patchedRapierPath"
}
catch
{
  if (Test-Path -Path $tmpDir)
  {
    Remove-Item -Recurse -Force -Path $tmpDir -ErrorAction SilentlyContinue
  }
  throw
}