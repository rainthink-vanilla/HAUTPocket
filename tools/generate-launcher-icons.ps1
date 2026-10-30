$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourcePath = Join-Path $projectRoot 'artwork/launcher.jpg'
$source = [System.Drawing.Image]::FromFile($sourcePath)
try {
    $res = Join-Path $projectRoot 'app/src/main/res'
    $targets = @{
        'drawable-nodpi/ic_launcher_artwork.png' = @(432,192)
    }
    foreach ($name in $targets.Keys) {
        $size, $artSize = $targets[$name]
        $path = Join-Path $res $name
        New-Item -ItemType Directory -Force -Path (Split-Path $path -Parent) | Out-Null
        $bitmap = New-Object System.Drawing.Bitmap($size,$size)
        $canvas = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $canvas.Clear([System.Drawing.Color]::White)
            $canvas.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $offset = [int](($size - $artSize) / 2)
            $canvas.DrawImage($source,$offset,$offset,$artSize,$artSize)
            $bitmap.Save($path,[System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $canvas.Dispose(); $bitmap.Dispose() }
    }
} finally { $source.Dispose() }
Write-Host 'Launcher resources generated from the unchanged user-supplied square artwork.'
