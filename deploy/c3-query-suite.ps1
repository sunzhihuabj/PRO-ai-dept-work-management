$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}'
$token = $login.data.token
$H = @{ Authorization = "Bearer $token" }
# 编码修复（F3）：中文 body 必须 UTF-8 字节发送，否则 PS5 按 ANSI 编码中文变问号
$cases = @(
  @{ id='Q1'; text='查一下进行中的任务' },
  @{ id='Q2'; text='查一下我的待办任务' },
  @{ id='Q3'; text='合同的审批流程是什么' },
  @{ id='Q4'; text='查一下三一重能项目进行中的任务' },
  @{ id='Q5'; text='知识库里搜一下设备维护规范' }
)
$results = @()
foreach ($c in $cases) {
  try {
    $convBody = [System.Text.Encoding]::UTF8.GetBytes('{"title":"C3F-' + $c.id + '"}')
    $conv = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/conversations' -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $convBody
    $cid = $conv.data.id
    $chatBody = [System.Text.Encoding]::UTF8.GetBytes('{"content":"' + $c.text + '"}')
    $chat = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/conversations/$cid/chat" -Method Post -Headers $H -ContentType 'application/json; charset=utf-8' -Body $chatBody -TimeoutSec 260
    $reply = $chat.data.assistantMessage.content
    $len = [Math]::Min(220, $reply.Length)
    $results += [PSCustomObject]@{ id=$c.id; conv=$cid; type=$chat.data.assistantMessage.msgType; reply=$reply.Substring(0,$len) }
  } catch {
    $msg = $_.Exception.Message
    $results += [PSCustomObject]@{ id=$c.id; conv='ERR'; type='ERR'; reply=$msg.Substring(0,[Math]::Min(150,$msg.Length)) }
  }
  Start-Sleep -Seconds 3
}
$json = $results | ConvertTo-Json -Depth 3
[IO.File]::WriteAllText("$env:TEMP\c3_query_r3.json", $json, [Text.Encoding]::UTF8)
Get-Content "$env:TEMP\c3_query_r3.json" -Encoding UTF8
