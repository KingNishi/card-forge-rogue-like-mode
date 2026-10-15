param([string]$Target,[switch]$NoShortcuts,[switch]$NonInteractive)
$ErrorActionPreference='Stop'
$package=Split-Path -Parent $PSScriptRoot
$game=Join-Path $package 'game'
$runtime=Join-Path $package 'runtime'
if (-not $Target) {
    $default=Join-Path $env:LOCALAPPDATA 'Programs\CardForge-Rogue'
    if ($NonInteractive) {$Target=$default} else {
        $answer=Read-Host "Install folder [$default]"
        $Target=if ([string]::IsNullOrWhiteSpace($answer)) {$default} else {$answer.Trim().Trim('"')}
    }
}
$Target=[IO.Path]::GetFullPath($Target).TrimEnd('\')
$package=[IO.Path]::GetFullPath($package).TrimEnd('\')
if ($Target.Length -lt 4 -or $Target -eq [IO.Path]::GetPathRoot($Target).TrimEnd('\')) {throw 'Choose a dedicated game folder.'}
if ($Target.Equals($package,[StringComparison]::OrdinalIgnoreCase) -or $Target.StartsWith($package+'\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Choose a folder outside the extracted installer package.'}
$existing=Test-Path -LiteralPath $Target
if ($existing -and (Get-ChildItem -LiteralPath $Target -Force | Select-Object -First 1)) {
    if (-not (Test-Path -LiteralPath (Join-Path $Target '.card-forge-rogue'))) {throw 'The folder is not empty. Choose a new folder; existing files were not changed.'}
    $running=@(Get-CimInstance Win32_Process | Where-Object {$_.Name -match '^javaw?\.exe$' -and $_.CommandLine -match 'forge-gui'})
    if ($running.Count) {throw 'Close Forge before updating an existing installation.'}
}
Write-Host 'Verifying installation files ...'
$manifest=Get-Content -LiteralPath (Join-Path $package 'manifest.json') -Raw | ConvertFrom-Json
foreach ($entry in $manifest.files) {
    $path=[IO.Path]::GetFullPath((Join-Path $package $entry.path))
    if (-not $path.StartsWith($package+'\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Invalid file manifest path.'}
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {throw "Missing file: $($entry.path)"}
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {throw "File checksum mismatch: $($entry.path)"}
}
function Copy-Tree([string]$From,[string]$To) {
    & robocopy $From $To /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) {throw "Copy failed: $From"}
}
Write-Host "Installing to $Target ..."
New-Item -ItemType Directory -Path $Target -Force | Out-Null
Copy-Tree $game $Target
Copy-Tree $runtime (Join-Path $Target 'runtime')
$java=Join-Path $Target 'runtime\bin\java.exe'
$jar=Join-Path $Target 'forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar'
& $java -cp (Join-Path $package 'installer') Relocate $Target $jar
if ($LASTEXITCODE) {throw 'Could not relocate the game to its installation folder.'}
$slash=($Target -replace '\\','/')+'/'
$back=($Target+'\').Replace('\','\\')
Get-ChildItem -LiteralPath (Join-Path $Target '_mods') -Recurse -File -Include *.json,*.properties,*.txt | ForEach-Object {
    $text=[IO.File]::ReadAllText($_.FullName)
    $replaced=$text.Replace('D:/forge/',$slash).Replace('D:\\forge\\',$back)
    if ($replaced -ne $text) {[IO.File]::WriteAllText($_.FullName,$replaced,(New-Object Text.UTF8Encoding $false))}
}
Set-Content -LiteralPath (Join-Path $Target '.card-forge-rogue') -Value ('version='+$manifest.version) -Encoding ASCII
Copy-Item -LiteralPath (Join-Path $package 'manifest.json') -Destination (Join-Path $Target 'installed-manifest.json') -Force
if (-not $NoShortcuts) {
    $shell=New-Object -ComObject WScript.Shell
    $desktop=[Environment]::GetFolderPath('Desktop')
    $shortcut=$shell.CreateShortcut((Join-Path $desktop 'Card Forge Rogue Like Mode.lnk'))
    $shortcut.TargetPath=Join-Path $Target 'Rogue Commanders.cmd'
    $shortcut.WorkingDirectory=$Target
    $shortcut.WindowStyle=7
    $shortcut.Save()
}
Write-Host ''
Write-Host 'Installed. Start Rogue Commanders.cmd in your installation folder.' -ForegroundColor Green
Write-Host 'Your existing Forge saves and settings were not copied or replaced.'
