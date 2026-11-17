$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}'
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
$out = @()

# A1: PENDING 建议列表
$a1 = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/agent-advices?status=PENDING&pageNum=1&pageSize=20' -Headers $H
$pend = @($a1.data.records) | Where-Object { $_.scene -in @('HERMES_APPROVAL','TOOL_APPROVAL') }
$out += [PSCustomObject]@{ case='A1'; total=$a1.data.total; hermesOrToolPending=($pend | ForEach-Object { "$($_.id):$($_.scene)" }) -join ',' }

# A2/A3: HERMES_APPROVAL PENDING 建议采纳/驳回（若存在两条则分别操作；一条则只驳回并记录）
$hermesPending = @($a1.data.records) | Where-Object { $_.scene -eq 'HERMES_APPROVAL' -and $_.status -eq 'PENDING' }
if ($hermesPending.Count -ge 1) {
  $target = $hermesPending[0]
  try {
    $ap = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/agent-advices/$($target.id)/approve" -Method Post -Headers $H -ContentType 'application/json' -Body '{}'
    $out += [PSCustomObject]@{ case='A2'; adviceId=$target.id; code=$ap.code; msg=$ap.msg }
  } catch { $out += [PSCustomObject]@{ case='A2'; adviceId=$target.id; code='ERR'; msg=$_.Exception.Message.Substring(0,[Math]::Min(120,$_.Exception.Message.Length)) } }
}
if ($hermesPending.Count -ge 2) {
  $t2 = $hermesPending[1]
  try {
    $rj = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/agent-advices/$($t2.id)/reject" -Method Post -Headers $H -ContentType 'application/json' -Body '{}'
    $out += [PSCustomObject]@{ case='A3'; adviceId=$t2.id; code=$rj.code; msg=$rj.msg }
  } catch { $out += [PSCustomObject]@{ case='A3'; adviceId=$t2.id; code='ERR'; msg=$_.Exception.Message.Substring(0,[Math]::Min(120,$_.Exception.Message.Length)) } }
}

# A4: Hermes 事件时间线（既有 run）
try {
  $ev = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/hermes/runs/run_8a0b66ce3840/events' -Headers $H
  $evs = @($ev.data)
  $out += [PSCustomObject]@{ case='A4'; count=$evs.Count; types=($evs | ForEach-Object { $_.eventType }) -join ',' }
} catch { $out += [PSCustomObject]@{ case='A4'; count='ERR'; types=$_.Exception.Message.Substring(0,[Math]::Min(120,$_.Exception.Message.Length)) } }

$json = $out | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText("$env:TEMP\c3_audit_results.json", $json, [Text.Encoding]::UTF8)
Get-Content "$env:TEMP\c3_audit_results.json" -Encoding UTF8
