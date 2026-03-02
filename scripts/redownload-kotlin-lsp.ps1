$ErrorActionPreference = 'Stop'

$version = '261.13587.0'
$zipUrl = "https://download-cdn.jetbrains.com/kotlin-lsp/$version/kotlin-lsp-$version-win-x64.zip"
$shaUrl = "$zipUrl.sha256"
$tmpRoot = Join-Path $env:TEMP 'kotlin-lsp-redownload'
$zipPath = Join-Path $tmpRoot "kotlin-lsp-$version-win-x64.zip"
$shaPath = "$zipPath.sha256"
$extractPath = Join-Path $tmpRoot 'extract'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = "C:\tools\kotlin-lsp.backup-$stamp"

New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null
Remove-Item $extractPath -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $extractPath | Out-Null

Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath
Invoke-WebRequest -Uri $shaUrl -OutFile $shaPath

$expected = (Get-Content $shaPath -Raw).Trim().Split(' ')[0].ToLower()
$actual = (Get-FileHash $zipPath -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) {
    throw "SHA256 mismatch. expected=$expected actual=$actual"
}

& tar.exe -xf $zipPath -C $extractPath
$root = Get-ChildItem $extractPath | Select-Object -First 1
if (-not $root) {
    throw 'Archive extracted no files'
}

$rootPath = $root.FullName
$mainJar = Join-Path $rootPath 'lib\language-server.kotlin-lsp.jar'
if (-not (Test-Path $mainJar)) {
    throw "Expected jar missing: $mainJar"
}

$probeHelp = & (Join-Path $rootPath 'kotlin-lsp.cmd') --help 2>&1 | Out-String
if ($probeHelp -notmatch 'Usage: kotlin-lsp \[OPTIONS\]') {
    throw 'Downloaded kotlin-lsp package failed --help sanity check'
}

Get-Process java, javaw -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -like 'C:\tools\kotlin-lsp*' -or $_.Path -like 'C:\tools\kotlin-lsp.backup-*' } |
    Stop-Process -Force -ErrorAction SilentlyContinue

if (Test-Path 'C:\tools\kotlin-lsp') {
    Rename-Item 'C:\tools\kotlin-lsp' $backupPath
}

Move-Item $rootPath 'C:\tools\kotlin-lsp'

$installedJar = Get-Item 'C:\tools\kotlin-lsp\lib\language-server.kotlin-lsp.jar'
$installedHelp = & 'C:\tools\kotlin-lsp\kotlin-lsp.cmd' --help 2>&1 | Out-String

[pscustomobject]@{
    Version = $version
    ZipUrl = $zipUrl
    ExpectedSha256 = $expected
    ActualSha256 = $actual
    DownloadedZipBytes = (Get-Item $zipPath).Length
    BackupPath = $backupPath
    InstalledMainJarBytes = $installedJar.Length
    HelpWorks = ($installedHelp -match 'Usage: kotlin-lsp \[OPTIONS\]')
} | ConvertTo-Json -Depth 4
