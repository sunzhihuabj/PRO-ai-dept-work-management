# d7-acceptance.ps1 - V2.0 全量验收（D7）：黄金指令回归 + 5 条越权用例 + 轻量压测
$ErrorActionPreference = 'Stop'
$BASE = 'http://127.0.0.1:8081/api'

function Login($user, $pwd) {
    $login = Invoke-RestMethod -Uri "$BASE/v1/auth/login" -Method Post -ContentType 'application/json' `
        -Body ([Text.Encoding]::UTF8.GetBytes((@{username=$user; password=$pwd} | ConvertTo-Json)))
    return @{ Authorization = "Bearer $($login.data.token)" }
}

Write-Output '=== 1. 黄金指令回归（25 条路由断言，服务端 dryRoute 纯判定） ==='
$leader = Login 'sunzhihua' '123456'
$reg = Invoke-RestMethod -Uri "$BASE/v1/ai/self-iteration/regression/run" -Method Post -Headers $leader -TimeoutSec 60
Write-Output ("REGRESSION: total=" + $reg.data.total + " pass=" + $reg.data.pass + " fail=" + $reg.data.fail +
    " skip=" + $reg.data.skip + " passRate=" + $reg.data.passRate + " costMs=" + $reg.data.costMs)
foreach ($c in $reg.data.cases) {
    if ($c.result -eq 'FAIL') {
        Write-Output ("  FAIL " + $c.caseNo + " [" + $c.scene + "] " + $c.instruction + " expect=" + $c.expectedRoute + " actual=" + $c.actualRoute)
    }
}

Write-Output ''
Write-Output '=== 2. 越权用例（WORKER 直调管理端点，期望 403；POST 带合法 body 避免参数校验先拦） ==='
$worker = Login 'lihailong' '123456'
$permCases = @(
    @{ no='P01'; method='GET';  url="$BASE/v1/fine-perms/preview?userId=1"; body=$null },
    @{ no='P02'; method='GET';  url="$BASE/v1/agent-grants"; body=$null },
    @{ no='P03'; method='POST'; url="$BASE/v1/agent-grants"; body='{"agentInstanceKey":"agent:1:AGT-13","capabilityCode":"AGT-13","principalUserId":1,"expireTime":"2027-01-01T00:00:00"}' },
    @{ no='P04'; method='POST'; url="$BASE/v1/code-rules";   body='{"ruleCode":"ACCT","ruleName":"越权探针规则","pattern":"ACCT-{seq:3}","datePattern":"yyyyMMdd"}' },
    @{ no='P05'; method='POST'; url="$BASE/v1/ai-configs";   body='{}' }
)
$permPass = 0
foreach ($pc in $permCases) {
    $status = 0
    $bodyBytes = $null
    if ($pc.body) { $bodyBytes = [Text.Encoding]::UTF8.GetBytes($pc.body) }
    try {
        $resp = Invoke-WebRequest -Uri $pc.url -Method $pc.method -Headers $worker -ContentType 'application/json; charset=utf-8' `
            -Body $bodyBytes -UseBasicParsing -TimeoutSec 15
        $status = $resp.StatusCode
    } catch {
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode } else { $status = -1 }
    }
    $pass = ($status -eq 403)
    if ($pass) { $permPass++ }
    $passFlag = $pass.ToString().ToLower()
    Invoke-RestMethod -Uri "$BASE/v1/ai/self-iteration/regression/perm-result?caseNo=$($pc.no)&pass=$passFlag" -Method Post -Headers $leader -TimeoutSec 10 | Out-Null
    Write-Output ("  " + $pc.no + " " + $pc.method + " " + $pc.url + " -> HTTP " + $status + " " + $(if ($pass) {'PASS(403)'} else {'FAIL'}))
}
Write-Output ("PERM: " + $permPass + "/5 intercepted")

Write-Output ''
Write-Output '=== 3. 周迭代归因（反馈差评 → 建议） ==='
Invoke-RestMethod -Uri "$BASE/v1/ai/self-iteration/feedback" -Method Post -Headers $leader -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes('{"sourceType":"RUN","sourceId":1,"runId":"acceptance-run-1","rating":-1,"dimension":"TOOL","comment":"验收注入：缺少对账工具"}')) | Out-Null
$it = Invoke-RestMethod -Uri "$BASE/v1/ai/self-iteration/iterate" -Method Post -Headers $leader -TimeoutSec 30
Write-Output ("ITERATE: period=" + $it.data.period + " negativeCount=" + $it.data.negativeCount + " proposalsCreated=" + $it.data.proposalsCreated)
$props = Invoke-RestMethod -Uri "$BASE/v1/ai/self-iteration/proposals?status=PROPOSED" -Headers $leader -TimeoutSec 15
Write-Output ("PROPOSALS PENDING: " + $props.data.Count)

Write-Output ''
Write-Output '=== 4. 审计链联查探针（RUN_EVENT/AI_CALL/OUTBOUND 四段视图） ==='
$chain = Invoke-RestMethod -Uri "$BASE/v1/ai/audit-chain?userId=1" -Headers $leader -TimeoutSec 30
Write-Output ("AUDIT-CHAIN: total=" + $chain.data.total + " errorCount=" + $chain.data.errorCount)
$types = $chain.data.rows | Group-Object chainType | ForEach-Object { $_.Name + ":" + $_.Count }
Write-Output ("  segments: " + ($types -join ' '))

Write-Output ''
Write-Output '=== 5. 轻量压测（20 并发审计链/委托列表查询，P95 延迟） ==='
$swAll = [System.Diagnostics.Stopwatch]::StartNew()
$jobs = @()
1..20 | ForEach-Object {
    $jobs += Start-Job -ScriptBlock {
        param($tk)
        $h = @{ Authorization = "Bearer $tk" }
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/audit-chain?userId=1' -Headers $h -TimeoutSec 60 | Out-Null
        Invoke-RestMethod -Uri 'http://127.0.0.1:8081/api/v1/ai/ws/hermes/delegations?pageNum=1&pageSize=50' -Headers $h -TimeoutSec 60 | Out-Null
        $sw.Stop()
        $sw.ElapsedMilliseconds
    } -ArgumentList $leader.Authorization.Split(' ')[1]
}
$times = @()
foreach ($j in $jobs) { $times += (Receive-Job -Job $j -Wait) ; Remove-Job $j -Force }
$swAll.Stop()
$sorted = $times | Sort-Object
$p95 = $sorted[[int][Math]::Floor($sorted.Count * 0.95) - 1]
Write-Output ("LOAD: 20 jobs done in " + $swAll.ElapsedMilliseconds + "ms; per-job min=" + $sorted[0] + "ms median=" + $sorted[[int]($sorted.Count/2)] + "ms p95=" + $p95 + "ms")

Write-Output ''
Write-Output '=== 验收汇总 ==='
$routePass = [double]$reg.data.passRate
Write-Output ("ROUTE passRate=" + $routePass + " (门槛 0.90) " + $(if ($routePass -ge 0.90) {'PASS'} else {'FAIL'}))
Write-Output ("PERM intercepted=" + $permPass + "/5 " + $(if ($permPass -eq 5) {'PASS'} else {'FAIL'}))
Write-Output ("AUDIT-CHAIN rows=" + $chain.data.total + " " + $(if ($chain.data.total -gt 0) {'PASS'} else {'FAIL'}))
Write-Output ("LOAD p95=" + $p95 + "ms " + $(if ($p95 -lt 3000) {'PASS'} else {'WARN'}))
