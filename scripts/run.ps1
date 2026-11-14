$ErrorActionPreference = "Stop"
& "$PSScriptRoot\build.ps1"
Write-Host "[NEXUS] Starting NEXUS Real-Time Multiplayer Gaming Hub Server..."
java -cp build/classes com.nexus.gaming.NexusApplication
