$ErrorActionPreference = "Stop"
Write-Host "[NEXUS] Compiling Java Backend and Test Suite..."
New-Item -ItemType Directory -Force -Path build/classes, build/test-classes | Out-Null

$mainSources = (Get-ChildItem -Recurse -Filter *.java src/main/java).FullName
javac --release 17 -Xlint:all -Werror -encoding UTF-8 -d build/classes $mainSources

$testSources = (Get-ChildItem -Recurse -Filter *.java src/test/java).FullName
javac --release 17 -Xlint:all -Werror -encoding UTF-8 -cp build/classes -d build/test-classes $testSources

Write-Host "[NEXUS] Compilation successful (0 errors, 0 warnings)!"
