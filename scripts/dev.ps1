# Local dev: Redis in Docker (kept running across sessions) + backend/webapp
# on the host with hot-reload. Ctrl+C stops backend+webapp only.

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
Set-Location $RootDir

Write-Host "Ensuring dev Redis is up..."
docker compose -f docker/compose.local.db.yml up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to start dev Redis container."
    exit 1
}

Write-Host "Waiting for Redis to be ready..." -NoNewline
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    docker compose -f docker/compose.local.db.yml exec -T db redis-cli ping *>$null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Write-Host "." -NoNewline
    Start-Sleep -Seconds 1
}
Write-Host ""
if (-not $ready) {
    Write-Error "Redis did not become ready in time."
    exit 1
}

if (-not (Test-Path "node_modules")) {
    Write-Host "Installing dependencies (first run)..."
    npm install
}

Write-Host "Starting backend (server) and webapp..."
Write-Host "  webapp:  http://localhost:5173"
Write-Host "  server:  http://localhost:8000  (ws :4444)"
Write-Host "Press Ctrl+C to stop backend + webapp."
Write-Host "Redis dev container keeps running - stop it with 'docker compose -f docker/compose.local.db.yml down' when you're done for the day."
Write-Host ""

$backend = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "npm run dev --workspace=@tumaet/server" -NoNewWindow -PassThru

try {
    npm run start --workspace=@tumaet/webapp
}
finally {
    Write-Host "Stopping backend..."
    if (-not $backend.HasExited) {
        Stop-Process -Id $backend.Id -Force -ErrorAction SilentlyContinue
    }
}
