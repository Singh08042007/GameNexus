$ErrorActionPreference = "Stop"
& "$PSScriptRoot\build.ps1"
Write-Host "[NEXUS] Running Automated Test Suite..."
java -cp "build/classes;build/test-classes" com.nexus.gaming.NexusTestSuite
