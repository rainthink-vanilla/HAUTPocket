$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.toolchain'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$signer = Join-Path $toolRoot 'android-sdk/build-tools/35.0.0/apksigner.bat'
$privateRoot = Join-Path $projectRoot '.private-signing'
$publicRoot = Join-Path $projectRoot 'signing'
$releaseKey = Join-Path $privateRoot 'hautpocket-release.p12'
$passwordFile = Join-Path $privateRoot 'store-password.txt'
$legacyKey = Join-Path $privateRoot 'legacy-debug.keystore'
if ((Test-Path -LiteralPath (Join-Path $publicRoot 'lineage.bin')) -and !(Test-Path -LiteralPath $releaseKey)) {
    throw 'A public signing lineage already exists. Restore its private signing backup; do not generate a replacement key.'
}
New-Item -ItemType Directory -Force -Path $privateRoot,$publicRoot | Out-Null
if (!(Test-Path -LiteralPath $legacyKey)) {
    Copy-Item -LiteralPath (Join-Path $toolRoot 'android-user/debug.keystore') -Destination $legacyKey
}
if (!(Test-Path -LiteralPath $releaseKey)) {
    if (Test-Path -LiteralPath $passwordFile) { throw 'Password exists without keystore; inspect local signing backup before continuing.' }
    $randomBytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($randomBytes) + "`n", [Text.Encoding]::ASCII)
    & $keytool -genkeypair -keystore $releaseKey -storetype PKCS12 -alias hautpocket -storepass:file $passwordFile -keypass:file $passwordFile -keyalg RSA -keysize 3072 -validity 10950 -dname 'CN=HAUTPocket, OU=Independent Project, O=HAUTPocket, C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed' }
}
if (!(Test-Path -LiteralPath $passwordFile)) { throw 'Release password file is missing. Restore the local signing backup.' }
# apksigner consumes one line for the store password, then one for the key password.
$storeSecret = [IO.File]::ReadAllLines($passwordFile)[0]
if ([string]::IsNullOrWhiteSpace($storeSecret)) { throw 'Empty local signing password' }
[IO.File]::WriteAllText($passwordFile, $storeSecret + "`n" + $storeSecret + "`n", [Text.Encoding]::ASCII)
$storeSecret = $null
$lineage = Join-Path $publicRoot 'lineage.bin'
if (!(Test-Path -LiteralPath $lineage)) {
    & $signer rotate --out $lineage --old-signer --ks $legacyKey --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --new-signer --ks $releaseKey --ks-key-alias hautpocket --ks-pass "file:$passwordFile" --key-pass "file:$passwordFile"
    if ($LASTEXITCODE -ne 0) { throw 'Signing lineage generation failed' }
}
& $keytool -exportcert -rfc -keystore $releaseKey -alias hautpocket -storepass:file $passwordFile -file (Join-Path $publicRoot 'release-certificate.pem')
if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed' }
Write-Host 'Signing materials ready. Keep .private-signing offline-backed-up; never publish that directory.'
