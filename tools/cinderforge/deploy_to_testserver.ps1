# Deploys the staged AscensionDungeons jar to the test server (port 25582): stops the server, backs up the old jar, installs
# the new one, and starts the server again. Run it yourself when nobody is connected:
#   powershell -ExecutionPolicy Bypass -File L:\ascensionDungeons\tools\cinderforge\deploy_to_testserver.ps1
$ErrorActionPreference = 'Stop'
$root   = 'L:\ascensionDungeons\testserver'
$staged = Join-Path $root 'mods-staged\AscensionDungeons-0.0.7-cinderforge.jar'
$backup = Join-Path $root 'mods-replaced-20261016'
$java   = 'L:\claude-cobbleraids-work\jdk-21.0.12.1+1\bin\java.exe'
if (-not (Test-Path $staged)) { throw "Staged jar not found: $staged" }

$listen = Get-NetTCPConnection -State Listen -LocalPort 25582 -ErrorAction SilentlyContinue
if ($listen) {
    $players = Get-NetTCPConnection -LocalPort 25582 -State Established -ErrorAction SilentlyContinue | Measure-Object
    if ($players.Count -gt 0) { throw "$($players.Count) connection(s) on 25582: wait until nobody is playing." }
    Write-Host "Stopping the test server (pid $($listen.OwningProcess))..."
    Stop-Process -Id $listen.OwningProcess -Force
    Start-Sleep -Seconds 5
}
New-Item -ItemType Directory -Force $backup | Out-Null
Get-ChildItem (Join-Path $root 'mods') -Filter 'AscensionDungeons-*.jar' | ForEach-Object {
    Write-Host "Backing up $($_.Name)"
    Move-Item $_.FullName (Join-Path $backup $_.Name) -Force
}
Copy-Item $staged (Join-Path $root 'mods') -Force
Write-Host 'Installed AscensionDungeons-0.0.7-cinderforge.jar. Starting the server...'
Start-Process -FilePath $java -ArgumentList '-Xmx4G', '-jar', 'fabric-server-launch.jar', 'nogui' -WorkingDirectory $root `
    -RedirectStandardOutput (Join-Path $root 'out.log') -RedirectStandardError (Join-Path $root 'err.log') -WindowStyle Hidden
Write-Host 'Started. Watch logs\latest.log for "Done". To roll back: stop the server, move the old jar back from mods-replaced-20261016.'
