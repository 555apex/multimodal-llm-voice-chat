param(
    [string]$HostName = "whtc@100.119.145.78",
    [string]$IdentityFile = "$env:USERPROFILE\.ssh\id_ed25519_dgx_spark",
    [string]$RemotePath = "/home/whtc/workspace/projects/road-agent-dgx",
    [string]$SecretsFile = "C:\Users\Lenovo\Desktop\multimodal-llm-voice-chat\config\api-test.ps1",
    [switch]$RefreshSecrets
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$ResolvedIdentity = (Resolve-Path $IdentityFile).Path
$Archive = Join-Path ([System.IO.Path]::GetTempPath()) ("road-agent-dgx-{0}.tar.gz" -f [guid]::NewGuid())
$RemoteArchive = "/tmp/$(Split-Path -Leaf $Archive)"
$DeployId = [guid]::NewGuid().ToString("N")
$RemoteStage = "/tmp/road-agent-dgx-stage-$DeployId"
$RemotePrevious = "/tmp/road-agent-dgx-previous-$DeployId"

try {
    & tar -czf $Archive `
        --exclude=.git `
        --exclude=.idea `
        --exclude=target `
        --exclude=node_modules `
        --exclude=dist `
        --exclude=.npm-cache `
        --exclude=.pytest_cache `
        --exclude=pytest-of-* `
        --exclude=__pycache__ `
        --exclude=.env `
        --exclude=.env.db-admin `
        --exclude=config/api-test.ps1 `
        --exclude=config/api-test.env `
        -C $ProjectRoot .
    if ($LASTEXITCODE -ne 0) { throw "project archive failed" }

    & scp -i $ResolvedIdentity -o IdentitiesOnly=yes $Archive "${HostName}:${RemoteArchive}"
    if ($LASTEXITCODE -ne 0) { throw "archive upload failed" }

    if (-not $RefreshSecrets) {
        & ssh -i $ResolvedIdentity -o IdentitiesOnly=yes $HostName `
            "test -s '$RemotePath/deploy/dgx/.env'"
        if ($LASTEXITCODE -ne 0) {
            throw "remote .env does not exist; use -RefreshSecrets for the first deployment"
        }
    }

    $RemoteInstall = @"
set -eu
stage='$RemoteStage'
previous='$RemotePrevious'
remote='$RemotePath'
archive='$RemoteArchive'
backup_root='/home/whtc/workspace/backups/road-agent-project'
rm -rf "`$stage" "`$previous"
mkdir -p "`$stage" "`$backup_root"
tar -xzf "`$archive" -C "`$stage"
if [ -d "`$remote" ]; then
  timestamp=`$(date -u +%Y%m%dT%H%M%SZ)
  tar --exclude='./deploy/dgx/.env' --exclude='./deploy/dgx/.env.db-admin' \
      --exclude='./.releases' --exclude='./.releases/*' \
      --exclude='*/target' --exclude='*/target/*' \
      --exclude='*/node_modules' --exclude='*/node_modules/*' \
      --exclude='*/dist' --exclude='*/dist/*' \
      --exclude='*/__pycache__' --exclude='*/__pycache__/*' \
      --exclude='./deploy/dgx/.download-venv' --exclude='./deploy/dgx/.download-venv/*' \
      -czf "`$backup_root/road-agent-dgx-`$timestamp.tar.gz" -C "`$remote" .
  if [ -f "`$remote/deploy/dgx/.env" ]; then cp -p "`$remote/deploy/dgx/.env" "`$stage/deploy/dgx/.env"; fi
  if [ -f "`$remote/deploy/dgx/.env.db-admin" ]; then cp -p "`$remote/deploy/dgx/.env.db-admin" "`$stage/deploy/dgx/.env.db-admin"; fi
  mv "`$remote" "`$previous"
fi
mv "`$stage" "`$remote"
if [ -d "`$previous" ]; then
  if ! rm -rf "`$previous" 2>/dev/null; then
    # A previous containerized build may have left root-owned target files.
    # Restrict the root cleanup container to this one validated deployment directory.
    test "`$(readlink -f -- "`$previous")" = "`$previous"
    docker run --rm --user 0 --entrypoint /bin/sh \
      -v "`$previous`:/cleanup" road-agent-dgx-frontend:local \
      -c 'find /cleanup -mindepth 1 -delete'
    rmdir "`$previous"
  fi
fi
rm -f "`$archive"
chmod +x "`$remote/deploy/dgx/dgx-stack" "`$remote/deploy/dgx/db-maintenance" "`$remote/deploy/dgx/database-switch" "`$remote/deploy/dgx/public-db-access" "`$remote/deploy/dgx/mysql-client-entrypoint" "`$remote/deploy/dgx/download-speech-models" "`$remote/deploy/dgx/smoke.py"
if [ -f "`$remote/deploy/dgx/.env" ]; then chmod 600 "`$remote/deploy/dgx/.env"; fi
if [ -f "`$remote/deploy/dgx/.env.db-admin" ]; then chmod 600 "`$remote/deploy/dgx/.env.db-admin"; fi
"@
    $RemoteInstall = $RemoteInstall -replace "`r", ""
    & ssh -i $ResolvedIdentity -o IdentitiesOnly=yes $HostName $RemoteInstall
    if ($LASTEXITCODE -ne 0) { throw "remote extraction failed" }

    if ($RefreshSecrets) {
        $ResolvedSecrets = (Resolve-Path $SecretsFile).Path
        . $ResolvedSecrets
        $Required = @("ROADAGENT_DB_URL", "ROADAGENT_DB_USERNAME", "ROADAGENT_DB_PASSWORD")
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
    }

    Write-Output "Uploaded Road Agent to $HostName`:$RemotePath (existing secrets preserved: $(-not $RefreshSecrets))"
} finally {
    if (Test-Path -LiteralPath $Archive -PathType Leaf) {
        Remove-Item -LiteralPath $Archive -Force
    }
}
