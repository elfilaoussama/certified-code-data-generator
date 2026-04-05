# setup.ps1
$ErrorActionPreference = 'Stop'

if (Test-Path 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir
try {
    # Install the AlloyInEcore core artifact into the local Maven repo.
    # This mirrors setup.sh and prevents missing-dependency failures on fresh Windows setups.
    $alloyCoreDir = (Resolve-Path (Join-Path $scriptDir '..\AlloyInEcore\Source\eu.modelwriter.core.alloyinecore')).Path
    if (Test-Path $alloyCoreDir) {
        Push-Location $alloyCoreDir
        try {
            mvn -q -DskipTests install
        } finally {
            Pop-Location
        }
    } else {
        Write-Warning "AlloyInEcore core directory not found: $alloyCoreDir"
    }

    mvn -q clean compile
} finally {
    Pop-Location
}

