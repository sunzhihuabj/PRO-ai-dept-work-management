# ============================================================================
# restore-services.ps1  Restore all services after system reboot (Phase-1 kit)
# Usage: powershell -ExecutionPolicy Bypass -File restore-services.ps1
# Order: MySQL(3307) -> ai-gateway(8090) -> backend(8081) -> vite(5173) -> Docker Desktop -> Hermes(8642)
# ============================================================================
$ErrorActionPreference = 'Continue'
# Resolve repo root from script location (avoids non-ASCII literal path issues)
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$java = 'C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe'

Write-Host '[1/5] Starting MySQL (3307)...'
Start-Process 'D:\build-tools\mysql-8.0-dept\bin\mysqld.exe' -ArgumentList '--defaults-file=D:\build-tools\mysql-8.0-dept\my.ini', '--console' -WindowStyle Minimized
Start-Sleep -Seconds 6

Write-Host '[2/5] Starting ai-gateway (8090)...'
Start-Process $java -ArgumentList '-jar', "$root\ai-gateway\target\ai-gateway.jar" -WindowStyle Minimized

Write-Host '[3/5] Starting backend (8081)...'
Start-Process $java -ArgumentList '-jar', "$root\backend\target\dept-mgmt.jar" -WindowStyle Minimized

Write-Host '[4/5] Starting vite dev (5173)...'
Start-Process 'cmd' -ArgumentList '/c', 'npm run dev' -WorkingDirectory "$root\frontend" -WindowStyle Minimized

Write-Host '[5/5] Starting Docker Desktop (Dify host)...'
Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'

# Hermes Gateway resident (API Server on 127.0.0.1:8642) - scheduled task DepSvc-svc-hermes
Write-Host '[+ ] Registering/starting Hermes Gateway task (DepSvc-svc-hermes)...'
$hermesAction = New-ScheduledTaskAction -Execute "$root\deploy\svc-hermes.cmd"
$hermesTrigger = New-ScheduledTaskTrigger -AtStartup
Register-ScheduledTask -TaskName 'DepSvc-svc-hermes' -Action $hermesAction -Trigger $hermesTrigger -User $env:USERNAME -RunLevel Highest -Force | Out-Null
Start-ScheduledTask -TaskName 'DepSvc-svc-hermes'

# Hermes Studio 已剥离为独立项目（2026-11-18）：D:\2、开发源码\hermes-studio
# 其服务化由自身 deploy/restore-services.ps1 负责（计划任务 DepSvc-svc-studio）

Write-Host 'All services launched. Wait 60-90s, then verify:'
Write-Host '  Get-NetTCPConnection -LocalPort 3307,8081,8090,5173,8642 -State Listen'
