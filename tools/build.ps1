param([switch]$TestOnly, [switch]$Release)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$toolRoot = Join-Path $projectRoot '.toolchain'
$env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolRoot 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
# Use an ordinary long Windows path for Java's AF_UNIX temporary socket files.
$javaTemp = Join-Path $toolRoot 'tmp'
New-Item -ItemType Directory -Force -Path $javaTemp | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djava.net.preferIPv4Stack=true "-Djava.io.tmpdir=' + $javaTemp + '" "-Djdk.net.unixdomain.tmpdir=' + $javaTemp + '"'
Push-Location $projectRoot
try {
    if ($TestOnly) { & .\gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain }
    elseif ($Release) { & .\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease --no-daemon --console=plain --stacktrace }
    else { & .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon --console=plain --stacktrace }
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally { Pop-Location }
