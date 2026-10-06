<#
.SYNOPSIS
Prepares a Minecraft 1.20.1 / Forge 47.3.5 Prism test instance without launching it.
.DESCRIPTION
Creates a reviewed instance directory and importable MultiMC/Prism zip under the
repository's ignored .local/prism directory. Only an explicit -InstancesDirectory
copies the validated instance into a launcher profile. Existing instances, output
archives and shortcuts are never overwritten. No accounts or tokens are accessed,
and no Minecraft dependencies are downloaded. A supplied -ShortcutPath creates an
offline launch shortcut only after explicit installation into an instances folder.
The default 4096 MiB heap is intended for later testing after the RAM upgrade.
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File ./New-YigdPrismInstance.ps1 -ModJarPath C:/build/yigd.jar -ClothConfigJarPath C:/mods/cloth.jar
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ModJarPath,
    [Parameter(Mandatory)] [string] $ClothConfigJarPath,
    [string] $JavaHome,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$')] [string] $InstanceId = 'YIGD-1_20_1-repair',
    [ValidatePattern('^[^\r\n\[\]\\]+$')] [string] $Name = 'YIGD 1.20.1 repair test',
    [ValidateRange(512, 16384)] [int] $MaxMemAlloc = 4096,
    [string] $OutputDirectory,
    [string] $InstancesDirectory,
    [string] $PrismLauncherPath,
    [ValidatePattern('^[A-Za-z0-9_]{3,16}$')] [string] $OfflinePlayerName = 'TriagePlayer',
    [string] $ShortcutPath
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$jdk = Resolve-TriageJdk17 -JavaHome $JavaHome
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Assert-PrismInputJar {
    param([string] $Path, [string] $ModId, [string] $RequiredClass)
    $file = Get-Item -LiteralPath $Path
    if ($file.PSIsContainer -or $file.Extension -ne '.jar') { throw "Expected a jar file: $Path" }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $entry = $archive.GetEntry('META-INF/mods.toml')
        if (-not $entry) { throw "Expected a Forge jar with META-INF/mods.toml: $Path" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $modPattern = '(?m)^\s*modId\s*=\s*"' + [regex]::Escape($ModId) + '"\s*$'
        if ($metadata -notmatch $modPattern) { throw "Expected modId $ModId in $Path" }
        if ($metadata -match '\$\{[^}]+\}') { throw "Unexpanded metadata in $Path" }
        if ($RequiredClass -and -not $archive.GetEntry($RequiredClass)) { throw "Missing compiled $RequiredClass in $Path" }
    } finally { $archive.Dispose() }
    return $file
}

function Quote-PrismCommandArgument {
    param([string] $Value)
    if ($Value.Contains('"') -or $Value.Contains("`r") -or $Value.Contains("`n")) {
        throw 'Launcher paths must not contain quotes or newlines.'
    }
    return '"' + $Value + '"'
}

$mod = Assert-PrismInputJar $ModJarPath 'yigd' 'com/b1n_ry/yigd/Yigd.class'
$cloth = Assert-PrismInputJar $ClothConfigJarPath 'cloth_config' ''
if ($mod.Name -eq $cloth.Name) { throw 'The YIGD and Cloth jar filenames must differ.' }
$inputs = @(
    @{ name = $mod.Name; sha256 = (Get-FileHash -LiteralPath $mod.FullName -Algorithm SHA256).Hash },
    @{ name = $cloth.Name; sha256 = (Get-FileHash -LiteralPath $cloth.FullName -Algorithm SHA256).Hash }
)

$installTarget = $null
$prismDataDirectory = $null
if ($InstancesDirectory) {
    $InstancesDirectory = (Resolve-Path -LiteralPath $InstancesDirectory).Path
    if (-not (Test-Path -LiteralPath $InstancesDirectory -PathType Container)) { throw 'InstancesDirectory must be an existing folder.' }
    $installTarget = [System.IO.Path]::GetFullPath((Join-Path $InstancesDirectory $InstanceId))
    if (-not $installTarget.StartsWith($InstancesDirectory.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Resolved instance target is outside InstancesDirectory.'
    }
    if (Test-Path -LiteralPath $installTarget) { throw "Instance already exists; refusing overwrite: $installTarget" }
    if ((Split-Path -Leaf $InstancesDirectory) -eq 'instances') { $prismDataDirectory = Split-Path -Parent $InstancesDirectory }
}
if (-not $PrismLauncherPath) {
    $launcherCandidates = @()
    if ($env:LOCALAPPDATA) { $launcherCandidates += Join-Path $env:LOCALAPPDATA 'Programs/PrismLauncher/prismlauncher.exe' }
    if ($env:ProgramFiles) { $launcherCandidates += Join-Path $env:ProgramFiles 'PrismLauncher/prismlauncher.exe' }
    $command = Get-Command 'prismlauncher' -ErrorAction SilentlyContinue
    if ($command) { $launcherCandidates += $command.Source }
    $PrismLauncherPath = $launcherCandidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
}
if ($PrismLauncherPath) { $PrismLauncherPath = (Resolve-Path -LiteralPath $PrismLauncherPath).Path }
if ($ShortcutPath) {
    if (-not $installTarget -or -not $prismDataDirectory -or -not $PrismLauncherPath) {
        throw 'ShortcutPath requires an installed launcher and an explicit InstancesDirectory named instances.'
    }
    $ShortcutPath = [System.IO.Path]::GetFullPath($ShortcutPath)
    if ([System.IO.Path]::GetExtension($ShortcutPath) -ne '.lnk') { throw 'ShortcutPath must end in .lnk.' }
    if (Test-Path -LiteralPath $ShortcutPath) { throw "Shortcut already exists; refusing overwrite: $ShortcutPath" }
    if (-not (Test-Path -LiteralPath (Split-Path -Parent $ShortcutPath) -PathType Container)) { throw 'The shortcut parent directory must already exist.' }
    if ($env:OS -ne 'Windows_NT') { throw 'ShortcutPath requires Windows.' }
}
if (-not $OutputDirectory) {
    $repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $OutputDirectory = Join-Path $repositoryRoot '.local/prism'
}
$OutputDirectory = [System.IO.Path]::GetFullPath($OutputDirectory)
$packageRoot = Join-Path $OutputDirectory "$InstanceId-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff')-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$staging = Join-Path $packageRoot $InstanceId
$modsDirectory = Join-Path $staging 'minecraft/mods'
New-Item -ItemType Directory -Path $modsDirectory -Force | Out-Null
foreach ($file in @($mod, $cloth)) { [System.IO.File]::Copy($file.FullName, (Join-Path $modsDirectory $file.Name), $false) }

$pack = [ordered]@{
    formatVersion = 1
    components = @(
        [ordered]@{
            uid = 'org.lwjgl3'; version = '3.3.1'; dependencyOnly = $true
            cachedName = 'LWJGL 3'; cachedVersion = '3.3.1'
        },
        [ordered]@{
            uid = 'net.minecraft'; version = '1.20.1'; important = $true
            cachedName = 'Minecraft'; cachedVersion = '1.20.1'
            cachedRequires = @([ordered]@{ uid = 'org.lwjgl3'; suggests = '3.3.1' })
        },
        [ordered]@{
            uid = 'net.minecraftforge'; version = '47.3.5'
            cachedName = 'Forge'; cachedVersion = '47.3.5'
            cachedRequires = @([ordered]@{ uid = 'net.minecraft'; equals = '1.20.1' })
        }
    )
}
$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $staging 'mmc-pack.json'), ($pack | ConvertTo-Json -Depth 5), $utf8)
$settings = @(
    '[General]',
    'InstanceType=OneSix',
    "name=$Name",
    'iconKey=default',
    'OverrideJavaLocation=true',
    'AutomaticJava=false',
    ('JavaPath=' + $jdk.Java.Replace('\', '/')),
    'OverrideMemory=true',
    'MinMemAlloc=512',
    "MaxMemAlloc=$MaxMemAlloc",
    'OverrideConsole=true',
    'ShowConsole=true',
    'ShowConsoleOnError=true',
    'AutoCloseConsole=false',
    'OverrideCommands=true',
    'PreLoadCommand=',
    'PreLaunchCommand=',
    'WrapperCommand=',
    'PostExitCommand='
)
[System.IO.File]::WriteAllText((Join-Path $staging 'instance.cfg'), (($settings -join [Environment]::NewLine) + [Environment]::NewLine), $utf8)
[System.IO.File]::WriteAllText((Join-Path $packageRoot 'input-hashes.json'), ($inputs | ConvertTo-Json -Depth 3), $utf8)

# Validate the complete staging directory before any launcher-profile writes.
$parsedPack = Get-Content -LiteralPath (Join-Path $staging 'mmc-pack.json') -Raw | ConvertFrom-Json
if ($parsedPack.formatVersion -ne 1 -or $parsedPack.components.Count -ne 3 -or
    $parsedPack.components[0].uid -ne 'org.lwjgl3' -or $parsedPack.components[0].version -ne '3.3.1' -or
    $parsedPack.components[1].uid -ne 'net.minecraft' -or $parsedPack.components[1].version -ne '1.20.1' -or
    $parsedPack.components[2].uid -ne 'net.minecraftforge' -or $parsedPack.components[2].version -ne '47.3.5') {
    throw 'Generated Prism component configuration failed validation.'
}
foreach ($inputJar in $inputs) {
    $copy = Join-Path $modsDirectory $inputJar.name
    if ((Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash -ne $inputJar.sha256) { throw "Copied jar hash mismatch: $copy" }
}
if (@(Get-ChildItem -LiteralPath $modsDirectory -File).Count -ne 2) { throw 'Expected exactly YIGD and Cloth Config in the prepared instance.' }
$cfg = Get-Content -LiteralPath (Join-Path $staging 'instance.cfg') -Raw
if ($cfg -notmatch '(?m)^InstanceType=OneSix\s*$' -or $cfg -notmatch "(?m)^MaxMemAlloc=$MaxMemAlloc\s*$" -or $cfg -notmatch '(?m)^OverrideJavaLocation=true\s*$') {
    throw 'Generated Prism Java/memory settings failed validation.'
}
$zipPath = Join-Path $packageRoot "$InstanceId.zip"
$zipStream = [System.IO.File]::Open($zipPath, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
$zipArchive = $null
try {
    $zipArchive = [System.IO.Compression.ZipArchive]::new($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)
    foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -File) {
        # .NET Framework CreateFromDirectory can retain Windows separators.
        # Prism's importer expects portable ZIP names with forward slashes.
        $entryName = $file.FullName.Substring($staging.Length + 1).Replace('\', '/')
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zipArchive, $file.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    if ($zipArchive) { $zipArchive.Dispose() }
    $zipStream.Dispose()
}
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    foreach ($required in @('instance.cfg', 'mmc-pack.json', "minecraft/mods/$($mod.Name)", "minecraft/mods/$($cloth.Name)")) {
        if (-not $archive.GetEntry($required)) { throw "Import zip is incomplete: $required" }
    }
} finally { $archive.Dispose() }

if ($installTarget) {
    # New-Item without -Force rejects a target created concurrently. File.Copy
    # likewise refuses overwrite; source staging is retained for review/import.
    New-Item -ItemType Directory -Path $installTarget | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -File) {
        $relative = $file.FullName.Substring($staging.Length + 1)
        $target = Join-Path $installTarget $relative
        $parent = Split-Path -Parent $target
        if (-not (Test-Path -LiteralPath $parent -PathType Container)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        [System.IO.File]::Copy($file.FullName, $target, $false)
    }
}

$launchArguments = '--launch ' + (Quote-PrismCommandArgument $InstanceId) + ' --offline ' + $OfflinePlayerName
if ($prismDataDirectory) { $launchArguments = '--dir ' + (Quote-PrismCommandArgument $prismDataDirectory) + ' ' + $launchArguments }
$launchCommand = $null
if ($PrismLauncherPath) {
    $launchCommand = '& ' + ("'" + $PrismLauncherPath.Replace("'", "''") + "'") + ' ' + $launchArguments
}
if ($ShortcutPath) {
    $shell = New-Object -ComObject WScript.Shell
    $stagedShortcut = Join-Path $packageRoot 'prepared-launch-shortcut.lnk'
    $shortcut = $shell.CreateShortcut($stagedShortcut)
    $shortcut.TargetPath = $PrismLauncherPath
    $shortcut.Arguments = $launchArguments
    $shortcut.WorkingDirectory = $prismDataDirectory
    $shortcut.Description = "$Name; Minecraft 1.20.1 Forge 47.3.5; offline test"
    $shortcut.IconLocation = "$PrismLauncherPath,0"
    $shortcut.Save()
    [System.IO.File]::Move($stagedShortcut, $ShortcutPath)
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($shortcut)
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($shell)
}

Write-Host "Prepared Prism instance and import zip; Minecraft was not launched. Future heap: $MaxMemAlloc MiB."
if ($launchCommand) { Write-Host "After installation/import, launch when ready: $launchCommand" }
[pscustomobject]@{
    InstanceId = $InstanceId
    PreparedInstancePath = $staging
    ImportZipPath = $zipPath
    InstalledInstancePath = $installTarget
    ShortcutPath = $ShortcutPath
    OfflineLaunchCommand = $launchCommand
    MaxMemAllocMiB = $MaxMemAlloc
}
