$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = (Get-ChildItem (Join-Path $project '.toolchain\jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $project '.toolchain\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $project '.toolchain\android-user'
$env:ANDROID_AVD_HOME = Join-Path $project '.toolchain\avd'
New-Item -ItemType Directory -Force $env:ANDROID_AVD_HOME | Out-Null
$commandTools = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest'
if (-not (Test-Path -LiteralPath $commandTools)) {
    New-Item -ItemType Directory -Force (Join-Path $env:ANDROID_HOME 'cmdline-tools') | Out-Null
    Copy-Item -LiteralPath (Join-Path $project '.toolchain\sdk-bootstrap\cmdline-tools') -Destination $commandTools -Recurse
}
'no' | & (Join-Path $commandTools 'bin\avdmanager.bat') create avd --force --name MindOrbit_API33 --package 'system-images;android-33;google_apis;x86_64' --device pixel_6 --path (Join-Path $env:ANDROID_AVD_HOME 'MindOrbit_API33.avd')
Start-Process -FilePath (Join-Path $env:ANDROID_HOME 'emulator\emulator.exe') -ArgumentList '-avd MindOrbit_API33 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect -memory 2048 -no-boot-anim' -WindowStyle Hidden -RedirectStandardOutput (Join-Path $project '.toolchain\emulator.log') -RedirectStandardError (Join-Path $project '.toolchain\emulator-error.log')
