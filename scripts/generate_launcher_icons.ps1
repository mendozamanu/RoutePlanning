param(
    [string]$SourcePath = "app_ico_new.png",
    [string]$ResourceRoot = "app\src\main\res"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$resolvedSource = (Resolve-Path -LiteralPath $SourcePath).Path
$resolvedResourceRoot = (Resolve-Path -LiteralPath $ResourceRoot).Path
$source = [System.Drawing.Bitmap]::FromFile($resolvedSource)

$densities = [ordered]@{
    "mdpi" = @{ Legacy = 48; Foreground = 108 }
    "hdpi" = @{ Legacy = 72; Foreground = 162 }
    "xhdpi" = @{ Legacy = 96; Foreground = 216 }
    "xxhdpi" = @{ Legacy = 144; Foreground = 324 }
    "xxxhdpi" = @{ Legacy = 192; Foreground = 432 }
}

function New-HighQualityGraphics {
    param([System.Drawing.Bitmap]$Bitmap)

    $graphics = [System.Drawing.Graphics]::FromImage($Bitmap)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    return $graphics
}

function Save-SquareIcon {
    param(
        [System.Drawing.Bitmap]$InputImage,
        [int]$Size,
        [string]$Destination
    )

    $output = [System.Drawing.Bitmap]::new(
        $Size,
        $Size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = New-HighQualityGraphics -Bitmap $output
    try {
        $graphics.DrawImage($InputImage, 0, 0, $Size, $Size)
        $output.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $output.Dispose()
    }
}

function Save-RoundIcon {
    param(
        [System.Drawing.Bitmap]$InputImage,
        [int]$Size,
        [string]$Destination
    )

    $workingSize = $Size * 4
    $working = [System.Drawing.Bitmap]::new(
        $workingSize,
        $workingSize,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = New-HighQualityGraphics -Bitmap $working
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $path.AddEllipse(1, 1, $workingSize - 2, $workingSize - 2)
        $graphics.SetClip($path)
        $graphics.DrawImage($InputImage, 0, 0, $workingSize, $workingSize)
    } finally {
        $path.Dispose()
        $graphics.Dispose()
    }

    try {
        Save-SquareIcon -InputImage $working -Size $Size -Destination $Destination
    } finally {
        $working.Dispose()
    }
}

function Save-AdaptiveForeground {
    param(
        [System.Drawing.Bitmap]$InputImage,
        [int]$Size,
        [string]$Destination
    )

    $output = [System.Drawing.Bitmap]::new(
        $Size,
        $Size,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = New-HighQualityGraphics -Bitmap $output
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $artworkSize = [int][Math]::Round($Size * 2 / 3)
        $offset = [int][Math]::Round(($Size - $artworkSize) / 2)
        $graphics.DrawImage($InputImage, $offset, $offset, $artworkSize, $artworkSize)
        $output.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose()
        $output.Dispose()
    }
}

try {
    foreach ($entry in $densities.GetEnumerator()) {
        $directory = Join-Path $resolvedResourceRoot "mipmap-$($entry.Key)"
        $legacySize = $entry.Value.Legacy
        $foregroundSize = $entry.Value.Foreground

        Save-SquareIcon `
            -InputImage $source `
            -Size $legacySize `
            -Destination (Join-Path $directory "ic_launcher.png")
        Save-RoundIcon `
            -InputImage $source `
            -Size $legacySize `
            -Destination (Join-Path $directory "ic_launcher_round.png")
        Save-AdaptiveForeground `
            -InputImage $source `
            -Size $foregroundSize `
            -Destination (Join-Path $directory "ic_launcher_foreground.png")
    }
} finally {
    $source.Dispose()
}

Write-Output "Launcher icons generated from $resolvedSource"
