$g = [System.Text.Encoding]::GetEncoding(936)
$tests = @(
  ,@(0xE4,0xBA,0xAB,0x35,0x39)
  ,@(0xE5,0x85,0x83,0x27,0x4D)
  ,@(0xE5,0x85,0x83,0x2F,0x4D)
)
foreach ($b in $tests) {
  $s = $g.GetString([byte[]]$b)
  $hex = ($s.ToCharArray() | ForEach-Object { ([int]$_).ToString('x4') }) -join ' '
  Write-Output ("IN=" + (($b | ForEach-Object { $_.ToString('x2') }) -join ' ') + " OUT=" + $hex)
}
