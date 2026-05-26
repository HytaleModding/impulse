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
if (-not $env:PATCHED_CARGO_CONFIG_PATH)
{
  Fail "PATCHED_CARGO_CONFIG_PATH is required"
}
if (-not $env:RAPIER_PATCH_PATH)
{
  Fail "RAPIER_PATCH_PATH is required"
}

$rapierVersion = $env:RAPIER_VERSION
$patchedRapierPath = $env:PATCHED_RAPIER_PATH
$patchedCargoConfigPath = $env:PATCHED_CARGO_CONFIG_PATH
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
  Write-Host "rapier3d-$rapierVersion not found; creating temporary fetch manifest and running 'cargo fetch'..."
  $fetchDir = Join-Path (Split-Path -Parent $patchedCargoConfigPath) 'fetch'
  $fetchManifest = Join-Path $fetchDir 'Cargo.toml'
  New-Item -ItemType Directory -Force -Path (Join-Path $fetchDir 'src') | Out-Null
  New-Item -ItemType File -Force -Path (Join-Path $fetchDir 'src\lib.rs') | Out-Null

  $fetchContent = @"
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$rapierVersion", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
"@
  $fetchContent | Out-File -FilePath $fetchManifest -Encoding utf8

  if (-not (Get-Command cargo -ErrorAction SilentlyContinue))
  {
    Fail "cargo not found in PATH."
  }
  & cargo fetch --manifest-path $fetchManifest

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
Write-Host "Creating temp staging directory: $tmpDir"
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

try
{
  Write-Host "Copying rapier source contents to temp staging directory: $tmpDir"


  New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

  $copySucceeded = $false

  try
  {
    Write-Host "Attempting Copy-Item (PowerShell) copy..."
    Copy-Item -Path (Join-Path $baseDir '*') -Destination $tmpDir -Recurse -Force -ErrorAction Stop
    $copySucceeded = $true
    Write-Host "Copy-Item succeeded."
  }
  catch
  {
    Write-Warning "Copy-Item failed: $( $_.Exception.Message )"
    $copySucceeded = $false
  }

  if (-not $copySucceeded)
  {
    if (Get-Command robocopy -ErrorAction SilentlyContinue)
    {
      Write-Host "Falling back to robocopy..."

      $robocopyLog = Join-Path $tmpDir 'robocopy.log'

      if (Test-Path (Join-Path $tmpDir 'Cargo.toml'))
      {
        $copySucceeded = $true
        Write-Host "robocopy completed and Cargo.toml found."
      }
      else
      {
        Write-Error "robocopy did not produce expected files. See $robocopyLog for details."
        $copySucceeded = $false
      }
    }
    else
    {
      Write-Error "Copy-Item failed and robocopy not available as fallback."
      $copySucceeded = $false
    }
  }

  if (-not $copySucceeded)
  {
    Write-Host "Staging directory contents (if any):"
    Get-ChildItem -Path $tmpDir -Recurse -Force -Depth 2 | Format-Table FullName,Mode -AutoSize
    Fail "Failed to copy rapier source into staging directory."
  }

  if (-not (Test-Path -Path (Join-Path $tmpDir 'Cargo.toml')))
  {
    Write-Host "Staging directory listing (top-level):"
    Get-ChildItem -Path $tmpDir -Force | Select-Object Name,FullName | Format-Table -AutoSize
    Fail "Cargo.toml missing in staged copy; aborting."
  }


  Write-Host "Applying patch..."
  if (Get-Command git -ErrorAction SilentlyContinue)
  {
    $applyProcess = Start-Process -FilePath "git" -ArgumentList "-C", $tmpDir, "apply", $rapierPatchPath -NoNewWindow -Wait -PassThru
    if ($applyProcess.ExitCode -ne 0)
    {
      Fail "git apply failed with exit code $( $applyProcess.ExitCode )"
    }
  }
  elseif (Get-Command patch -ErrorAction SilentlyContinue)
  {
    $patchCmd = (Get-Command patch).Source
    $applyProcess = Start-Process -FilePath $patchCmd -ArgumentList "-d", $tmpDir, "-p1", "-i", $rapierPatchPath -NoNewWindow -Wait -PassThru
    if ($applyProcess.ExitCode -ne 0)
    {
      Fail "patch failed with exit code $( $applyProcess.ExitCode )"
    }
  }
  else
  {
    Fail "Neither git nor patch found in PATH."
  }

  if (-not (Test-Path -Path (Join-Path $tmpDir 'Cargo.toml')))
  {
    Write-Host "Staged contents (top-level):"
    Get-ChildItem -Path $tmpDir -Recurse -Depth 2 | Format-Table FullName,Mode -AutoSize
    Fail "Cargo.toml missing in staged copy; aborting."
  }

  try
  {
    $absCratePath = (Resolve-Path -Path $patchedRapierPath -ErrorAction Stop).ProviderPath
  }
  catch
  {
    Fail "Failed to resolve absolute path for staged crate: $patchedRapierPath"
  }
  $absCratePath = $absCratePath -replace '\\', '/'

  $cargoTomlContent = @"
[patch.crates-io]
rapier3d = { path = '$absCratePath' }
"@

  $cfgDir = Split-Path -Parent $patchedCargoConfigPath
  New-Item -ItemType Directory -Force -Path $cfgDir | Out-Null
  Set-Content -Path $patchedCargoConfigPath -Value $cargoTomlContent -Encoding UTF8

  Write-Host "Wrote cargo config: $patchedCargoConfigPath -> path: $absCratePath"

  if (Test-Path -Path $patchedRapierPath)
  {
    Write-Host "Removing existing patched directory: $patchedRapierPath"
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