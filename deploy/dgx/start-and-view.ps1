[CmdletBinding()]
param(
    [ValidateSet("Start", "Status", "Stop")]
    [string]$Action = "Start",
    [string]$HostName = "whtc@100.119.145.78",
    [string]$IdentityFile = "$env:USERPROFILE\.ssh\id_ed25519_dgx_spark",
    [string]$RemotePath = "/home/whtc/workspace/projects/road-agent-dgx",
    [ValidateRange(1024, 65535)]
    [int]$LocalPort = 18080,
    [switch]$SkipRemoteStart,
    [switch]$SkipSmoke,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$StateFile = Join-Path ([System.IO.Path]::GetTempPath()) "road-agent-dgx-tunnel-$LocalPort.json"
$MainUrl = "http://127.0.0.1:$LocalPort/"
$DemoUrl = "http://127.0.0.1:$LocalPort/digital-human-demo.html"

function Get-ManagedTunnel {
    if (-not (Test-Path -LiteralPath $StateFile -PathType Leaf)) {
        return $null
    }

    try {
        $State = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        $Process = Get-Process -Id ([int]$State.pid) -ErrorAction Stop
        if ($Process.ProcessName -notin @("ssh", "ssh.exe")) {
            return $null
        }
        return $Process
    } catch {
        return $null
    }
}

function Test-Frontend {
    try {
        $Response = Invoke-WebRequest -UseBasicParsing -Uri $MainUrl -TimeoutSec 3
        return $Response.StatusCode -eq 200
    } catch {
        return $false
    }
}

if ($Action -eq "Stop") {
    $Tunnel = Get-ManagedTunnel
    if ($null -ne $Tunnel) {
        Stop-Process -Id $Tunnel.Id -Force
        Write-Output "Stopped SSH tunnel PID $($Tunnel.Id)."
    } else {
        Write-Output "No managed SSH tunnel is running on local port $LocalPort."
    }
    Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
    exit 0
}

if ($Action -eq "Status") {
    $Tunnel = Get-ManagedTunnel
    $TunnelStatus = if ($null -ne $Tunnel) { "running (PID $($Tunnel.Id))" } else { "not managed/running" }
    $FrontendStatus = if (Test-Frontend) { "reachable" } else { "unreachable" }
    Write-Output "SSH tunnel: $TunnelStatus"
    Write-Output "Frontend: $FrontendStatus - $MainUrl"
    exit 0
}

$ResolvedIdentity = (Resolve-Path -LiteralPath $IdentityFile).Path

if (-not $SkipRemoteStart) {
    $RemoteCommands = @(
        "cd '$RemotePath'",
        "deploy/dgx/dgx-stack up",
        "deploy/dgx/dgx-stack status"
    )
    if (-not $SkipSmoke) {
        $RemoteCommands += "deploy/dgx/dgx-stack smoke --model-iterations 3 --with-tts --with-traffic --with-agent"
    }

    & ssh -i $ResolvedIdentity -o BatchMode=yes -o IdentitiesOnly=yes -o ConnectTimeout=10 `
        $HostName ($RemoteCommands -join " && ")
    if ($LASTEXITCODE -ne 0) {
        throw "DGX startup or smoke test failed with exit code $LASTEXITCODE."
    }
}

$Tunnel = Get-ManagedTunnel
if ($null -eq $Tunnel) {
    Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
    $Forward = "127.0.0.1:${LocalPort}:127.0.0.1:18080"
    $SshArguments = @(
        "-N",
        "-L", $Forward,
        "-i", ('"' + $ResolvedIdentity + '"'),
        "-o", "BatchMode=yes",
        "-o", "IdentitiesOnly=yes",
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3",
        $HostName
    )
    $Tunnel = Start-Process -FilePath "ssh.exe" -ArgumentList $SshArguments -WindowStyle Hidden -PassThru
    @{
        pid = $Tunnel.Id
        hostName = $HostName
        localPort = $LocalPort
        startedAt = (Get-Date).ToString("o")
    } | ConvertTo-Json | Set-Content -LiteralPath $StateFile -Encoding utf8
}

$Ready = $false
for ($Attempt = 0; $Attempt -lt 20; $Attempt++) {
    if (Test-Frontend) {
        $Ready = $true
        break
    }
    if ($Tunnel.HasExited) {
        break
    }
    Start-Sleep -Milliseconds 500
}

if (-not $Ready) {
    if (-not $Tunnel.HasExited) {
        Stop-Process -Id $Tunnel.Id -Force
    }
    Remove-Item -LiteralPath $StateFile -Force -ErrorAction SilentlyContinue
    throw "SSH tunnel started, but the frontend did not become reachable at $MainUrl."
}

Write-Output "Road Agent is ready."
Write-Output "Main page: $MainUrl"
Write-Output "Digital human demo: $DemoUrl"
Write-Output "Tunnel PID: $($Tunnel.Id)"
Write-Output "Stop tunnel: .\deploy\dgx\start-and-view.ps1 -Action Stop -LocalPort $LocalPort"

if (-not $NoBrowser) {
    Start-Process $MainUrl
}
