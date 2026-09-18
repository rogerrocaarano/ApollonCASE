# Stop and remove the ApollonCASE docker stack (containers + network).

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
Set-Location $RootDir

docker compose -f docker/compose.local.yml down @args
exit $LASTEXITCODE
