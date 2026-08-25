param(
    [string]$HostName = "whtc@100.119.145.78",
    [string]$IdentityFile = "$env:USERPROFILE\.ssh\id_ed25519_dgx_spark",
    [string]$RemotePath = "/home/whtc/workspace/projects/road-agent-dgx",
    [string]$SecretsFile = "C:\Users\Lenovo\Desktop\multimodal-llm-voice-chat\config\api-test.ps1"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ResolvedIdentity = (Resolve-Path $IdentityFile).Path
$ResolvedSecrets = (Resolve-Path $SecretsFile).Path
$Archive = Join-Path ([System.IO.Path]::GetTempPath()) ("road-agent-dgx-{0}.tar.gz" -f [guid]::NewGuid())
$RemoteArchive = "/tmp/$(Split-Path -Leaf $Archive)"

try {
    & tar -czf $Archive `
        --exclude=.git `
        --exclude=.idea `
        --exclude=target `
        --exclude=node_modules `
        --exclude=dist `
        --exclude=.npm-cache `
        --exclude=.env `
        --exclude=config/api-test.ps1 `
        --exclude=config/api-test.env `
        -C $ProjectRoot .
    if ($LASTEXITCODE -ne 0) { throw "project archive failed" }

    & scp -i $ResolvedIdentity -o IdentitiesOnly=yes $Archive "${HostName}:${RemoteArchive}"
    if ($LASTEXITCODE -ne 0) { throw "archive upload failed" }

    & ssh -i $ResolvedIdentity -o IdentitiesOnly=yes $HostName `
        "mkdir -p '$RemotePath' && tar -xzf '$RemoteArchive' -C '$RemotePath' && rm -f '$RemoteArchive' && chmod +x '$RemotePath/deploy/dgx/dgx-stack' '$RemotePath/deploy/dgx/download-speech-models' '$RemotePath/deploy/dgx/smoke.py'"
    if ($LASTEXITCODE -ne 0) { throw "remote extraction failed" }

    . $ResolvedSecrets
    $Required = @("ROADAGENT_DB_URL", "ROADAGENT_DB_USERNAME", "ROADAGENT_DB_PASSWORD", "AMAP_API_KEY")
    foreach ($Name in $Required) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name))) {
            throw "missing required secret in ${ResolvedSecrets}: $Name"
        }
    }

    $Template = Get-Content -LiteralPath (Join-Path $ProjectRoot "deploy\dgx\.env.example")
    $Overrides = @{}
    foreach ($Name in $Required) {
        $Overrides[$Name] = [Environment]::GetEnvironmentVariable($Name)
    }
    $Lines = foreach ($Line in $Template) {
        if ($Line -match '^([A-Z][A-Z0-9_]*)=') {
            $Name = $Matches[1]
            if ($Overrides.ContainsKey($Name)) { "${Name}=$($Overrides[$Name])" } else { $Line }
        } else {
            $Line
        }
    }
    ($Lines -join "`n") | & ssh -i $ResolvedIdentity -o IdentitiesOnly=yes $HostName `
        "umask 077; tr -d '\r' > '$RemotePath/deploy/dgx/.env'"
    if ($LASTEXITCODE -ne 0) { throw "secure environment upload failed" }

    Write-Output "Uploaded Road Agent to $HostName`:$RemotePath"
} finally {
    if (Test-Path -LiteralPath $Archive -PathType Leaf) {
        Remove-Item -LiteralPath $Archive -Force
    }
}
