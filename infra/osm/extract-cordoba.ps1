param(
    [string]$SourcePath = "andalucia-260717.osm.pbf",
    [string]$OutputPath = "data\raw\osm\cordoba.osm.pbf"
)

$ErrorActionPreference = "Stop"
$workspaceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$resolvedSource = (Resolve-Path -LiteralPath (Join-Path $workspaceRoot $SourcePath)).Path
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $workspaceRoot $OutputPath))
$workspacePrefix = $workspaceRoot.TrimEnd('\') + '\'

if (-not $resolvedSource.StartsWith($workspacePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The source PBF must be inside the workspace."
}
if (-not $resolvedOutput.StartsWith($workspacePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The output PBF must be inside the workspace."
}
if (-not $resolvedSource.EndsWith(".osm.pbf", [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "The source must be an .osm.pbf file."
}
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "The output already exists: $resolvedOutput"
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerExecutable = if ($dockerCommand) {
    $dockerCommand.Source
} else {
    "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
}
if (-not (Test-Path -LiteralPath $dockerExecutable)) {
    throw "Docker Desktop is not installed or its CLI could not be found."
}
$dockerBinDirectory = Split-Path -Parent $dockerExecutable
$env:PATH = "$dockerBinDirectory;$env:PATH"

$outputDirectory = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

& $dockerExecutable build `
    --tag route-planning-osmium:bookworm `
    --file (Join-Path $PSScriptRoot "Dockerfile") `
    $PSScriptRoot
if ($LASTEXITCODE -ne 0) {
    throw "Could not build the local osmium image."
}

$sourceInContainer = "/work/" + [System.IO.Path]::GetRelativePath(
    $workspaceRoot,
    $resolvedSource
).Replace('\', '/')
$outputInContainer = "/work/" + [System.IO.Path]::GetRelativePath(
    $workspaceRoot,
    $resolvedOutput
).Replace('\', '/')

& $dockerExecutable run --rm `
    --mount "type=bind,source=$workspaceRoot,target=/work" `
    route-planning-osmium:bookworm `
    extract `
    --bbox=-5.04,37.75,-4.55,38.10 `
    --strategy=complete_ways `
    --set-bounds `
    --output=$outputInContainer `
    $sourceInContainer
if ($LASTEXITCODE -ne 0) {
    throw "Osmium could not create the Córdoba extract."
}

Write-Output "Córdoba OSM extract created at $resolvedOutput"
