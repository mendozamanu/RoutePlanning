$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backendDirectory = Join-Path $repositoryRoot "backend"
$pythonExecutable = Join-Path $backendDirectory ".venv-win\Scripts\python.exe"
$otpDataDirectory = Join-Path $repositoryRoot "infra\otp\data"
$aucorsaGtfsPath = Join-Path $otpDataDirectory "aucorsa.gtfs.zip"
$renfeGtfsPath = Join-Path $otpDataDirectory "renfe-md.gtfs.zip"

function Get-GtfsServiceEndDate {
    param([Parameter(Mandatory = $true)][string]$Path)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $dates = [System.Collections.Generic.List[datetime]]::new()
    $archive = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        foreach ($entryName in @("calendar.txt", "calendar_dates.txt")) {
            $entry = $archive.Entries | Where-Object {
                $_.FullName.Equals($entryName, [System.StringComparison]::OrdinalIgnoreCase)
            } | Select-Object -First 1
            if ($null -eq $entry) {
                continue
            }
            $reader = [System.IO.StreamReader]::new($entry.Open())
            try {
                $null = $reader.ReadLine()
                while (-not $reader.EndOfStream) {
                    $fields = $reader.ReadLine().Trim().Split(',')
                    $value = if ($entryName -eq "calendar.txt") {
                        $fields[-1]
                    } elseif ($fields.Count -ge 2) {
                        $fields[1]
                    } else {
                        $null
                    }
                    $parsed = [datetime]::MinValue
                    if (
                        $value -and
                        [datetime]::TryParseExact(
                            $value.Trim(),
                            "yyyyMMdd",
                            [System.Globalization.CultureInfo]::InvariantCulture,
                            [System.Globalization.DateTimeStyles]::None,
                            [ref]$parsed
                        )
                    ) {
                        $dates.Add($parsed)
                    }
                }
            } finally {
                $reader.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
    if ($dates.Count -eq 0) {
        throw "No service end date could be read from $Path"
    }
    return ($dates | Sort-Object | Select-Object -Last 1)
}

if (-not (Test-Path -LiteralPath $pythonExecutable -PathType Leaf)) {
    throw "Backend virtual environment not found. Create backend\.venv-win and install backend[dev]."
}
foreach ($gtfsPath in @($aucorsaGtfsPath, $renfeGtfsPath)) {
    if (-not (Test-Path -LiteralPath $gtfsPath -PathType Leaf)) {
        throw "Missing prepared GTFS: $gtfsPath. Run infra\otp\prepare-data.ps1 first."
    }
}

$aucorsaChecksum = (Get-FileHash -LiteralPath $aucorsaGtfsPath -Algorithm SHA256).Hash
$renfeChecksum = (Get-FileHash -LiteralPath $renfeGtfsPath -Algorithm SHA256).Hash
$serviceEnd = @(
    Get-GtfsServiceEndDate -Path $aucorsaGtfsPath
    Get-GtfsServiceEndDate -Path $renfeGtfsPath
) | Sort-Object | Select-Object -First 1

$env:OTP_BASE_URL = "http://localhost:8080/otp/gtfs/v1"
$env:TRANSIT_DATA_VERSION = "cordoba-aucorsa-$($aucorsaChecksum.Substring(0, 12).ToLowerInvariant())-renfe-$($renfeChecksum.Substring(0, 12).ToLowerInvariant())"
$env:TRANSIT_DATA_EXPIRES_ON = $serviceEnd.ToString("yyyy-MM-dd")

Write-Output "Starting Route Planning API on http://0.0.0.0:8000"
Write-Output "Android emulator endpoint: http://10.0.2.2:8000"
$lanAddresses = @(
    [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        Where-Object {
            $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up -and
            $_.NetworkInterfaceType -notin @(
                [System.Net.NetworkInformation.NetworkInterfaceType]::Loopback,
                [System.Net.NetworkInformation.NetworkInterfaceType]::Tunnel
            )
        } |
        ForEach-Object {
            $properties = $_.GetIPProperties()
            $hasIpv4Gateway = @($properties.GatewayAddresses).Where({
                $_.Address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork
            }).Count -gt 0
            if ($hasIpv4Gateway) {
                $properties.UnicastAddresses |
                    Where-Object {
                        $_.Address.AddressFamily -eq
                            [System.Net.Sockets.AddressFamily]::InterNetwork
                    } |
                    ForEach-Object { $_.Address.IPAddressToString }
            }
        } |
        Sort-Object -Unique
)
if ($lanAddresses.Count -eq 0) {
    Write-Warning "No LAN IPv4 address was detected for a physical Android device."
} else {
    $lanAddresses | ForEach-Object {
        Write-Output "Physical Android endpoint: http://${_}:8000"
    }
    Write-Output "The phone and this computer must use the same local network."
}
Write-Output "Transit data version: $env:TRANSIT_DATA_VERSION"
Write-Output "Combined schedule expires on: $env:TRANSIT_DATA_EXPIRES_ON"
Write-Warning "Scheduled service after 2026-03-31 uses the documented assumed-current GTFS projection."
Write-Warning "Renfe results use its latest downloaded static GTFS and do not yet include service alerts or real-time changes."

Push-Location $backendDirectory
try {
    & $pythonExecutable -m uvicorn route_planning_api.main:app `
        --host 0.0.0.0 `
        --port 8000 `
        --reload
    if ($LASTEXITCODE -ne 0) {
        throw "Route Planning API stopped with an error."
    }
} finally {
    Pop-Location
}
