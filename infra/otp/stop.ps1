$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "docker-command.ps1")

$dockerExecutable = Get-DockerExecutable
& $dockerExecutable compose `
    --file (Join-Path $PSScriptRoot "docker-compose.yml") `
    down
if ($LASTEXITCODE -ne 0) {
    throw "OpenTripPlanner could not stop cleanly."
}
