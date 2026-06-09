$file = $args[0]
$content = Get-Content $file -Raw
$content = $content -replace 'pick f06e857', 'reword f06e857'
$content = $content -replace 'pick 429f162', 'reword 429f162'
$content = $content -replace 'pick 993c110', 'reword 993c110'
$content = $content -replace 'pick 5f884c1', 'reword 5f884c1'
Set-Content -Path $file -Value $content -NoNewline
