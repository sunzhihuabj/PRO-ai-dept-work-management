$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}'
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
# O1/O2/O5: Hermes 委托（立即返回 hermes 卡片，后台 run）
# O3: Ops 写入待确认（返回确认卡片）
# O4: Agent 能力点（日报 → AgentTask 异步）
$cases = @(
  @{ id='O1'; text='委托AI Agent员工帮我跟进：比对生产排期与交付计划的冲突项，然后把冲突项同步给售后部门，之后评估冲突对交付节奏的影响程度' },
  @{ id='O2'; text='委托AI Agent员工：统计本周任务完成率并生成一份简报' },
  @{ id='O3'; text='帮我创建任务：项目「三一重能一期」任务名「C3联调验证」优先级高' },
  @{ id='O4'; text='生成今天的日报' },
  @{ id='O5'; text='委托AI Agent员工跟进：检查库存不足的物料清单，并列出需要紧急采购的项' }
)
$results = @()
foreach ($c in $cases) {
  try {
    $conv = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/conversations' -Method Post -Headers $H -ContentType 'application/json' -Body ('{"title":"C3-' + $c.id + '"}')
    $cid = $conv.data.id
    $chat = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/conversations/$cid/chat" -Method Post -Headers $H -ContentType 'application/json' -Body ('{"content":"' + $c.text + '"}') -TimeoutSec 260
    $reply = $chat.data.assistantMessage.content
    $len = [Math]::Min(260, $reply.Length)
    $results += [PSCustomObject]@{ id=$c.id; conv=$cid; type=$chat.data.assistantMessage.msgType; refTaskId=$chat.data.assistantMessage.refTaskId; reply=$reply.Substring(0,$len) }
  } catch {
    $msg = $_.Exception.Message
    $results += [PSCustomObject]@{ id=$c.id; conv='ERR'; type='ERR'; refTaskId=$null; reply=$msg.Substring(0,[Math]::Min(150,$msg.Length)) }
  }
  Start-Sleep -Seconds 3
}
$json = $results | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText("$env:TEMP\c3_ops_results.json", $json, [Text.Encoding]::UTF8)
Get-Content "$env:TEMP\c3_ops_results.json" -Encoding UTF8
