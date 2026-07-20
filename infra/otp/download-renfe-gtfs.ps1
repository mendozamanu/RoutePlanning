param(
    [uri]$SourceUri = "https://ssl.renfe.com/gtransit/Fichero_AV_LD/google_transit.zip",
    [string]$DestinationPath = (Join-Path $PSScriptRoot "..\..\data\raw\renfe\renfe-md-latest.gtfs.zip")
)

$ErrorActionPreference = "Stop"

$resolvedDestination = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath(
    $DestinationPath
)
$destinationDirectory = Split-Path -Parent $resolvedDestination
$temporaryPath = Join-Path $destinationDirectory (
    ".renfe-md-{0}.download" -f [guid]::NewGuid().ToString("N")
)

New-Item -ItemType Directory -Force -Path $destinationDirectory | Out-Null

try {
    Write-Output "Downloading the official Renfe Media Distancia GTFS..."
    Invoke-WebRequest -Uri $SourceUri -OutFile $temporaryPath

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($temporaryPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.ToLowerInvariant() })
        $requiredEntries = @(
            "agency.txt",
            "stops.txt",
            "routes.txt",
            "trips.txt",
            "stop_times.txt"
        )
        $missingEntries = @($requiredEntries | Where-Object { $_ -notin $entryNames })
        if ($missingEntries.Count -gt 0) {
            throw "The Renfe download is not a valid GTFS. Missing: $($missingEntries -join ', ')"
        }

        $stopsEntry = $archive.Entries | Where-Object {
            $_.FullName.Equals("stops.txt", [System.StringComparison]::OrdinalIgnoreCase)
        } | Select-Object -First 1
        $reader = [System.IO.StreamReader]::new($stopsEntry.Open())
        try {
            $stopsText = $reader.ReadToEnd()
        } finally {
            $reader.Dispose()
        }
        if (
            -not $stopsText.Contains("Córdoba-Julio Anguita") -or
            -not $stopsText.Contains("Campus Universitario de Rabanales")
        ) {
            throw "The downloaded Renfe GTFS does not contain the Córdoba-Rabanales service."
        }
    } finally {
        $archive.Dispose()
    }

    Move-Item -LiteralPath $temporaryPath -Destination $resolvedDestination -Force
    $file = Get-Item -LiteralPath $resolvedDestination
    $checksum = (Get-FileHash -LiteralPath $resolvedDestination -Algorithm SHA256).Hash.ToLowerInvariant()
    Write-Output "Renfe GTFS saved to $resolvedDestination"
    Write-Output "SHA-256: $checksum"
    Write-Output "Bytes: $($file.Length)"
} finally {
    if (Test-Path -LiteralPath $temporaryPath) {
        Remove-Item -LiteralPath $temporaryPath -Force
    }
}
