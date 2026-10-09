[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$CandidateJar,
    [Parameter(Mandatory=$true)][string]$ClothJar,
    [Parameter(Mandatory=$true)][string]$FixtureJar,
    [Parameter(Mandatory=$true)][string]$SmokeRoot,
    [ValidateSet('write','read')][string]$Phase = 'write',
    [string]$JavaPath,
    [string]$OutputDirectory,
    [string]$InstancesDirectory,
    [string]$InstanceId = ('YIGD-26_1_2-native-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Phase),
    [ValidateRange(1024,4096)][int]$MaximumMemory = 2048,
    [ValidateRange(1024,65535)][int]$ServerPort = 25578
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-VerifiedJar([string]$Path, [string]$RequiredEntry) {
    $resolved = (Resolve-Path -LiteralPath $Path).ProviderPath
    if (-not [IO.File]::Exists($resolved)) { throw "Not a file: $resolved" }
    $zip = [IO.Compression.ZipFile]::OpenRead($resolved)
    try {
        if ($null -eq $zip.GetEntry($RequiredEntry)) { throw "Expected entry missing in $resolved : $RequiredEntry" }
    } finally { $zip.Dispose() }
    return $resolved
}
function Write-Utf8([string]$Path,[string]$Content) {
    [IO.File]::WriteAllText($Path,$Content,(New-Object Text.UTF8Encoding($false)))
}
function Confirm-Java25([string]$Path) {
    if (-not [IO.File]::Exists($Path)) { return $false }
    $releaseFile = Join-Path ([IO.Directory]::GetParent([IO.Path]::GetDirectoryName($Path)).FullName) 'release'
    if (-not [IO.File]::Exists($releaseFile)) { return $false }
    return ([IO.File]::ReadAllText($releaseFile) -match '(?m)^JAVA_VERSION="25(?:\.|\+|")')
}

if ($InstanceId -notmatch '^[A-Za-z0-9_-]+$') { throw 'InstanceId must contain only letters, numbers, underscore or hyphen.' }
$candidate = Get-VerifiedJar $CandidateJar 'com/b1n_ry/yigd/Yigd.class'
$cloth = Get-VerifiedJar $ClothJar 'me/shedaniel/autoconfig/AutoConfig.class'
$fixture = Get-VerifiedJar $FixtureJar 'com/b1n_ry/yigd/gametest/YigdClientSmoke.class'
$jars = @($candidate,$cloth,$fixture)
if (($jars | ForEach-Object {[IO.Path]::GetFileName($_)} | Select-Object -Unique).Count -ne 3) { throw 'All three jar filenames must be distinct.' }

if ($JavaPath) {
    $JavaPath = (Resolve-Path -LiteralPath $JavaPath).ProviderPath
    if (-not (Confirm-Java25 $JavaPath)) { throw 'The supplied JavaPath must be Java 25.' }
} else {
    $javaCandidates = @()
    if ($env:JAVA_HOME) { $javaCandidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
    $jdkDirectory = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkDirectory) {
        $javaCandidates += Get-ChildItem -LiteralPath $jdkDirectory -Directory | Where-Object {$_.Name -like '*25*'} |
            Sort-Object Name -Descending | ForEach-Object {Join-Path $_.FullName 'bin\java.exe'}
    }
    foreach ($candidateJava in $javaCandidates) {
        if (Confirm-Java25 $candidateJava) { $JavaPath = [IO.Path]::GetFullPath($candidateJava); break }
    }
    if (-not $JavaPath) { throw 'Java 25 not found; supply -JavaPath explicitly.' }
}

$smokeDirectory = [IO.Path]::GetFullPath($SmokeRoot)
if ($smokeDirectory -match '["\r\n]') { throw 'SmokeRoot contains unsupported command argument characters.' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $smokeDirectory ('prepared-' + $Phase) }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $output.StartsWith($smokeDirectory.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputDirectory must be inside SmokeRoot so the fixture can validate its isolated directory.'
}
$staging = Join-Path $output $InstanceId
$archive = Join-Path $output ($InstanceId + '.zip')
if ([IO.Directory]::Exists($staging) -or [IO.File]::Exists($archive)) { throw 'Prepared instance or archive already exists; no existing output is overwritten.' }
$installTarget = $null
if ($InstancesDirectory) {
    $instances = (Resolve-Path -LiteralPath $InstancesDirectory).ProviderPath
    if (-not [IO.Directory]::Exists($instances)) { throw 'InstancesDirectory is not a directory.' }
    $installTarget = [IO.Path]::GetFullPath((Join-Path $instances $InstanceId))
    if (-not $installTarget.StartsWith($instances.TrimEnd('\') + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Install target escapes InstancesDirectory.' }
    if (Test-Path -LiteralPath $installTarget) { throw 'The dedicated target instance already exists; no profile is overwritten.' }
}
if ($smokeDirectory -match '\s' -or $staging -match '\s' -or $installTarget -match '\s') {
    throw 'SmokeRoot and dedicated Prism paths must avoid whitespace for unquoted JVM property arguments.'
}

[IO.Directory]::CreateDirectory($staging) | Out-Null
$minecraftDirectory = Join-Path $staging '.minecraft'
[IO.Directory]::CreateDirectory((Join-Path $minecraftDirectory 'mods')) | Out-Null
foreach ($jar in $jars) { [IO.File]::Copy($jar,(Join-Path (Join-Path $minecraftDirectory 'mods') ([IO.Path]::GetFileName($jar))),$false) }
Write-Utf8 (Join-Path $minecraftDirectory '.yigd-isolated-runtime') "YIGD_ISOLATED_RUNTIME_26_1_2`n"
Write-Utf8 (Join-Path $minecraftDirectory 'options.txt') "onboardAccessibility:false`npauseOnLostFocus:false`ntutorialStep:none`njoinedFirstServer:true`n"
$pack = [ordered]@{
    formatVersion=1
    components=@(
        [ordered]@{uid='org.lwjgl3';version='3.4.1';cachedName='LWJGL 3';cachedVersion='3.4.1';dependencyOnly=$true;cachedVolatile=$true},
        [ordered]@{uid='net.minecraft';version='26.1.2';cachedName='Minecraft';cachedVersion='26.1.2';important=$true;cachedRequires=@(@{uid='org.lwjgl3';suggests='3.4.1'})},
        [ordered]@{uid='net.neoforged';version='26.1.2.114';cachedName='NeoForge';cachedVersion='26.1.2.114';cachedRequires=@(@{uid='net.minecraft';equals='26.1.2'})}
    )
}
Write-Utf8 (Join-Path $staging 'mmc-pack.json') ($pack | ConvertTo-Json -Depth 8)
$runtimeMinecraftDirectory = $minecraftDirectory
if ($installTarget) { $runtimeMinecraftDirectory = Join-Path $installTarget '.minecraft' }
$runtimeArguments = '-Dyigd.smokeClient=true -Dyigd.smokeRoot=' + $smokeDirectory.Replace('\','/') + ' -Dyigd.smokeClientDirectory=' + $runtimeMinecraftDirectory.Replace('\','/') + ' -Dyigd.smokePhase=' + $Phase + ' -Dyigd.smokePort=' + $ServerPort
$cfg = @"
[General]
InstanceType=OneSix
name=YiGD 26.1.2 isolated $Phase verification
iconKey=default
uuid=$([Guid]::NewGuid().ToString('N'))
ConfigVersion=1.3
OverrideMemory=true
MinMemAlloc=256
MaxMemAlloc=$MaximumMemory
OverrideWindow=true
LaunchMaximized=false
MinecraftWinWidth=1280
MinecraftWinHeight=720
OverrideJavaLocation=true
AutomaticJava=false
JavaPath=$($JavaPath.Replace('\','/'))
OverrideJavaArgs=true
JvmArgs="$runtimeArguments"
OverrideConsole=true
ShowConsole=false
ShowConsoleOnError=true
AutoCloseConsole=false
"@
Write-Utf8 (Join-Path $staging 'instance.cfg') $cfg

$stream = [IO.File]::Open($archive,[IO.FileMode]::CreateNew)
$zip = New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create,$false)
try {
    foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -File) {
        $entryName = $file.FullName.Substring($staging.Length + 1).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entryName,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose(); $stream.Dispose() }
$verify = [IO.Compression.ZipFile]::OpenRead($archive)
try {
    if ($verify.Entries | Where-Object {$_.FullName.Contains('\')}) { throw 'Archive contains invalid backslash entry paths.' }
    foreach ($entry in @('mmc-pack.json','instance.cfg','.minecraft/.yigd-isolated-runtime')) {
        if ($null -eq $verify.GetEntry($entry)) { throw "Prepared archive missing $entry" }
    }
    if (($verify.Entries | Where-Object {$_.FullName -like '.minecraft/mods/*.jar'}).Count -ne 3) { throw 'Prepared archive does not contain exactly candidate, Cloth and fixture jars.' }
} finally { $verify.Dispose() }

if ($installTarget) {
    if (Test-Path -LiteralPath $installTarget) { throw 'Dedicated profile appeared during preparation; no profile writes performed.' }
    New-Item -ItemType Directory -Path $installTarget | Out-Null
    foreach ($directory in Get-ChildItem -LiteralPath $staging -Recurse -Directory) {
        [IO.Directory]::CreateDirectory((Join-Path $installTarget $directory.FullName.Substring($staging.Length + 1))) | Out-Null
    }
    foreach ($file in Get-ChildItem -LiteralPath $staging -Recurse -File) {
        [IO.File]::Copy($file.FullName,(Join-Path $installTarget $file.FullName.Substring($staging.Length + 1)),$false)
    }
}

[ordered]@{
    InstanceId=$InstanceId;StagingDirectory=$staging;Archive=$archive;InstalledDirectory=$installTarget
    MinecraftDirectory=$runtimeMinecraftDirectory;SmokeRoot=$smokeDirectory;Phase=$Phase;JavaPath=$JavaPath
    Jars=@($jars | ForEach-Object {[ordered]@{Name=[IO.Path]::GetFileName($_);Sha256=(Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()}})
} | ConvertTo-Json -Depth 5
