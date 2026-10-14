$content = Get-Content -Path 'js_bundle.txt' -Raw
$patterns = @('form', 'input', 'button', 'modal', 'dialog', 'drawer', 'menu', 'login', 'logout', 'dangxuat', 'dangnhap', 'tai_khoan', 'mat_khau', 'username', 'password')
foreach ($p in $patterns) {
    $matches = [System.Text.RegularExpressions.Regex]::Matches($content, "class:\s*[\"']([^\"']*$p[^\"']*)[\"']", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Output "Pattern '$p' classes: $($matches.Count)"
    foreach ($m in $matches | Select-Object -First 5) {
        Write-Output "  Class: $($m.Groups[1].Value)"
    }
}
