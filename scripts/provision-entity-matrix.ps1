param(
    [Parameter(Mandatory = $true)]
    [string] $Server
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'entity-matrix/common.ps1')

$repoRoot = Get-EntityMatrixRepoRoot
$serverConfig = Get-EntityMatrixServerConfig -RepoRoot $repoRoot -Server $Server

Write-Host "Provisioning entity matrix assets for $($serverConfig.id)"
$javaExecutable = Ensure-TemurinJdk -RepoRoot $repoRoot -ServerConfig $serverConfig
$serverJar = Ensure-ServerJar -RepoRoot $repoRoot -ServerConfig $serverConfig

Write-Host "Provisioned Java: $javaExecutable"
Write-Host "Provisioned server jar: $serverJar"
