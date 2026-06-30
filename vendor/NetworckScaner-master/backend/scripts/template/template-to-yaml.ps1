#Requires -Version 5.1
<#
.SYNOPSIS
  Converts obfuscated .template files back to plain YAML.

.DESCRIPTION
  Inverse of yaml-to-template.ps1 (Base64 decode + reverse UTF-8 string).

.PARAMETER Path
  File or directory. Directories are scanned recursively for *.template.

.PARAMETER KeepTemplate
  Do not delete source .template after successful conversion.

.EXAMPLE
  .\template-to-yaml.ps1 -Path C:\templates\template_os_linux_snmp_snmp.template

.EXAMPLE
  .\template-to-yaml.ps1 -Path .\out\ -KeepTemplate
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Path,

    [switch] $KeepTemplate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Convert-TemplateContentToYaml {
    param([string] $Base64Content)
    $trimmed = $Base64Content.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed)) {
        throw 'Corrupt template file: empty content'
    }
    try {
        $reversedBytes = [Convert]::FromBase64String($trimmed)
    }
    catch {
        throw 'Corrupt template file: invalid Base64'
    }
    $reversed = [System.Text.Encoding]::UTF8.GetString($reversedBytes)
    $chars = $reversed.ToCharArray()
    [array]::Reverse($chars)
    return -join $chars
}

function Get-YamlOutputPath {
    param([string] $TemplatePath)
    if ($TemplatePath -match '\.template$') {
        return $TemplatePath -replace '\.template$', '.yaml'
    }
    return "$TemplatePath.yaml"
}

function Convert-SingleTemplateFile {
    param(
        [string] $TemplateFile,
        [bool] $RetainSource
    )
    $raw = [System.IO.File]::ReadAllText($TemplateFile)
    $plain = Convert-TemplateContentToYaml -Base64Content $raw
    $out = Get-YamlOutputPath -TemplatePath $TemplateFile
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($out, $plain, $utf8NoBom)
    Write-Host "OK: $TemplateFile -> $out"
    if (-not $RetainSource) {
        Remove-Item -LiteralPath $TemplateFile -Force
        Write-Host "Removed: $TemplateFile"
    }
}

$resolved = (Resolve-Path -LiteralPath $Path).Path

if (Test-Path -LiteralPath $resolved -PathType Leaf) {
    if (-not $resolved.ToLowerInvariant().EndsWith('.template')) {
        throw "Expected .template file: $resolved"
    }
    Convert-SingleTemplateFile -TemplateFile $resolved -RetainSource:$KeepTemplate.IsPresent
    exit 0
}

if (-not (Test-Path -LiteralPath $resolved -PathType Container)) {
    throw "Path not found: $Path"
}

$files = Get-ChildItem -LiteralPath $resolved -Recurse -File -Filter *.template
if ($files.Count -eq 0) {
    Write-Warning "No .template files under $resolved"
    exit 0
}

foreach ($file in $files) {
    Convert-SingleTemplateFile -TemplateFile $file.FullName -RetainSource:$KeepTemplate.IsPresent
}

Write-Host "Done. Converted $($files.Count) file(s)."
