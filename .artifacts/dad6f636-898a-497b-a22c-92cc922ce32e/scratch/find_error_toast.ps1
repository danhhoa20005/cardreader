$content = Get-Content -Path 'js_bundle.txt' -Raw
$matches = [System.Text.RegularExpressions.Regex]::Matches($content, 'toast\.[a-zA-Z]+\s*\([^)]+\)', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
foreach ($m in $matches | Select-Object -First 10) {
    Write-Output "Toast: $($m.Value)"
}
