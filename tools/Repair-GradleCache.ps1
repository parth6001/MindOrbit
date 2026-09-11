$ErrorActionPreference = 'Stop'
$root = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.toolchain\gradle-home\caches\8.13\transforms'))
$repaired = 0
Get-ChildItem -LiteralPath $root -Directory | Where-Object Name -Match '^[a-f0-9]{32}-[a-f0-9-]{36}$' | ForEach-Object {
    $target = [System.IO.Path]::GetFullPath((Join-Path $root $_.Name.Substring(0, 32)))
    if (-not $target.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Cache target is outside the expected directory.' }
    if ((Test-Path -LiteralPath (Join-Path $_.FullName 'metadata.bin')) -and -not (Test-Path -LiteralPath $target)) {
        Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse
        $repaired++
    }
}
Write-Output "Recovered $repaired completed Gradle transform directories."
