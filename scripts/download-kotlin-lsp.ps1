$ErrorActionPreference = 'Stop'

$version = '261.13587.0'
$tmpRoot = Join-Path $env:TEMP 'kotlin-lsp-redownload'
$zipPath = Join-Path $tmpRoot "kotlin-lsp-$version-win-x64.zip"
$shaPath = "$zipPath.sha256"
$zipUrl = "https://download-cdn.jetbrains.com/kotlin-lsp/$version/kotlin-lsp-$version-win-x64.zip"
$shaUrl = "$zipUrl.sha256"

New-Item -ItemType Directory -Force -Path $tmpRoot | Out-Null
Remove-Item $zipPath, $shaPath -Force -ErrorAction SilentlyContinue

& curl.exe -L --fail --retry 5 --retry-all-errors --connect-timeout 30 -o $zipPath $zipUrl
& curl.exe -L --fail --retry 5 --retry-all-errors --connect-timeout 30 -o $shaPath $shaUrl

$expected = (Get-Content $shaPath -Raw).Trim().Split(' ')[0].ToLower()
$actual = (Get-FileHash $zipPath -Algorithm SHA256).Hash.ToLower()

[pscustomobject]@{
    Version = $version
    ZipPath = $zipPath
    ZipBytes = (Get-Item $zipPath).Length
    ExpectedSha256 = $expected
    ActualSha256 = $actual
    Match = ($expected -eq $actual)
} | ConvertTo-Json -Depth 4
