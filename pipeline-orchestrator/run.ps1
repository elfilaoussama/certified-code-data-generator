# run.ps1
$ErrorActionPreference = 'Stop'

# Ensure Unicode output renders correctly (box-drawing glyphs, arrows, checkmarks).
try {
    chcp 65001 | Out-Null
} catch {
}
try {
    [Console]::InputEncoding = [System.Text.Encoding]::UTF8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

# Ensure the JVM used by Maven/exec:java runs in UTF-8.
# Use MAVEN_OPTS (instead of JAVA_TOOL_OPTIONS) to avoid the noisy "Picked up JAVA_TOOL_OPTIONS" banner.
if (-not $env:MAVEN_OPTS) {
    $env:MAVEN_OPTS = ''
}
if ($env:MAVEN_OPTS -notmatch '(^|\s)-Dfile\.encoding=UTF-8(\s|$)') {
    $env:MAVEN_OPTS = ("-Dfile.encoding=UTF-8 " + $env:MAVEN_OPTS).Trim()
}

if (Test-Path 'C:\Program Files\Java\jdk-17') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir
try {
    if ($args.Count -eq 0) {
        mvn -q exec:java "-Dexec.args=--help"
        exit 1
    }

    # Build a single exec.args string that survives PowerShell -> mvn.cmd parsing.
    # Use single-quotes inside the property to avoid embedded double-quotes.
    $execArgs = ($args | ForEach-Object { "'" + ($_ -replace "'", "''") + "'" }) -join ' '
    mvn -q exec:java "-Dexec.args=$execArgs"
} finally {
    Pop-Location
}

