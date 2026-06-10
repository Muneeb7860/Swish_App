$paths = Get-ChildItem -Path "C:\Users\DELL 9420\Documents\swiss_App" -Recurse -Filter "jacoco.csv"
foreach ($file in $paths) {
    $csv = Import-Csv $file.FullName
    $missed = 0
    $covered = 0
    foreach ($row in $csv) {
        $missed += [int]$row.INSTRUCTION_MISSED
        $covered += [int]$row.INSTRUCTION_COVERED
    }
    $total = $missed + $covered
    if ($total -gt 0) {
        $pct = ($covered / $total) * 100
        $formatted = "{0:N2}" -f $pct
        Write-Output "$($file.DirectoryName) -> $formatted%"
    }
}
