param([switch]$SkipPackages)
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path (Split-Path $PSScriptRoot -Parent) '.toolchain'
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
$ProgressPreference = 'SilentlyContinue'
function Save-Archive($uri, $destination, $checksum) {
    if (!(Test-Path -LiteralPath $destination)) {
        Write-Host "Downloading $([IO.Path]::GetFileName($destination))"
        Invoke-WebRequest -Uri $uri -OutFile $destination
    }
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLower() -ne $checksum.ToLower()) {
        throw "Checksum mismatch: $destination"
    }
}
$jdkRoot = Join-Path $toolRoot 'jdk'
if (!(Test-Path -LiteralPath $jdkRoot)) {
    $asset = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $asset[0].binary.package
    $jdkZip = Join-Path $toolRoot 'jdk17.zip'
    Save-Archive $package.link $jdkZip $package.checksum
    Expand-Archive -LiteralPath $jdkZip -DestinationPath $jdkRoot
}
$jdkHome = (Get-ChildItem -LiteralPath $jdkRoot -Directory | Select-Object -First 1).FullName
$sdkRoot = Join-Path $toolRoot 'android-sdk'
$cliRoot = Join-Path $sdkRoot 'cmdline-tools'
if (!(Test-Path -LiteralPath (Join-Path $cliRoot 'latest/bin/sdkmanager.bat'))) {
    $sdkZip = Join-Path $toolRoot 'commandlinetools.zip'
    Save-Archive 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' $sdkZip '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    $extractRoot = Join-Path $toolRoot 'commandline-extracted'
    Expand-Archive -LiteralPath $sdkZip -DestinationPath $extractRoot -Force
    New-Item -ItemType Directory -Force -Path $cliRoot | Out-Null
    Copy-Item -LiteralPath (Join-Path $extractRoot 'cmdline-tools') -Destination (Join-Path $cliRoot 'latest') -Recurse
}
$env:JAVA_HOME = $jdkHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-cache'
if (!$SkipPackages) {
    1..40 | ForEach-Object { 'y' } | & (Join-Path $cliRoot 'latest/bin/sdkmanager.bat') "--sdk_root=$sdkRoot" --licenses
    if ($LASTEXITCODE -ne 0) { throw 'SDK license setup failed' }
    & (Join-Path $cliRoot 'latest/bin/sdkmanager.bat') "--sdk_root=$sdkRoot" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
    if ($LASTEXITCODE -ne 0) { throw 'SDK package installation failed' }
}
$propertiesPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'local.properties'
('sdk.dir=' + $sdkRoot.Replace('\', '/')) | Set-Content -LiteralPath $propertiesPath -Encoding ascii
Write-Host "Toolchain ready: $toolRoot"
