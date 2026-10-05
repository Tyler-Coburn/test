# Emerald setup doctor for Windows PowerShell. Usage: .\tools\doctor.ps1 [-Build]
param([switch]$Build)
Set-Location (Join-Path $PSScriptRoot "..")
$bad = 0
Write-Host "Java"
try {
  $v = (& java -version 2>&1 | Select-String 'version "(\d+)').Matches[0].Groups[1].Value
  if ($v -eq "21") { Write-Host "  OK    Java 21" -ForegroundColor Green }
  else { Write-Host "  FAIL  Java $v; install JDK 21 from https://adoptium.net/temurin/releases/?version=21" -ForegroundColor Red; $bad++ }
} catch { Write-Host "  FAIL  java not on PATH; install JDK 21" -ForegroundColor Red; $bad++ }
Write-Host "Network"
$hosts = @(
  "https://services.gradle.org/distributions/", "https://plugins.gradle.org/m2/", "https://repo.maven.apache.org/maven2/",
  "https://maven.neoforged.net/releases/", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
  "https://piston-data.mojang.com/", "https://libraries.minecraft.net/", "https://maven.parchmentmc.org/",
  "https://dl.cloudsmith.io/public/tslat/sbl/maven/")
foreach ($h in $hosts) {
  try { Invoke-WebRequest -Uri $h -Method Head -TimeoutSec 15 -UseBasicParsing | Out-Null; Write-Host "  OK    $h" -ForegroundColor Green }
  catch {
    if ($_.Exception.Response) { Write-Host "  OK    $h" -ForegroundColor Green }
    else { Write-Host "  FAIL  $h" -ForegroundColor Red; $bad++ }
  }
}
if ($Build) {
  .\gradlew.bat -Pemerald.coreOnly=true test :simulation-core:sandbox
  if ($bad -eq 0) { .\gradlew.bat build; .\gradlew.bat runGameTestServer }
}
exit $bad
