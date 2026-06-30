#Requires -Version 5.1
<#
.SYNOPSIS
  Converts monitoring template YAML files to obfuscated .template (UTF-8 reverse + Base64).

.DESCRIPTION
  Same algorithm as MonitoringTemplateObfuscator in the backend.

.PARAMETER Path
  File or directory. Directories are scanned recursively for *.yaml and *.yml.

.PARAMETER KeepYaml
  Do not delete source .yaml/.yml after successful conversion.

.EXAMPLE
  .\yaml-to-template.ps1 -Path C:\templates\template_os_linux_snmp_snmp.yaml

.EXAMPLE
  .\yaml-to-template.ps1 -Path .\monitoring-templates\
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Path,

    [switch] $KeepYaml
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Convert-YamlContentToTemplate {
    param([string] $PlainYaml)
    $chars = $PlainYaml.ToCharArray()
    [array]::Reverse($chars)
    $reversed = -join $chars
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($reversed)
    return [Convert]::ToBase64String($bytes)
}

function Get-TemplateOutputPath {
    param([string] $YamlPath)
    if ($YamlPath -match '\.(yaml|yml)$') {
        return $YamlPath -replace '\.(yaml|yml)$', '.template'
    }
    return "$YamlPath.template"
}

function Convert-SingleYamlFile {
    param(
        [string] $YamlFile,
        [bool] $RetainSource
    )
    $plain = [System.IO.File]::ReadAllText($YamlFile)
    $encoded = Convert-YamlContentToTemplate -PlainYaml $plain
    $out = Get-TemplateOutputPath -YamlPath $YamlFile
    [System.IO.File]::WriteAllText($out, $encoded, [System.Text.Encoding]::ASCII)
    Write-Host "OK: $YamlFile -> $out"
    if (-not $RetainSource) {
        Remove-Item -LiteralPath $YamlFile -Force
        Write-Host "Removed: $YamlFile"
    }
}

$resolved = (Resolve-Path -LiteralPath $Path).Path

if (Test-Path -LiteralPath $resolved -PathType Leaf) {
    $ext = [System.IO.Path]::GetExtension($resolved).ToLowerInvariant()
    if ($ext -notin '.yaml', '.yml') {
        throw "Expected .yaml or .yml file: $resolved"
    }
    Convert-SingleYamlFile -YamlFile $resolved -RetainSource:$KeepYaml.IsPresent
    exit 0
}

if (-not (Test-Path -LiteralPath $resolved -PathType Container)) {
    throw "Path not found: $Path"
}

$files = Get-ChildItem -LiteralPath $resolved -Recurse -File -Include *.yaml, *.yml
if ($files.Count -eq 0) {
    Write-Warning "No .yaml/.yml files under $resolved"
    exit 0
}

foreach ($file in $files) {
    Convert-SingleYamlFile -YamlFile $file.FullName -RetainSource:$KeepYaml.IsPresent
}

Write-Host "Done. Converted $($files.Count) file(s)."
