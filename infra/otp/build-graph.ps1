$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "docker-command.ps1")

$dockerExecutable = Get-DockerExecutable
& $dockerExecutable compose `
    --file (Join-Path $PSScriptRoot "docker-compose.yml") `
    --profile build `
    run `
    --rm `
    otp-build
if ($LASTEXITCODE -ne 0) {
    throw "OpenTripPlanner could not build the local graph."
}
