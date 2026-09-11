$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$sdk = Join-Path $project '.toolchain\sdk'
$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
$evidence = Join-Path $project 'build-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
$aapt = Join-Path $sdk 'build-tools\36.0.0\aapt2.exe'
$badging = & $aapt dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect APK.' }
$badging | Set-Content -LiteralPath (Join-Path $evidence 'apk-badging.txt')
$manifest = & $aapt dump xmltree $apk --file AndroidManifest.xml
$manifest | Set-Content -LiteralPath (Join-Path $evidence 'apk-manifest.txt')
if ($badging -match "uses-permission: name='android.permission.INTERNET'") { throw 'Forbidden INTERNET permission in APK.' }
if ($badging -match "uses-permission: name='android.permission.ACCESS_NETWORK_STATE'") { throw 'Unexpected network state permission in APK.' }
$env:JAVA_HOME = (Get-ChildItem (Join-Path $project '.toolchain\jdk') -Directory | Select-Object -First 1).FullName
& (Join-Path $sdk 'build-tools\36.0.0\apksigner.bat') verify --verbose --print-certs $apk | Tee-Object -FilePath (Join-Path $evidence 'apk-signature.txt')
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
Get-FileHash -LiteralPath $apk -Algorithm SHA256 | Format-List | Out-File (Join-Path $evidence 'apk-sha256.txt')
$badging | Select-String -Pattern "package:|sdkVersion:|targetSdkVersion:|uses-permission:|launchable-activity:"
