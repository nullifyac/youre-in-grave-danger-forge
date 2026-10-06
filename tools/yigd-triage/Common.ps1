Set-StrictMode -Version Latest

function Resolve-TriageJdk17 {
    param([string] $JavaHome)

    $executableSuffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
    $candidates = [System.Collections.Generic.List[string]]::new()
    if ($JavaHome) {
        $candidates.Add((Join-Path $JavaHome "bin/java$executableSuffix"))
    } else {
        if ($env:JAVA_HOME) {
            $candidates.Add((Join-Path $env:JAVA_HOME "bin/java$executableSuffix"))
        }
        $command = Get-Command "java$executableSuffix" -ErrorAction SilentlyContinue
        if ($command) { $candidates.Add($command.Source) }
        if ($env:ProgramFiles) {
            foreach ($vendor in @('Eclipse Adoptium', 'Java', 'Microsoft')) {
                $vendorRoot = Join-Path $env:ProgramFiles $vendor
                if (Test-Path -LiteralPath $vendorRoot -PathType Container) {
                    foreach ($directory in Get-ChildItem -LiteralPath $vendorRoot -Directory) {
                        $candidates.Add((Join-Path $directory.FullName "bin/java$executableSuffix"))
                    }
                }
            }
        }
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $previousPreference = $ErrorActionPreference
        try {
            # Java writes its version to stderr even on a successful invocation.
            $ErrorActionPreference = 'Continue'
            $version = (& $candidate '-version' 2>&1 | Out-String)
            $versionExit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($versionExit -ne 0 -or $version -notmatch 'version "17(?:[.\-+"])') { continue }
        $binDirectory = Split-Path -Parent $candidate
        $javac = Join-Path $binDirectory "javac$executableSuffix"
        $jar = Join-Path $binDirectory "jar$executableSuffix"
        if ((Test-Path -LiteralPath $javac -PathType Leaf) -and
            (Test-Path -LiteralPath $jar -PathType Leaf)) {
            return @{ Java = (Resolve-Path -LiteralPath $candidate).Path; Javac = $javac; Jar = $jar }
        }
    }
    throw 'A Java 17 JDK is required. Supply -JavaHome with the JDK directory.'
}

function Find-TriageLibrary {
    param([string] $ForgeRuntimePath, [string] $RelativeDirectory, [string] $Pattern)

    $directory = Join-Path (Join-Path $ForgeRuntimePath 'libraries') $RelativeDirectory
    $matches = @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter $Pattern |
        Where-Object Name -NotMatch '-(sources|javadoc)\.jar$')
    if ($matches.Count -ne 1) {
        throw "Expected one $Pattern under $directory; found $($matches.Count). Use an isolated Forge install."
    }
    return $matches[0].FullName
}

function ConvertTo-TriageJavaArg {
    param([string] $Value)

    # Java argument files understand forward slashes on Windows, including paths with spaces.
    if ($Value.Contains('"') -or $Value.Contains("`n") -or $Value.Contains("`r")) {
        throw 'Java argument values must not contain quotes or newlines.'
    }
    return '"' + $Value.Replace('\', '/') + '"'
}
