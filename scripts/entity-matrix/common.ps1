Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-EntityMatrixRepoRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
}

function Resolve-EntityMatrixPath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [string] $RelativePath
    )

    $normalized = $RelativePath.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $normalized))
}

function Read-EntityMatrixJson {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -Depth 20
}

function Get-EntityMatrixServerConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [string] $Server
    )

    $configPath = Join-Path $PSScriptRoot 'servers.json'
    $config = Read-EntityMatrixJson -Path $configPath
    $serverConfig = $config.servers | Where-Object { $_.id -eq $Server } | Select-Object -First 1
    if ($null -eq $serverConfig) {
        $availableServers = ($config.servers | ForEach-Object { $_.id }) -join ', '
        throw "Unknown server '$Server'. Available servers: $availableServers"
    }

    return [pscustomobject]@{
        id = $serverConfig.id
        serverType = $serverConfig.serverType
        minecraftVersion = $serverConfig.minecraftVersion
        javaMajor = [int]$serverConfig.javaMajor
        javaExecutable = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $serverConfig.javaExecutable
        javaHome = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $serverConfig.javaHome
        serverHome = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $serverConfig.serverHome
        serverJar = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $serverConfig.serverJar
        downloadCache = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $serverConfig.downloadCache
        raw = $serverConfig
    }
}

function Get-EntityMatrixScenarioConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Scenario
    )

    $configPath = Join-Path $PSScriptRoot 'scenarios.json'
    $config = Read-EntityMatrixJson -Path $configPath
    $scenarioConfig = $config.scenarios | Where-Object { $_.id -eq $Scenario } | Select-Object -First 1
    if ($null -eq $scenarioConfig) {
        $availableScenarios = ($config.scenarios | ForEach-Object { $_.id }) -join ', '
        throw "Unknown scenario '$Scenario'. Available scenarios: $availableScenarios"
    }

    return $scenarioConfig
}

function Ensure-EntityMatrixDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Invoke-EntityMatrixDownload {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Uri,
        [Parameter(Mandatory = $true)]
        [string] $DestinationPath
    )

    $parent = Split-Path -Parent $DestinationPath
    Ensure-EntityMatrixDirectory -Path $parent
    Write-Host "Downloading $Uri"
    Invoke-WebRequest -Uri $Uri -OutFile $DestinationPath
}

function Expand-EntityMatrixArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ArchivePath,
        [Parameter(Mandatory = $true)]
        [string] $DestinationPath
    )

    $extractRoot = Join-Path ([System.IO.Path]::GetDirectoryName($ArchivePath)) ([System.IO.Path]::GetFileNameWithoutExtension($ArchivePath) + '-expanded')
    if (Test-Path -LiteralPath $extractRoot) {
        Remove-Item -LiteralPath $extractRoot -Recurse -Force
    }
    Ensure-EntityMatrixDirectory -Path $extractRoot
    Expand-Archive -LiteralPath $ArchivePath -DestinationPath $extractRoot -Force

    $children = @(Get-ChildItem -LiteralPath $extractRoot)
    $contentRoot = $extractRoot
    if ($children.Count -eq 1 -and $children[0].PSIsContainer) {
        $contentRoot = $children[0].FullName
    }

    if (Test-Path -LiteralPath $DestinationPath) {
        Remove-Item -LiteralPath $DestinationPath -Recurse -Force
    }
    Ensure-EntityMatrixDirectory -Path $DestinationPath
    Copy-Item -Path (Join-Path $contentRoot '*') -Destination $DestinationPath -Recurse -Force
    Remove-Item -LiteralPath $extractRoot -Recurse -Force
}

function Get-TemurinPackageUri {
    param(
        [Parameter(Mandatory = $true)]
        [int] $JavaMajor
    )

    $apiUri = "https://api.adoptium.net/v3/assets/latest/$JavaMajor/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse"
    $response = Invoke-RestMethod -Uri $apiUri -Method Get
    if ($null -eq $response -or $response.Count -eq 0) {
        throw "No Temurin JDK package was returned for Java $JavaMajor."
    }

    $link = $response[0].binary.package.link
    if ([string]::IsNullOrWhiteSpace($link)) {
        throw "Temurin JDK package metadata for Java $JavaMajor did not include a download link."
    }

    return $link
}

function Ensure-TemurinJdk {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ServerConfig
    )

    if (Test-Path -LiteralPath $ServerConfig.javaExecutable) {
        return $ServerConfig.javaExecutable
    }

    $jdkRoot = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath '.tools/entity-matrix/jdks'
    $downloadsRoot = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath '.tools/entity-matrix/downloads/jdks'
    Ensure-EntityMatrixDirectory -Path $jdkRoot
    Ensure-EntityMatrixDirectory -Path $downloadsRoot

    $archivePath = Join-Path $downloadsRoot ("temurin-jdk-$($ServerConfig.javaMajor).zip")
    if (-not (Test-Path -LiteralPath $archivePath)) {
        $jdkUri = Get-TemurinPackageUri -JavaMajor $ServerConfig.javaMajor
        Invoke-EntityMatrixDownload -Uri $jdkUri -DestinationPath $archivePath
    }

    Expand-EntityMatrixArchive -ArchivePath $archivePath -DestinationPath $ServerConfig.javaHome

    if (-not (Test-Path -LiteralPath $ServerConfig.javaExecutable)) {
        throw "Provisioned JDK for Java $($ServerConfig.javaMajor) but did not find '$($ServerConfig.javaExecutable)'."
    }

    return $ServerConfig.javaExecutable
}

function Invoke-EntityMatrixProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,
        [Parameter(Mandatory = $true)]
        [string[]] $ArgumentList,
        [Parameter(Mandatory = $true)]
        [string] $WorkingDirectory,
        [hashtable] $Environment = @{},
        [int] $TimeoutSeconds = 0,
        [string] $LogPath
    )

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $process.StartInfo.FileName = $FilePath
    foreach ($argument in $ArgumentList) {
        [void]$process.StartInfo.ArgumentList.Add($argument)
    }
    $process.StartInfo.WorkingDirectory = $WorkingDirectory
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.RedirectStandardInput = $true
    $process.StartInfo.CreateNoWindow = $true

    foreach ($key in $Environment.Keys) {
        $process.StartInfo.Environment[$key] = [string]$Environment[$key]
    }

    $logWriter = $null
    if ($PSBoundParameters.ContainsKey('LogPath') -and -not [string]::IsNullOrWhiteSpace($LogPath)) {
        Ensure-EntityMatrixDirectory -Path (Split-Path -Parent $LogPath)
        $logWriter = New-Object System.IO.StreamWriter($LogPath, $true)
        $logWriter.AutoFlush = $true
    }

    try {
        $null = Register-ObjectEvent -InputObject $process -EventName OutputDataReceived -Action {
            if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
                if ($null -ne $Event.MessageData) {
                    $Event.MessageData.WriteLine($EventArgs.Data)
                }
                Write-Host $EventArgs.Data
            }
        } -MessageData $logWriter

        $null = Register-ObjectEvent -InputObject $process -EventName ErrorDataReceived -Action {
            if (-not [string]::IsNullOrWhiteSpace($EventArgs.Data)) {
                if ($null -ne $Event.MessageData) {
                    $Event.MessageData.WriteLine($EventArgs.Data)
                }
                Write-Host $EventArgs.Data
            }
        } -MessageData $logWriter

        if (-not $process.Start()) {
            throw "Failed to start process '$FilePath'."
        }

        $process.BeginOutputReadLine()
        $process.BeginErrorReadLine()

        if ($TimeoutSeconds -gt 0) {
            if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
                try {
                    $process.Kill()
                } catch {
                }
                throw "Process '$FilePath' timed out after $TimeoutSeconds seconds."
            }
        } else {
            $process.WaitForExit()
        }

        if ($process.ExitCode -ne 0) {
            throw "Process '$FilePath' exited with code $($process.ExitCode)."
        }
    } finally {
        Get-EventSubscriber | Where-Object {
            $_.SourceObject -eq $process -and ($_.EventName -eq 'OutputDataReceived' -or $_.EventName -eq 'ErrorDataReceived')
        } | Unregister-Event

        if ($null -ne $logWriter) {
            $logWriter.Dispose()
        }
        $process.Dispose()
    }
}

function Get-BuildToolsEnvironment {
    $pathEntries = [System.Collections.Generic.List[string]]::new()

    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    if ($null -ne $gitCommand -and -not [string]::IsNullOrWhiteSpace($gitCommand.Source)) {
        $gitCmdDirectory = Split-Path -Parent $gitCommand.Source
        $gitRoot = Split-Path -Parent $gitCmdDirectory
        foreach ($candidate in @(
            $gitCmdDirectory,
            (Join-Path $gitRoot 'bin'),
            (Join-Path $gitRoot 'usr/bin')
        )) {
            if (Test-Path -LiteralPath $candidate) {
                $pathEntries.Add($candidate)
            }
        }
    }

    $pathEntries.Add($env:PATH)

    return @{
        'PATH' = (($pathEntries | Select-Object -Unique) -join [System.IO.Path]::PathSeparator)
    }
}

function Get-GitForWindowsRoot {
    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    if ($null -eq $gitCommand -or [string]::IsNullOrWhiteSpace($gitCommand.Source)) {
        return $null
    }

    return Split-Path -Parent (Split-Path -Parent $gitCommand.Source)
}

function Get-GitBashExecutable {
    $gitRoot = Get-GitForWindowsRoot
    if ([string]::IsNullOrWhiteSpace($gitRoot)) {
        return $null
    }

    $gitBash = Join-Path $gitRoot 'bin/bash.exe'
    if (Test-Path -LiteralPath $gitBash) {
        return $gitBash
    }

    return $null
}

function Ensure-BuildToolsShellShim {
    param(
        [Parameter(Mandatory = $true)]
        [string] $WorkingDirectory
    )

    $gitRoot = Get-GitForWindowsRoot
    if ([string]::IsNullOrWhiteSpace($gitRoot)) {
        return
    }

    $gitBash = Join-Path $gitRoot 'bin/bash.exe'
    if (-not (Test-Path -LiteralPath $gitBash)) {
        return
    }

    $gitSh = Join-Path $gitRoot 'usr/bin/sh.exe'
    $shimContent = @(
        '@echo off',
        '"' + $gitBash + '" %*'
    )

    $shimDirectories = @(
        $WorkingDirectory,
        (Join-Path $WorkingDirectory 'Bukkit'),
        (Join-Path $WorkingDirectory 'CraftBukkit'),
        (Join-Path $WorkingDirectory 'Spigot'),
        (Join-Path $WorkingDirectory 'Spigot\Bukkit'),
        (Join-Path $WorkingDirectory 'Spigot\CraftBukkit')
    ) | Where-Object { Test-Path -LiteralPath $_ }

    foreach ($directory in $shimDirectories) {
        foreach ($shimName in @('bash.cmd', 'sh.cmd')) {
            Set-Content -LiteralPath (Join-Path $directory $shimName) -Value $shimContent -Encoding ASCII
        }

        foreach ($link in @(
            @{ Name = 'bash.exe'; Source = $gitBash },
            @{ Name = 'sh.exe'; Source = $gitSh }
        )) {
            if (-not (Test-Path -LiteralPath $link.Source)) {
                continue
            }

            $destination = Join-Path $directory $link.Name
            if (Test-Path -LiteralPath $destination) {
                continue
            }

            try {
                New-Item -ItemType HardLink -Path $destination -Target $link.Source | Out-Null
            } catch {
                Copy-Item -LiteralPath $link.Source -Destination $destination -Force
            }
        }
    }
}

function Ensure-BuildToolsJar {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ServerConfig
    )

    $buildToolsJar = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath $ServerConfig.raw.provisioning.buildToolsJar
    if (-not (Test-Path -LiteralPath $buildToolsJar)) {
        Invoke-EntityMatrixDownload -Uri $ServerConfig.raw.provisioning.buildToolsUrl -DestinationPath $buildToolsJar
    }

    return $buildToolsJar
}

function Ensure-SpigotServerJar {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ServerConfig
    )

    if (Test-Path -LiteralPath $ServerConfig.serverJar) {
        return $ServerConfig.serverJar
    }

    Ensure-EntityMatrixDirectory -Path $ServerConfig.serverHome
    Ensure-EntityMatrixDirectory -Path $ServerConfig.downloadCache
    Ensure-BuildToolsShellShim -WorkingDirectory $ServerConfig.serverHome
    $javaExecutable = Ensure-TemurinJdk -RepoRoot $RepoRoot -ServerConfig $ServerConfig
    $buildToolsJar = Ensure-BuildToolsJar -RepoRoot $RepoRoot -ServerConfig $ServerConfig
    $buildLog = Join-Path $ServerConfig.serverHome 'buildtools.log'

    $gitBash = Get-GitBashExecutable
    if ($null -ne $gitBash) {
        $bashCommand = '"' + ($javaExecutable -replace '\\', '/') + '" -jar "' + ($buildToolsJar -replace '\\', '/') + '" --rev "' + $ServerConfig.minecraftVersion + '"'
        Invoke-EntityMatrixProcess -FilePath $gitBash -ArgumentList @(
            '-lc',
            $bashCommand
        ) -WorkingDirectory $ServerConfig.serverHome -Environment (Get-BuildToolsEnvironment) -TimeoutSeconds 7200 -LogPath $buildLog
    } else {
        Invoke-EntityMatrixProcess -FilePath $javaExecutable -ArgumentList @(
            '-jar',
            $buildToolsJar,
            '--rev',
            $ServerConfig.minecraftVersion
        ) -WorkingDirectory $ServerConfig.serverHome -Environment (Get-BuildToolsEnvironment) -TimeoutSeconds 7200 -LogPath $buildLog
    }

    $artifactPattern = [string]$ServerConfig.raw.provisioning.artifactPattern
    $builtJar = Get-ChildItem -LiteralPath $ServerConfig.serverHome -Filter $artifactPattern -File | Where-Object {
        $_.Name -notmatch 'remapped|sources|javadoc'
    } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1

    if ($null -eq $builtJar) {
        throw "BuildTools finished but no server jar matching '$artifactPattern' was produced in '$($ServerConfig.serverHome)'."
    }

    Copy-Item -LiteralPath $builtJar.FullName -Destination $ServerConfig.serverJar -Force
    return $ServerConfig.serverJar
}

function Ensure-PaperServerJar {
    param(
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ServerConfig
    )

    if (Test-Path -LiteralPath $ServerConfig.serverJar) {
        return $ServerConfig.serverJar
    }

    Ensure-EntityMatrixDirectory -Path $ServerConfig.serverHome
    Ensure-EntityMatrixDirectory -Path $ServerConfig.downloadCache

    $buildsResponse = Invoke-RestMethod -Uri $ServerConfig.raw.provisioning.downloadsApi -Method Get
    if ($null -eq $buildsResponse -or $null -eq $buildsResponse.builds -or $buildsResponse.builds.Count -eq 0) {
        throw "Paper downloads API for '$($ServerConfig.id)' did not return any builds."
    }

    $selectedBuild = $buildsResponse.builds | Sort-Object build -Descending | Select-Object -First 1
    $downloadName = $selectedBuild.downloads.application.name
    if ([string]::IsNullOrWhiteSpace($downloadName)) {
        throw "Paper build metadata for '$($ServerConfig.id)' did not include an application download name."
    }

    $downloadUri = "https://api.papermc.io/v2/projects/$($ServerConfig.raw.provisioning.project)/versions/$($ServerConfig.raw.provisioning.version)/builds/$($selectedBuild.build)/downloads/$downloadName"
    $archivePath = Join-Path $ServerConfig.downloadCache $downloadName
    Invoke-EntityMatrixDownload -Uri $downloadUri -DestinationPath $archivePath
    Copy-Item -LiteralPath $archivePath -Destination $ServerConfig.serverJar -Force

    return $ServerConfig.serverJar
}

function Ensure-ServerJar {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot,
        [Parameter(Mandatory = $true)]
        [pscustomobject] $ServerConfig
    )

    switch ([string]$ServerConfig.raw.provisioning.strategy) {
        'buildtools' {
            return Ensure-SpigotServerJar -RepoRoot $RepoRoot -ServerConfig $ServerConfig
        }
        'paper' {
            return Ensure-PaperServerJar -ServerConfig $ServerConfig
        }
        default {
            throw "Unsupported provisioning strategy '$($ServerConfig.raw.provisioning.strategy)' for '$($ServerConfig.id)'."
        }
    }
}

function Get-TestPluginJarPath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot
    )

    $targetDir = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath 'test-plugin/target'
    if (-not (Test-Path -LiteralPath $targetDir)) {
        return $null
    }

    return Get-ChildItem -LiteralPath $targetDir -Filter '*.jar' -File | Where-Object {
        $_.Name -notlike 'original-*' -and $_.Name -notmatch 'sources|javadoc'
    } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
}

function Ensure-TestPluginJar {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RepoRoot
    )

    $mavenWrapper = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $mavenWrapper)) {
        throw "Could not find Maven wrapper at '$mavenWrapper'."
    }

    $packageLog = Resolve-EntityMatrixPath -RepoRoot $RepoRoot -RelativePath ('.tools/entity-matrix/logs/test-plugin-package-' + $PID + '.log')
    Invoke-EntityMatrixProcess -FilePath 'cmd.exe' -ArgumentList @(
        '/c',
        '.\mvnw.cmd -pl test-plugin -am package -DskipTests'
    ) -WorkingDirectory $RepoRoot -TimeoutSeconds 3600 -LogPath $packageLog

    $pluginJar = Get-TestPluginJarPath -RepoRoot $RepoRoot
    if ($null -eq $pluginJar) {
        throw "Packaging completed but no test-plugin jar was produced in 'test-plugin/target'."
    }

    return $pluginJar.FullName
}
