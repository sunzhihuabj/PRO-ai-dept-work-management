﻿$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}'
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
$out = @()

# 1) 发送委托指令（UTF-8 正确编码）
$chatBody = [System.Text.Encoding]::UTF8.GetBytes('{"content":"委托AI Agent员工帮我跟进：比对生产排期与交付计划的冲突项，然后把冲突项同步给售后部门，之后评估冲突对交付节奏的影响程度"}')
$convBody = [System.Text.Encoding]::UTF8.GetBytes('{"title":"C3-HermesE2E"}')
$conv = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/conversations' -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $convBody
$cid = $conv.data.id
$chat = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/conversations/$cid/chat" -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $chatBody -TimeoutSec 120
$am = $chat.data.assistantMessage
$runId = $null
if ($am.content -match 'hermes-run:([a-zA-Z0-9_-]+)') { $runId = $Matches[1] }
$out += [PSCustomObject]@{ step='1.委托提交'; conv=$cid; msgType=$am.msgType; runId=$runId }

# 2) 等待后台 run 产生事件（轮询 events 接口，最长 90s）
$evCount = 0; $evTypes = ''
if ($runId) {
  for ($i=0; $i -lt 18; $i++) {
    Start-Sleep -Seconds 5
    try {
      $ev = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/hermes/runs/$runId/events" -Headers $H
      $evs = @($ev.data); $evCount = $evs.Count
      $evTypes = ($evs | ForEach-Object { $_.eventType }) -join ','
      if ($evTypes -match 'run\.(completed|failed|cancelled)') { break }
    } catch {}
  }
}
$out += [PSCustomObject]@{ step='2.事件时间线'; runId=$runId; eventCount=$evCount; types=$evTypes }

# 3) delegations 面板接口
$dl = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/hermes/delegations?pageNum=1&pageSize=50' -Headers $H
$newRow = @($dl.data) | Where-Object { $_.runId -eq $runId } | Select-Object -First 1
$out += [PSCustomObject]@{ step='3.委托面板'; total=$dl.data.Count; newRowStatus=$newRow.status; resultPreview=$(if($newRow.resultPreview){$newRow.resultPreview.Substring(0,[Math]::Min(80,$newRow.resultPreview.Length))}else{$null}) }

$json = $out | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText("$env:TEMP\c3_hermes_e2e.json", $json, [Text.Encoding]::UTF8)
Get-Content "$env:TEMP\c3_hermes_e2e.json" -Encoding UTF8
