$content = Get-Content -Path 'js_bundle.txt' -Raw
$index = 0
while (($index = $content.IndexOf('toast', $index, [System.StringComparison]::OrdinalIgnoreCase)) -ge 0) {
    $start = [Math]::Max(0, $index - 50)
    $length = [Math]::Min($content.Length - $start, 150)
    Write-Output "Found: $($content.Substring($start, $length) -replace '\s+', ' ')"
    $index += 5
}
