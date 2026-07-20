param(
    [string]$GtfsPath = (Join-Path $PSScriptRoot "..\..\data\raw\aucorsa\aucorsa-20260225-assumed-20260719.gtfs.zip"),
    [string]$RenfeGtfsPath = (Join-Path $PSScriptRoot "..\..\data\raw\renfe\renfe-md-latest.gtfs.zip"),
    [Parameter(Mandatory = $true)]
    [string]$OsmPath
)

$ErrorActionPreference = "Stop"

$resolvedGtfsPath = (Resolve-Path -LiteralPath $GtfsPath).Path
$resolvedRenfeGtfsPath = (Resolve-Path -LiteralPath $RenfeGtfsPath).Path
$resolvedOsmPath = (Resolve-Path -LiteralPath $OsmPath).Path
$dataDirectory = Join-Path $PSScriptRoot "data"

if ([System.IO.Path]::GetExtension($resolvedGtfsPath) -ne ".zip") {
    throw "The AUCORSA GTFS input must be a ZIP file."
}
if ([System.IO.Path]::GetExtension($resolvedRenfeGtfsPath) -ne ".zip") {
    throw "The Renfe GTFS input must be a ZIP file."
}
if (-not $resolvedOsmPath.EndsWith(".osm.pbf", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The OpenStreetMap input must be an .osm.pbf file."
}

New-Item -ItemType Directory -Force -Path $dataDirectory | Out-Null
Copy-Item -LiteralPath $resolvedGtfsPath -Destination (Join-Path $dataDirectory "aucorsa.gtfs.zip")
Copy-Item -LiteralPath $resolvedRenfeGtfsPath -Destination (Join-Path $dataDirectory "renfe-md.gtfs.zip")
Copy-Item -LiteralPath $resolvedOsmPath -Destination (Join-Path $dataDirectory "cordoba.osm.pbf")

Write-Output "OTP inputs prepared in $dataDirectory"
Write-Warning "AUCORSA dates after 2026-03-31 use an assumed weekly schedule, not newly confirmed or real-time data. Refresh the official Renfe feed before rebuilding OTP."
