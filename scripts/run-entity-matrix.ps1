param(
    [Parameter(Mandatory = $true)]
    [string] $Server,
    [Parameter(Mandatory = $true)]
    [string] $Scenario,
    [int] $StartupTimeoutSeconds = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'entity-matrix/common.ps1')

function Wait-ForServerReady {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process] $Process,
        [Parameter(Mandatory = $true)]
        [string] $LogPath,
        [Parameter(Mandatory = $true)]
        [int] $TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "Server process exited early with code $($Process.ExitCode). See '$LogPath'."
        }

        if (Test-Path -LiteralPath $LogPath) {
            $contents = Get-Content -LiteralPath $LogPath -Raw
            if ($contents -match 'Done \(' -or $contents -match 'For help, type') {
                return
            }
        }

        Start-Sleep -Milliseconds 500
    }

    throw "Server '$Server' did not finish startup within $TimeoutSeconds seconds. See '$LogPath'."
}

function Wait-ForScenarioOutputs {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process] $Process,
        [Parameter(Mandatory = $true)]
        [string] $TracePath,
        [Parameter(Mandatory = $true)]
        [string] $AssertionsPath,
        [Parameter(Mandatory = $true)]
        [int] $TimeoutSeconds,
        [Parameter(Mandatory = $true)]
        [string] $LogPath
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Test-Path -LiteralPath $TracePath) -and (Test-Path -LiteralPath $AssertionsPath)) {
            return
        }

        if ($Process.HasExited) {
            throw "Server process exited before writing both trace and assertions files. See '$LogPath'."
        }

        Start-Sleep -Milliseconds 500
    }

    throw "Scenario '$Scenario' did not produce both '$TracePath' and '$AssertionsPath' within $TimeoutSeconds seconds."
}

function Stop-ServerProcess {
    param(
        [Parameter(Mandatory = $true)]
        [System.Diagnostics.Process] $Process
    )

    if ($Process.HasExited) {
        return
    }

    try {
        $Process.StandardInput.WriteLine('stop')
        $Process.StandardInput.Flush()
    } catch {
    }

    if (-not $Process.WaitForExit(60000)) {
        try {
            $Process.Kill()
        } catch {
        }
        throw 'Server process did not stop cleanly within 60 seconds.'
    }
}

function Assert-ScenarioOutputs {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ScenarioConfig,
        [Parameter(Mandatory = $true)]
        [string] $TracePath,
        [Parameter(Mandatory = $true)]
        [string] $AssertionsPath
    )

    if (-not (Test-Path -LiteralPath $TracePath)) {
        throw "Missing trace file '$TracePath'."
    }
    if (-not (Test-Path -LiteralPath $AssertionsPath)) {
        throw "Missing assertions file '$AssertionsPath'."
    }

    $null = Get-Content -LiteralPath $TracePath -Raw | ConvertFrom-Json -Depth 20
    $assertions = Get-Content -LiteralPath $AssertionsPath -Raw | ConvertFrom-Json -Depth 20

    foreach ($key in $ScenarioConfig.expectedAssertionKeys) {
        if (-not ($assertions.PSObject.Properties.Name -contains $key)) {
            throw "Assertions file '$AssertionsPath' is missing required key '$key'."
        }
    }

    if (-not ($assertions.PSObject.Properties.Name -contains 'pass')) {
        throw "Assertions file '$AssertionsPath' is missing the required 'pass' key."
    }

    if (-not [bool]$assertions.pass) {
        throw "Scenario '$Scenario' reported pass=false in '$AssertionsPath'."
    }

    if ($assertions.PSObject.Properties.Name -contains 'failures') {
        if ($null -ne $assertions.failures -and @($assertions.failures).Count -gt 0) {
            throw "Scenario '$Scenario' reported assertion failures in '$AssertionsPath'."
        }
    }
}

$repoRoot = Get-EntityMatrixRepoRoot
$serverConfig = Get-EntityMatrixServerConfig -RepoRoot $repoRoot -Server $Server
$scenarioConfig = Get-EntityMatrixScenarioConfig -Scenario $Scenario

if (-not (Test-Path -LiteralPath $serverConfig.javaExecutable) -or -not (Test-Path -LiteralPath $serverConfig.serverJar)) {
    & (Join-Path $PSScriptRoot 'provision-entity-matrix.ps1') -Server $Server
}

$pluginJar = Ensure-TestPluginJar -RepoRoot $repoRoot

Ensure-EntityMatrixDirectory -Path $serverConfig.serverHome
$pluginsDir = Join-Path $serverConfig.serverHome 'plugins'
Ensure-EntityMatrixDirectory -Path $pluginsDir

Get-ChildItem -LiteralPath $pluginsDir -Filter 'test-plugin*.jar' -File -ErrorAction SilentlyContinue | Remove-Item -Force

$pluginDestination = Join-Path $pluginsDir ([System.IO.Path]::GetFileName($pluginJar))
Copy-Item -LiteralPath $pluginJar -Destination $pluginDestination -Force

Set-Content -LiteralPath (Join-Path $serverConfig.serverHome 'eula.txt') -Value 'eula=true' -Encoding ASCII
Set-Content -LiteralPath (Join-Path $serverConfig.serverHome 'server.properties') -Value @(
    'motd=spigot-boot entity matrix',
    'online-mode=false',
    'allow-flight=true',
    'spawn-protection=0',
    'difficulty=normal'
) -Encoding ASCII

$outputDir = Resolve-EntityMatrixPath -RepoRoot $repoRoot -RelativePath ("target/entity-matrix/$Server/$Scenario")
if (Test-Path -LiteralPath $outputDir) {
    Remove-Item -LiteralPath $outputDir -Recurse -Force
}
Ensure-EntityMatrixDirectory -Path $outputDir

$tracePath = Join-Path $outputDir 'trace.json'
$assertionsPath = Join-Path $outputDir 'assertions.json'
$logPath = Join-Path $outputDir 'server.log'

$javaArguments = @(
    "-Dentity.matrix.autoRun=true",
    "-Dspigotboot.entityMatrix.autoRun=true",
    "-Dentity.matrix.server=$Server",
    "-Dspigotboot.entityMatrix.server=$Server",
    "-Dentity.matrix.scenario=$Scenario",
    "-Dspigotboot.entityMatrix.scenario=$Scenario",
    "-Dentity.matrix.outputDir=$outputDir",
    "-Dspigotboot.entityMatrix.outputDir=$outputDir",
    "-Dentity.matrix.traceFile=$tracePath",
    "-Dspigotboot.entityMatrix.traceFile=$tracePath",
    "-Dentity.matrix.assertionsFile=$assertionsPath",
    "-Dspigotboot.entityMatrix.assertionsFile=$assertionsPath",
    '-Xms512M',
    '-Xmx1024M',
    '-jar',
    $serverConfig.serverJar,
    'nogui'
)

$environment = @{
    'ENTITY_MATRIX_AUTO_RUN' = 'true'
    'SPIGOTBOOT_ENTITY_MATRIX_AUTO_RUN' = 'true'
    'ENTITY_MATRIX_SERVER' = $Server
    'SPIGOTBOOT_ENTITY_MATRIX_SERVER' = $Server
    'ENTITY_MATRIX_SCENARIO' = $Scenario
    'SPIGOTBOOT_ENTITY_MATRIX_SCENARIO' = $Scenario
    'ENTITY_MATRIX_OUTPUT_DIR' = $outputDir
    'SPIGOTBOOT_ENTITY_MATRIX_OUTPUT_DIR' = $outputDir
    'ENTITY_MATRIX_TRACE_FILE' = $tracePath
    'SPIGOTBOOT_ENTITY_MATRIX_TRACE_FILE' = $tracePath
    'ENTITY_MATRIX_ASSERTIONS_FILE' = $assertionsPath
    'SPIGOTBOOT_ENTITY_MATRIX_ASSERTIONS_FILE' = $assertionsPath
}

$process = New-Object System.Diagnostics.Process
$process.StartInfo = New-Object System.Diagnostics.ProcessStartInfo
$process.StartInfo.FileName = $serverConfig.javaExecutable
foreach ($argument in $javaArguments) {
    [void]$process.StartInfo.ArgumentList.Add($argument)
}
$process.StartInfo.WorkingDirectory = $serverConfig.serverHome
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.RedirectStandardError = $true
$process.StartInfo.RedirectStandardInput = $true
$process.StartInfo.CreateNoWindow = $true
foreach ($key in $environment.Keys) {
    $process.StartInfo.Environment[$key] = [string]$environment[$key]
}

$logWriter = New-Object System.IO.StreamWriter($logPath, $true)
$logWriter.AutoFlush = $true

try {
    $null = Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -Action {
        if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
            $Event.MessageData.WriteLine($EventArgs.Data)
            Write-Host $EventArgs.Data
        }
    } -MessageData $logWriter
    $null = Register-ObjectEvent -InputObject $process -EventName ErrorDataReceived -Action {
        if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
            $Event.MessageData.WriteLine($EventArgs.Data)
            Write-Host $EventArgs.Data
        }
    } -MessageData $logWriter

    if (-not $process.Start()) {
        throw "Failed to start server process with '$($serverConfig.javaExecutable)'."
    }

    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()

    Wait-ForServerReady -Process $process -LogPath $logPath -TimeoutSeconds $StartupTimeoutSeconds
    Wait-ForScenarioOutputs -Process $process -TracePath $tracePath -AssertionsPath $assertionsPath -TimeoutSeconds ([int]$scenarioConfig.timeoutSeconds) -LogPath $logPath
    Assert-ScenarioOutputs -ScenarioConfig $scenarioConfig -TracePath $tracePath -AssertionsPath $assertionsPath
} finally {
    try {
        Stop-ServerProcess -Process $process
    } finally {
        Get-EventSubscriber | Where-Object {
            $_.SourceObject -eq $process -and ($_.EventName -eq 'OutputDataReceived' -or $_.EventName -eq 'ErrorDataReceived')
        } | Unregister-Event

        $logWriter.Dispose()
        $process.Dispose()
    }
}

Write-Host "Scenario '$Scenario' completed successfully for '$Server'."
Write-Host "Trace: $tracePath"
Write-Host "Assertions: $assertionsPath"
Write-Host "Retained server home at '$($serverConfig.serverHome)' for cache reuse."
