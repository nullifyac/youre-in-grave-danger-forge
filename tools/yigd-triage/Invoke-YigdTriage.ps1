<#
.SYNOPSIS
Runs Minecraft 1.20.1 Forge common mod initialization with a 1 GiB heap cap.
.DESCRIPTION
Requires an existing Forge server install, a YIGD jar and a Cloth Config jar.
Creates a fresh local diagnostic directory and never calls server Main, checks or
changes the EULA, generates a world, or opens a server port. Baseline and candidate
jars can be tested in separate invocations. Exit 0 means common mod loading passed;
exit 1 means it failed; timeout throws. This does not test client rendering or gameplay.
.EXAMPLE
./Invoke-YigdTriage.ps1 -ForgeRuntimePath C:/forge-runtime -ModJarPath C:/mods/yigd.jar -ClothConfigJarPath C:/mods/cloth.jar -Label baseline
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $ForgeRuntimePath,
    [Parameter(Mandatory)] [string] $ModJarPath,
    [Parameter(Mandatory)] [string] $ClothConfigJarPath,
    [string[]] $AdditionalModJarPath = @(),
    [string] $JavaHome,
    [ValidatePattern('^47\.\d+\.\d+$')] [string] $ForgeVersion = '47.3.5',
    [ValidatePattern('^[A-Za-z0-9_-]+$')] [string] $Label = 'candidate',
    [ValidateRange(10, 600)] [int] $TimeoutSeconds = 120,
    [switch] $PrepareOnly
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Common.ps1')
$jdk = Resolve-TriageJdk17 -JavaHome $JavaHome
$ForgeRuntimePath = (Resolve-Path -LiteralPath $ForgeRuntimePath).Path
$originalArguments = Join-Path $ForgeRuntimePath "libraries/net/minecraftforge/forge/1.20.1-$ForgeVersion/win_args.txt"
if (-not (Test-Path -LiteralPath $originalArguments -PathType Leaf)) {
    throw "Forge 1.20.1 $ForgeVersion argument file is missing: $originalArguments"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDirectory = Join-Path $PSScriptRoot ".local/$Label-$stamp-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$harnessDirectory = Join-Path $runDirectory 'harness'
$classesDirectory = Join-Path $harnessDirectory 'classes'
$servicesDirectory = Join-Path $classesDirectory 'META-INF/services'
$modsDirectory = Join-Path $runDirectory 'mods'
New-Item -ItemType Directory -Path $servicesDirectory, $modsDirectory -Force | Out-Null

$inputJars = @($ModJarPath, $ClothConfigJarPath) + $AdditionalModJarPath
$inputDescriptions = @()
foreach ($jarPath in $inputJars) {
    $jarFile = Get-Item -LiteralPath $jarPath
    if ($jarFile.PSIsContainer -or $jarFile.Extension -ne '.jar') {
        throw "Expected a jar file: $jarPath"
    }
    $destination = Join-Path $modsDirectory $jarFile.Name
    if (Test-Path -LiteralPath $destination) { throw "Duplicate jar filename: $($jarFile.Name)" }
    Copy-Item -LiteralPath $jarFile.FullName -Destination $destination
    $inputDescriptions += @{ name = $jarFile.Name; sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash }
}

$fmlLoader = Find-TriageLibrary $ForgeRuntimePath 'net/minecraftforge/fmlloader' "fmlloader-1.20.1-$ForgeVersion.jar"
$modLauncher = Find-TriageLibrary $ForgeRuntimePath 'cpw/mods/modlauncher' 'modlauncher-*.jar'
$source = Join-Path $PSScriptRoot 'src/triage/YigdTriageLaunchHandler.java'
& $jdk.Javac '--release' '17' '-cp' "$fmlLoader;$modLauncher" '-d' $classesDirectory $source
if ($LASTEXITCODE -ne 0) { throw 'Diagnostic harness compilation failed.' }
'triage.YigdTriageLaunchHandler' | Set-Content -LiteralPath (Join-Path $servicesDirectory 'cpw.mods.modlauncher.api.ILaunchHandlerService') -Encoding Ascii
$harnessJar = Join-Path $harnessDirectory 'yigd-triage-harness.jar'
& $jdk.Jar '--create' '--file' $harnessJar '-C' $classesDirectory '.'
if ($LASTEXITCODE -ne 0) { throw 'Diagnostic harness packaging failed.' }

$absoluteLibraries = (Join-Path $ForgeRuntimePath 'libraries').Replace('\', '/')
$hasClassPath = $false
$hasTarget = $false
$arguments = foreach ($line in Get-Content -LiteralPath $originalArguments) {
    if ($line -match '^\s*(#|$)') { $line; continue }
    $line = $line.Replace('libraries/', "$absoluteLibraries/").Replace('libraries\', "$absoluteLibraries/")
    if ($line -match '^-p\s+(.+)$') {
        '-p'
        ConvertTo-TriageJavaArg $Matches[1]
    } elseif ($line -match '^-DlibraryDirectory=') {
        ConvertTo-TriageJavaArg "-DlibraryDirectory=$absoluteLibraries"
    } elseif ($line -match '^-DlegacyClassPath=') {
        $hasClassPath = $true
        ConvertTo-TriageJavaArg "$line;$harnessJar"
    } elseif ($line -match '^--launchTarget\s+forgeserver\s*$') {
        $hasTarget = $true
        '--launchTarget yigdtriageserver'
    } else {
        $line
    }
}
if (-not $hasClassPath -or -not $hasTarget) {
    throw 'Unexpected Forge argument format; refusing to launch a standard hosted server.'
}
$argumentFile = Join-Path $runDirectory 'triage_args.txt'
$arguments | Set-Content -LiteralPath $argumentFile -Encoding Ascii
$stdout = Join-Path $runDirectory 'stdout.log'
$stderr = Join-Path $runDirectory 'stderr.log'
$metadata = @{ label = $Label; forge = $ForgeVersion; minecraft = '1.20.1'; java = $jdk.Java; inputs = $inputDescriptions; heapMiB = 1024 }
$metadata | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $runDirectory 'inputs.json') -Encoding UTF8

Write-Host "Common mod startup diagnostic: $runDirectory"
if ($PrepareOnly) {
    Write-Host "Prepared harness and isolated arguments. Minecraft was not launched: $argumentFile"
    exit 0
}
$processArguments = @('-Xms128m', '-Xmx1024m', ('"@' + $argumentFile + '"'), '--nogui')
$startParameters = @{
    FilePath = $jdk.Java; ArgumentList = $processArguments; WorkingDirectory = $runDirectory
    RedirectStandardOutput = $stdout; RedirectStandardError = $stderr; PassThru = $true
}
if ($env:OS -eq 'Windows_NT') { $startParameters.WindowStyle = 'Hidden' }
$process = Start-Process @startParameters
$null = $process.Handle
if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    $process.Kill()
    $process.WaitForExit()
    throw "Diagnostic exceeded $TimeoutSeconds seconds. Process stopped; logs: $runDirectory"
}
$process.WaitForExit()
$process.Refresh()
$resultCode = $process.ExitCode
Get-Content -LiteralPath $stdout | Write-Output
Get-Content -LiteralPath $stderr | Write-Output
if (Test-Path -LiteralPath (Join-Path $runDirectory 'eula.txt')) {
    throw "Unexpected EULA file created; inspect diagnostic output: $runDirectory"
}
if (Test-Path -LiteralPath (Join-Path $runDirectory 'world')) {
    throw "Unexpected world directory created; inspect diagnostic output: $runDirectory"
}
if ($resultCode -eq 0 -and -not (Select-String -LiteralPath $stdout -SimpleMatch 'YIGD_TRIAGE: ServerModLoader.load completed' -Quiet)) {
    throw "Java exited successfully without completing common mod initialization: $runDirectory"
}
Write-Host "Diagnostic exit code: $resultCode. Logs and any FML crash report: $runDirectory"
exit $resultCode
