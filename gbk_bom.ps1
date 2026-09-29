$ErrorActionPreference = 'Stop'
$gbk = [System.Text.Encoding]::GetEncoding(936)
$utf8 = [System.Text.Encoding]::UTF8
$samples = @(
  '涓汉涓昏祫璐',
  '闇€姹傛彁鎶?,
  '閰嶇疆鏂规',
  '閰嶇疆宸ヤ綔鍙?,
  '閰嶇疆淇℃伅',
  '鏄?,
  '鍚?,
  '骞?,
  '鍏?
)
foreach ($s in $samples) {
  $bytes = $gbk.GetBytes($s)
  $t = $utf8.GetString($bytes)
  $hex = ($t.ToCharArray() | ForEach-Object { ([int]$_).ToString('x4') }) -join ' '
  Write-Output ("IN=" + $s + "  OUTHEX=" + $hex)
}
