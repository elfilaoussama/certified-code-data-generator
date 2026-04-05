# setup.ps1
$ErrorActionPreference = 'Stop'

if (Test-Path 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir
try {
    mvn -q clean compile
} finally {
    Pop-Location
}

