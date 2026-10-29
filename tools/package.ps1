$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.toolchain'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$deliverables = Join-Path $projectRoot 'deliverables'
New-Item -ItemType Directory -Force -Path $deliverables | Out-Null
$apkInput = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
$metadata = Get-Content -LiteralPath (Join-Path $projectRoot 'app/build/outputs/apk/debug/output-metadata.json') -Raw | ConvertFrom-Json
$version = $metadata.elements[0].versionName
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid artifact version' }
$apkOutput = Join-Path $deliverables "HAUTPocket-$version-debug.apk"
if (!(Test-Path -LiteralPath $apkInput)) { throw 'Build the APK first' }
$buildTools = Join-Path $toolRoot 'android-sdk/build-tools/35.0.0'
$badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apkInput
if ($LASTEXITCODE -ne 0 -or !($badging -match "package: name='cn.hautpocket.app'")) { throw 'APK metadata verification failed' }
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
[xml]$lint = Get-Content -LiteralPath (Join-Path $projectRoot 'app/build/reports/lint-results-debug.xml') -Raw
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
    foreach ($directory in @('app/src','gradle','docs','tools','licenses')) {
        $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -File -Recurse
    }
    $internalPlan = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $projectRoot 'docs') 'PLAN.md'))
    $files = $files | Where-Object { $_.FullName -ne $internalPlan }
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, "HAUTPocket/$relative") | Out-Null
    }
} finally { $archive.Dispose() }
$hashes = @($apkOutput,$zipPath) | ForEach-Object {
    $hash = Get-FileHash -LiteralPath $_ -Algorithm SHA256
    $hash.Hash.ToLower() + '  ' + [IO.Path]::GetFileName($_)
}
$hashes | Set-Content -LiteralPath (Join-Path $deliverables 'SHA256SUMS.txt') -Encoding ascii
Write-Host "Verified $total unit tests, APK metadata/signature and lint. Packaged:"
Get-Item -LiteralPath $apkOutput,$zipPath | Select-Object Name,Length
