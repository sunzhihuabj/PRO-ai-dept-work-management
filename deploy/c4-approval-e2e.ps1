$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}'
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
$out = @()
$runId = $null
$status = ''

# 1) 委托明确触发敏感写操作（task.create 为 MCP 白名单写工具）
try {
  $chatBody = [System.Text.Encoding]::UTF8.GetBytes('{"content":"委托AI Agent员工帮我创建任务：项目「三一重能一期」任务名「C4审批验证」优先级高，计划工时8小时"}')
  $convBody = [System.Text.Encoding]::UTF8.GetBytes('{"title":"C4-ApprovalE2E-V2"}')
  $conv = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/conversations' -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $convBody
  $cid = $conv.data.id
  $chat = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/conversations/$cid/chat" -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $chatBody -TimeoutSec 300
  $am = $chat.data.assistantMessage
  if ($am.content -match 'hermes-run:([a-zA-Z0-9_-]+)') { $runId = $Matches[1] }
  $out += [PSCustomObject]@{ step='1.委托提交'; conv=$cid; msgType=$am.msgType; runId=$runId }
} catch {
  $out += [PSCustomObject]@{ step='1.委托提交'; conv='ERR'; msg=$_.Exception.Message.Substring(0,[Math]::Min(150,$_.Exception.Message.Length)) }
}

# 2) 轮询 run 状态至 waiting_for_approval / 建议产生 / 终态（最长 8 分钟）
$approvalSeen = $false; $adviceId = $null
if ($runId) {
  for ($i=0; $i -lt 48; $i++) {
    Start-Sleep -Seconds 10
    try {
      $run = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/hermes/runs/$runId" -Headers $H
      $status = $run.data.status
    } catch { $status = 'poll-err' }
    if ($status -eq 'waiting_for_approval') { $approvalSeen = $true }
    try {
      $adv = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/agent-advices?status=PENDING&pageNum=1&pageSize=20' -Headers $H
      $her = @($adv.data.records) | Where-Object { $_.adviceAction -eq 'HERMES_RESOLVE' } | Select-Object -First 1
      if ($her) { $approvalSeen = $true; $adviceId = $her.id; break }
    } catch {}
    if ($status -in @('completed','failed','cancelled','stopped','error','unknown')) { break }
  }
}
$out += [PSCustomObject]@{ step='2.审批等待'; runId=$runId; statusAtExit=$status; approvalSeen=$approvalSeen; adviceId=$adviceId }

# 3) 采纳 HERMES_APPROVAL 建议 → once 回调 → 轮询终态
if ($adviceId) {
  try {
    $apBody = [System.Text.Encoding]::UTF8.GetBytes('{"comment":"C4端到端验证：采纳放行一次"}')
    $ap = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/agent-advices/$adviceId/approve" -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $apBody
    $out += [PSCustomObject]@{ step='3.建议采纳'; adviceId=$adviceId; code=$ap.code }
  } catch {
    $out += [PSCustomObject]@{ step='3.建议采纳'; adviceId=$adviceId; code='ERR'; msg=$_.Exception.Message.Substring(0,[Math]::Min(150,$_.Exception.Message.Length)) }
  }
  for ($i=0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 10
    try {
      $run = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/hermes/runs/$runId" -Headers $H
      $status = $run.data.status
    } catch { $status = 'poll-err' }
    if ($status -in @('completed','failed','cancelled','stopped','error','unknown')) { break }
  }
}

# 4) run 终态 + 时间线
$evCount = 0; $evTypes = ''
try {
  $ev = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/hermes/runs/$runId/events" -Headers $H
  $evs = @($ev.data); $evCount = $evs.Count
  $evTypes = ($evs | ForEach-Object { $_.eventType }) -join ','
} catch {}
$out += [PSCustomObject]@{ step='4.终态时间线'; runId=$runId; status=$status; eventCount=$evCount }

# 5) 任务落库验证
try {
  $tasks = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/tasks?pageNum=1&pageSize=5&keyword=C4' -Headers $H
  $hit = @($tasks.data.records) | Where-Object { $_.taskName -like '*C4*' } | Select-Object -First 1
  if ($hit) {
    $out += [PSCustomObject]@{ step='5.任务落库'; found=$true; taskId=$hit.id; name=$hit.taskName; status=$hit.status }
  } else {
    $out += [PSCustomObject]@{ step='5.任务落库'; found=$false }
  }
} catch {
  $out += [PSCustomObject]@{ step='5.任务落库'; found='ERR' }
}

$json = $out | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText("D:\2、开发源码\基于AI的部门工作管理软件0812\deploy\logs\c4_approval_e2e.json", $json, [Text.Encoding]::UTF8)
Write-Output $json
