$content = Get-Content -Path 'js_bundle.txt' -Raw
$keywords = @('login', 'dangnhap', 'tai_khoan', 'mat_khau', 'username', 'password', 'dangxuat', 'logout', 'menu', 'hamburger', 'button', 'input')
foreach ($kw in $keywords) {
    $matches = [System.Text.RegularExpressions.Regex]::Matches($content, $kw, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Output "Keyword '$kw': $($matches.Count) matches"
    foreach ($m in $matches | Select-Object -First 3) {
        $start = [Math]::Max(0, $m.Index - 80)
        $length = [Math]::Min($content.Length - $start, 160)
        Write-Output "  Snippet: $($content.Substring($start, $length) -replace '\s+', ' ')"
    }
}
