<#
.SYNOPSIS
Checks a packaged Minecraft 1.20.1 YIGD jar without launching Minecraft.
.DESCRIPTION
Validates Accessories metadata with Forge's Maven version comparator, checks the
1.20.1 block/item tag paths and grave protection entries, and parses all tag JSON.
Requires an existing Forge install only for its Maven artifact library. Generated
helper classes stay under the ignored .local directory.
.EXAMPLE
./Test-YigdArtifact.ps1 -JarPath ../../Youre-in-grave-danger-1.20.1-forge/build/libs/youre-in-grave-danger-forge-1.20.1-2.0.20.jar -ForgeRuntimePath C:/forge-runtime
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $JarPath,
    [Parameter(Mandatory)] [string] $ForgeRuntimePath,
    [string] $JavaHome,
    [switch] $ResourcesOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$jdk = Resolve-TriageJdk17 -JavaHome $JavaHome
$JarPath = (Resolve-Path -LiteralPath $JarPath).Path
$ForgeRuntimePath = (Resolve-Path -LiteralPath $ForgeRuntimePath).Path
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
$issues = [System.Collections.Generic.List[string]]::new()
$accessoriesRange = $null
$clothConfigFound = $false

function Read-TriageZipText {
    param([System.IO.Compression.ZipArchiveEntry] $Entry)
    $reader = [System.IO.StreamReader]::new($Entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}

try {
    if (-not $ResourcesOnly -and -not $archive.GetEntry('com/b1n_ry/yigd/Yigd.class')) {
        $issues.Add('Missing compiled Yigd.class; use -ResourcesOnly for a deliberately resource-only source fixture.')
    }
    if (-not $ResourcesOnly) {
        $refmapEntry = $archive.GetEntry('yigd.mixins.refmap.json')
        if (-not $refmapEntry) {
            $issues.Add('Missing Mixin refmap; a cached compiler output can omit annotation processor files.')
        } else {
            try {
                $refmap = Read-TriageZipText $refmapEntry | ConvertFrom-Json
                if (-not $refmap.mappings -or @($refmap.mappings.PSObject.Properties).Count -eq 0) {
                    $issues.Add('Packaged Mixin refmap contains no mappings.')
                }
            } catch { $issues.Add('Packaged Mixin refmap is invalid JSON.') }
        }
    }
    $metadataEntry = $archive.GetEntry('META-INF/mods.toml')
    if (-not $metadataEntry) {
        $issues.Add('Missing META-INF/mods.toml')
    } else {
        $metadata = Read-TriageZipText $metadataEntry
        if ($metadata -match '\$\{[^}]+\}') { $issues.Add('Packaged metadata contains unresolved Gradle placeholders.') }
        $modBlock = [regex]::Match($metadata, '(?ms)^\s*\[\[mods\]\]\s*\r?\n(.*?)(?=^\s*\[|\z)').Groups[1].Value
        if ($modBlock -notmatch '(?m)^\s*modId\s*=\s*"yigd"\s*$') { $issues.Add('Packaged modId must be yigd.') }
        if ($modBlock -notmatch '(?m)^\s*version\s*=\s*"\d[^"\r\n]*"\s*$') { $issues.Add('Packaged mod version must be an expanded version value.') }
        foreach ($dependency in [regex]::Matches($metadata, '(?ms)^\s*\[\[dependencies\.yigd\]\]\s*\r?\n(.*?)(?=^\s*\[|\z)')) {
            $body = $dependency.Groups[1].Value
            if ($body -match '(?m)^\s*modId\s*=\s*"cloth_config"\s*$') {
                $clothConfigFound = $true
                if ($body -notmatch '(?m)^\s*mandatory\s*=\s*true\s*$') { $issues.Add('Cloth Config must be a required dependency.') }
            }
            if ($body -match '(?m)^\s*modId\s*=\s*"accessories"\s*$') {
                if ($body -match '(?m)^\s*versionRange\s*=\s*"([^"]+)"') { $accessoriesRange = $Matches[1] }
                if ($body -notmatch '(?m)^\s*mandatory\s*=\s*false\s*$') { $issues.Add('Accessories must remain an optional dependency.') }
            }
        }
        if (-not $clothConfigFound) { $issues.Add('Missing required Cloth Config dependency in packaged mods.toml') }
        if (-not $accessoriesRange) { $issues.Add('Missing Accessories versionRange in packaged mods.toml') }
    }

    $requiredTags = @(
        'data/minecraft/tags/blocks/dragon_immune.json',
        'data/minecraft/tags/blocks/wither_immune.json',
        'data/yigd/tags/blocks/keep_strict_blacklist.json',
        'data/yigd/tags/blocks/replace_grave_blacklist.json',
        'data/yigd/tags/blocks/replace_soft_whitelist.json',
        'data/yigd/tags/items/grave_incompatible.json',
        'data/yigd/tags/items/loss_immune.json',
        'data/yigd/tags/items/natural_soulbound.json',
        'data/yigd/tags/items/natural_vanishing.json',
        'data/yigd/tags/items/soulbindable.json'
    )
    foreach ($path in $requiredTags) {
        if (-not $archive.GetEntry($path)) { $issues.Add("Missing 1.20.1 tag: $path") }
    }
    foreach ($entry in $archive.Entries) {
        if (-not $entry.Name) { continue }
        if ($entry.FullName -match '^data/[^/]+/tags/(block|item)/') {
            $issues.Add("Invalid 1.20.1 block/item tag path: $($entry.FullName)")
        }
        if ($entry.FullName -notmatch '^data/[^/]+/tags/.+\.json$') { continue }
        try {
            $json = Read-TriageZipText $entry | ConvertFrom-Json
            if ('values' -notin $json.PSObject.Properties.Name) { $issues.Add("Tag lacks values: $($entry.FullName)") }
            if ($entry.FullName -in @('data/minecraft/tags/blocks/dragon_immune.json', 'data/minecraft/tags/blocks/wither_immune.json')) {
                if ('yigd:grave' -notin $json.values) { $issues.Add("Protection tag lacks yigd:grave: $($entry.FullName)") }
            }
        } catch {
            $issues.Add("Invalid tag JSON: $($entry.FullName): $($_.Exception.Message)")
        }
    }
} finally {
    $archive.Dispose()
}

if ($accessoriesRange) {
    $mavenJar = Find-TriageLibrary $ForgeRuntimePath 'org/apache/maven/maven-artifact' 'maven-artifact-*.jar'
    $commonsLang = Find-TriageLibrary $ForgeRuntimePath 'org/apache/commons/commons-lang3' 'commons-lang3-*.jar'
    $classesDirectory = Join-Path $PSScriptRoot '.local/artifact-check/classes'
    New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
    & $jdk.Javac '--release' '17' '-cp' $mavenJar '-d' $classesDirectory (Join-Path $PSScriptRoot 'src/triage/VersionRangeCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Maven version check helper compilation failed.' }
    $expectations = @(
        '1.0.0-beta47+1.20.1=true',
        '1.0.0-beta48+1.20.1=true',
        '1.0.0-beta.47+1.20.1=true',
        '1.0=true',
        '0.9.0=false',
        '1.0.0-beta46+1.20.1=false'
    )
    & $jdk.Java '-Xmx64m' '-cp' "$classesDirectory;$mavenJar;$commonsLang" 'triage.VersionRangeCheck' $accessoriesRange @expectations
    if ($LASTEXITCODE -ne 0) { $issues.Add('Accessories version range rejected supported releases or accepted unsupported versions.') }
}

if ($issues.Count -gt 0) {
    foreach ($issue in $issues) { Write-Host "FAIL: $issue" }
    throw "Artifact validation failed with $($issues.Count) issue(s): $JarPath"
}
$scope = if ($ResourcesOnly) { 'RESOURCE-ONLY metadata/tag validation; compiled mod validation is pending' } else { 'Packaged mod metadata/tag validation' }
Write-Host "PASS: $scope`: $JarPath"
