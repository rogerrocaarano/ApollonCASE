# Build and start the full ApollonCASE stack (webapp + server + redis) in Docker.

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
Set-Location $RootDir

Write-Host "Starting ApollonCASE (docker) - webapp: http://localhost:8080  server: http://localhost:8000 (ws :4444)"
Write-Host "Press Ctrl+C to stop. Run scripts/docker-down.ps1 afterwards to remove containers/network."

docker compose -f docker/compose.local.yml up --build @args
exit $LASTEXITCODE
