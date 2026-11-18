$login = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body '{"username":"sunzhihua","password":"123456"}' -TimeoutSec 15
$token = $login.data.token
$h = @{ Authorization = "Bearer $token" }

# 1. M6 文档工作台：创建草稿 → 列表 → 合规检查
$body = @{ title = '风电场消防技改验收方案（AI 起草）'; contentMd = "# 验收方案`n`n## 1 概述`n本方案覆盖风电场自动消防系统改造的验收流程。`n`n## 2 验收标准`n消防设施需符合行业规范要求，探测器布点按标准执行。`n`n## 3 验收流程`n自检初验后组织现场验收并出具报告。" } | ConvertTo-Json
$created = Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/docs' -Method Post -ContentType 'application/json; charset=utf-8' -Headers $h -Body ([Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 30
$docId = $created.data.id
Write-Output ("DOC CREATED: id=$docId docNo=" + $created.data.docNo + " status=" + $created.data.status + " v" + $created.data.version)

# 编辑 → 版本+1
$ubody = @{ contentMd = "# 验收方案（修订）`n`n## 1 概述`n修订版：补充探测器布点与复验流程。" } | ConvertTo-Json
$updated = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/docs/$docId" -Method Put -ContentType 'application/json; charset=utf-8' -Headers $h -Body ([Text.Encoding]::UTF8.GetBytes($ubody)) -TimeoutSec 30
Write-Output ("DOC UPDATED: v" + $updated.data.version)

# 送审
$reviewed = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/docs/$docId/submit-review" -Method Post -Headers $h -TimeoutSec 30
Write-Output ("DOC SUBMITTED: status=" + $reviewed.data.status)

# 合规检查（LLM）
$cbody = @{} | ConvertTo-Json
try {
    $comp = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/docs/$docId/compliance-check" -Method Post -ContentType 'application/json; charset=utf-8' -Headers $h -Body $cbody -TimeoutSec 120
    $items = $comp.data.items.Count
    Write-Output ("COMPLIANCE: conclusion=" + $comp.data.conclusion + " items=$items kbBasis=" + $comp.data.kbBasisCount)
} catch {
    Write-Output ("COMPLIANCE FAIL: " + $_.Exception.Message)
}

# 2. M7 专业意见：对话触发 AGT-28
$conv = (Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/conversations' -Method Post -Headers $h -ContentType 'application/json' -Body '{"title":"D6专业意见验证"}' -TimeoutSec 15).data
$convId = $conv.id
$qbody = @{ content = '专业意见：风电场自动消防系统改造应该符合哪些行业标准和规范要求' } | ConvertTo-Json
$r = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/ai/ws/conversations/$convId/chat" -Method Post -ContentType 'application/json; charset=utf-8' -Headers $h -Body ([Text.Encoding]::UTF8.GetBytes($qbody)) -TimeoutSec 60
$am = $r.data.assistantMessage
Write-Output ("ADVISOR ROUTE: msgType=" + $am.msgType)
Write-Output ($am.content.Substring(0, [Math]::Min(120, $am.content.Length)))
