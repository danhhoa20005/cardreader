$html = (Invoke-WebRequest -Uri 'http://diemdanhhs.com' -UseBasicParsing).Content
if ($html -match 'src="([^"]+index-[^"]+\.js)"') {
    $jsUrl = 'http://diemdanhhs.com' + $Matches[1]
    Write-Output "JS URL: $jsUrl"
    $jsContent = (Invoke-WebRequest -Uri $jsUrl -UseBasicParsing).Content
    Set-Content -Path 'js_bundle.txt' -Value $jsContent -Encoding utf8
    Write-Output "Saved JS bundle, length: $($jsContent.Length)"
}
