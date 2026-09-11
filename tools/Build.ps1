param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug'))
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$jdk = Get-ChildItem (Join-Path $project '.toolchain\jdk') -Directory | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$env:GRADLE_USER_HOME = Join-Path $project '.toolchain\gradle-home'
$env:ANDROID_HOME = Join-Path $project '.toolchain\sdk'
$env:ANDROID_USER_HOME = Join-Path $project '.toolchain\android-user'
$gradle = Join-Path $project '.toolchain\gradle\gradle-8.13\bin\gradle.bat'
New-Item -ItemType Directory -Force (Join-Path $project 'build-evidence') | Out-Null
for ($attempt = 1; $attempt -le 8; $attempt++) {
    $log = Join-Path $project "build-evidence\gradle-attempt-$attempt.log"
    & $gradle @Tasks --console=plain --max-workers=2 '-Dorg.gradle.internal.instrumentation.agent=false' '-Dorg.gradle.vfs.watch=false' 2>&1 | Tee-Object -FilePath $log
    $result = $LASTEXITCODE
    if ($result -eq 0) { exit 0 }
    if (-not (Select-String -LiteralPath $log -Pattern 'Could not move temporary workspace' -Quiet)) { exit $result }
    & $gradle --stop
    & (Join-Path $PSScriptRoot 'Repair-GradleCache.ps1')
}
exit $result
