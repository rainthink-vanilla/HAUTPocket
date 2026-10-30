$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.toolchain'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$buildTools = Join-Path $toolRoot 'android-sdk/build-tools/35.0.0'
$privateRoot = Join-Path $projectRoot '.private-signing'
$passwordFile = Join-Path $privateRoot 'store-password.txt'
$inputApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-unsigned.apk'
$outputApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
$alignedApk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release-aligned.apk'
if (!(Test-Path -LiteralPath $inputApk)) { throw 'Run tools/build.ps1 -Release first' }
if (!(Test-Path -LiteralPath $passwordFile)) { throw 'Restore private signing materials; do not silently generate a replacement key' }
& (Join-Path $buildTools 'zipalign.exe') -f -p 4 $inputApk $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'Release APK alignment failed' }
& (Join-Path $buildTools 'apksigner.bat') sign --out $outputApk --min-sdk-version 26 --rotation-min-sdk-version 28 --v4-signing-enabled false --debuggable-apk-permitted false --ks (Join-Path $privateRoot 'legacy-debug.keystore') --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --next-signer --ks (Join-Path $privateRoot 'hautpocket-release.p12') --ks-key-alias hautpocket --ks-pass "file:$passwordFile" --key-pass "file:$passwordFile" --lineage (Join-Path $projectRoot 'signing/lineage.bin') $alignedApk
if ($LASTEXITCODE -ne 0) { throw 'Release signing failed' }
& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $outputApk
if ($LASTEXITCODE -ne 0) { throw 'Signed APK verification failed' }
Write-Host 'Signed Release APK verified for Android 8+; rotated signer applies on Android 9+.'
