param([switch]$Release)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.toolchain'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$deliverables = Join-Path $projectRoot 'deliverables'
New-Item -ItemType Directory -Force -Path $deliverables | Out-Null
$variant = if ($Release) { 'release' } else { 'debug' }
$apkInput = Join-Path $projectRoot "app/build/outputs/apk/$variant/app-$variant.apk"
$metadata = Get-Content -LiteralPath (Join-Path $projectRoot "app/build/outputs/apk/$variant/output-metadata.json") -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid artifact version' }
$apkOutput = Join-Path $deliverables $(if ($Release) { "HAUTPocket-$version.apk" } else { "HAUTPocket-$version-debug.apk" })
if (!(Test-Path -LiteralPath $apkInput)) { throw 'Build the APK first' }
$buildTools = Join-Path $toolRoot 'android-sdk/build-tools/35.0.0'
$badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apkInput
if ($LASTEXITCODE -ne 0 -or !($badging -match "package: name='cn.hautpocket.app'")) { throw 'APK metadata verification failed' }
if ($Release -and ($badging -match 'application-debuggable')) { throw 'A debuggable APK must not be published as a Release' }
& (Join-Path $buildTools 'apksigner.bat') verify $apkInput
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
$testFiles = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/build/test-results/testDebugUnitTest') -Filter '*.xml'
$total = 0
foreach ($file in $testFiles) {
    [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
    if ([int]$report.testsuite.failures -gt 0 -or [int]$report.testsuite.errors -gt 0) { throw 'Unit tests failed' }
    $total += [int]$report.testsuite.tests
}
if ($total -lt 20) { throw 'Final unit test report is missing' }
if ($Release) {
    $releaseTotal = 0
    foreach ($file in (Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/build/test-results/testReleaseUnitTest') -Filter '*.xml')) {
        [xml]$report = Get-Content -LiteralPath $file.FullName -Raw
        if ([int]$report.testsuite.failures -gt 0 -or [int]$report.testsuite.errors -gt 0) { throw 'Release unit tests failed' }
        $releaseTotal += [int]$report.testsuite.tests
    }
    if ($total -lt 50 -or $releaseTotal -lt 50) { throw 'Release requires all 50 tests in both variants' }
}
[xml]$lint = Get-Content -LiteralPath (Join-Path $projectRoot "app/build/reports/lint-results-$variant.xml") -Raw
if (@($lint.issues.issue | Where-Object { $_.severity -eq 'Error' -or $_.severity -eq 'Fatal' }).Count -gt 0) { throw 'Lint errors found' }
Copy-Item -LiteralPath $apkInput -Destination $apkOutput -Force

$licenseDir = Join-Path $projectRoot 'licenses'
New-Item -ItemType Directory -Force -Path $licenseDir | Out-Null
$gradleHome = Get-ChildItem -LiteralPath (Join-Path $toolRoot 'gradle-cache/wrapper/dists/gradle-8.9-bin') -Filter 'gradle-8.9' -Directory -Recurse | Select-Object -First 1
Copy-Item -LiteralPath (Join-Path $gradleHome.FullName 'LICENSE') -Destination (Join-Path $licenseDir 'Gradle-LICENSE.txt') -Force
Copy-Item -LiteralPath (Join-Path $gradleHome.FullName 'NOTICE') -Destination (Join-Path $licenseDir 'Gradle-NOTICE.txt') -Force

Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$zipPath = Join-Path $deliverables "HAUTPocket-$version-source.zip"
$zipStream = [System.IO.File]::Open($zipPath, [System.IO.FileMode]::Create)
$archive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = @()
    foreach ($name in @('README.md','LICENSE','THIRD_PARTY_NOTICES.md','.gitignore','.gitattributes','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','app/proguard-rules.pro')) {
        $files += Get-Item -LiteralPath (Join-Path $projectRoot $name)
    }
foreach ($directory in @('app/src','gradle','docs','tools','licenses','signing','artwork')) {
        $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -File -Recurse
    }
    $internalPlan = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $projectRoot 'docs') 'PLAN.md'))
    $files = $files | Where-Object { $_.FullName -ne $internalPlan }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, "HAUTPocket/$relative") | Out-Null
    }
} finally { $archive.Dispose() }
if ($Release) {
    # The official source archive must be the exact committed tree, not ignored local material.
    & git -c "safe.directory=$($projectRoot.Replace('\','/'))" -C $projectRoot diff --quiet HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Commit the reviewed publication files before packaging a Release' }
    $untracked = & git -c "safe.directory=$($projectRoot.Replace('\','/'))" -C $projectRoot ls-files --others --exclude-standard
    if ($untracked) { throw 'Untracked publication files remain; audit and commit them before Release packaging' }
    & git -c "safe.directory=$($projectRoot.Replace('\','/'))" -C $projectRoot archive --format=zip --prefix=HAUTPocket/ --output=$zipPath HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Committed source archive failed' }
}
$hashes = @($apkOutput,$zipPath) | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
    $hash.Hash.ToLower() + '  ' + [IO.Path]::GetFileName($_)
}
$hashes | Set-Content -LiteralPath (Join-Path $deliverables 'SHA256SUMS.txt') -Encoding ascii
Write-Host "Verified $total unit tests, APK metadata/signature and lint. Packaged:"
Get-Item -LiteralPath $apkOutput,$zipPath | Select-Object Name,Length
