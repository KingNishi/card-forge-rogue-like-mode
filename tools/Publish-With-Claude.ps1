param([switch]$Authenticate,[string]$ClaudePath)
$ErrorActionPreference='Stop'
$repository=Split-Path -Parent $PSScriptRoot
$release=Split-Path -Parent $repository
if (Test-Path -LiteralPath (Join-Path $release 'PUBLISH_ON_HOLD.txt')) {throw 'Publication is on hold at the human request. Finish development and obtain a new explicit publication instruction first.'}
$dist=Join-Path $release 'dist'
$gh=if (Get-Command gh -ErrorAction SilentlyContinue) {(Get-Command gh).Source} else {'C:\Program Files\GitHub CLI\gh.exe'}
if (-not (Test-Path -LiteralPath $gh)) {throw 'Install GitHub CLI from https://cli.github.com/ first.'}
if (-not $ClaudePath) {
    $found=Get-Command claude -ErrorAction SilentlyContinue
    if ($found) {$ClaudePath=$found.Source} else {
        $embedded=Join-Path $env:LOCALAPPDATA 'Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude\claude-code'
        $ClaudePath=(Get-ChildItem -LiteralPath $embedded -Recurse -File -Filter 'claude.exe' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    }
}
if (-not $ClaudePath -or -not (Test-Path -LiteralPath $ClaudePath)) {throw 'Claude Code CLI not found. Install Claude Code, then run this script again.'}
$archive=Get-ChildItem -LiteralPath $dist -Filter '*Windows-x64.zip' | Select-Object -First 1
if (-not $archive -or -not (Test-Path -LiteralPath (Join-Path $dist 'SHA256SUMS.txt'))) {throw 'The tested release ZIP is missing.'}
$expected=((Get-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt') -First 1) -split '\s+')[0]
if ((Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {throw 'Release ZIP checksum mismatch.'}
& $gh auth status *> $null
if ($LASTEXITCODE -ne 0) {
    if ($Authenticate) {& $gh auth login --hostname github.com --git-protocol https --web; if ($LASTEXITCODE) {throw 'GitHub login was not completed.'}}
    else {throw 'GitHub CLI is not logged in. Run this script with -Authenticate to open the normal browser login.'}
}
$auth=& $ClaudePath auth status 2>$null | ConvertFrom-Json
if (-not $auth.loggedIn) {
    if ($Authenticate) {& $ClaudePath auth login; if ($LASTEXITCODE) {throw 'Claude Code login was not completed.'}}
    else {throw 'Claude Code CLI is not logged in. Run this script with -Authenticate to open the normal browser login.'}
}
$env:PATH=(Split-Path -Parent $gh)+';'+$env:PATH
$prompt=@"
The human explicitly authorizes publishing this completed project. Work only in $repository and upload only its tracked public source/assets plus the two approved release assets in $dist.
Create a PUBLIC repository named card-forge-rogue-like-mode under the currently authenticated GitHub account. The human requested this exact normalized name and public visibility. Keep prominent original Forge/Card-Forge credits and https://github.com/Card-Forge/forge in README. Do not claim original Forge authorship.
Read README, CREDITS, SOURCE and ../build-report.json first. Do not modify the live game at D:/forge. Do not read any credentials, chats, user profiles or unrelated folders. Never copy tokens from another app. Normal gh authentication is already checked by this launcher.
Read repository-metadata.json and use its exact description for the repository About description, including the voluntary PayPal donation link. Preserve .github/FUNDING.yml. Donations support the mod author only, are optional, and must never gate features or source access. Preserve the unmodified LICENSE, NOTICE.md, upstream copyright notices and GPL-3.0-or-later terms for code; do not relabel third-party artwork or components as newly owned GPL material.
Initialize a git repository here if needed, add the prepared public files only, make an initial commit and push main. Use the authenticated account's GitHub noreply email if author identity is needed. If a same-named repository already exists, inspect its ownership/history and do not overwrite unrelated contents or force-push. Do not delete anything. Create a v0.1.0 GitHub Release with $($archive.FullName) and $(Join-Path $dist 'SHA256SUMS.txt') as release assets (never add the game ZIP to git). Use CHANGELOG.md for the release notes. If v0.1.0 exists, compare asset hashes and stop on a conflicting release rather than silently replacing it.
Verify the public repository and downloadable release assets, then return the actual repository URL, release URL and a short result. Use gh and git for publication. If login or a required permission is unavailable, stop and say exactly what is missing. Do not invent success.
"@
Push-Location $repository
try {
    $prompt | & $ClaudePath -p --no-session-persistence --output-format text --add-dir $release --tools 'Read,Bash' --allowedTools 'Read' 'Bash(gh *)' 'Bash(git *)'
    if ($LASTEXITCODE) {throw 'Claude publication did not complete successfully.'}
} finally {Pop-Location}
