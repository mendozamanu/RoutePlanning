function Get-DockerExecutable {
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
    return $dockerExecutable
}
