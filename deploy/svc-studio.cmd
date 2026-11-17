@echo off
rem svc-studio.cmd - Hermes Studio BFF on 127.0.0.1:8650 (Node 24, serves web/dist + proxies Hermes 8642)
rem Registered as scheduled task "DepSvc-svc-studio" (ONSTART + auto /Run), see restore-services.ps1
rem 日志重定向：计划任务无控制台会话，stdout 缓冲区满会阻塞 Node（同 2026-11-17 C3 svc-gateway 修复教训）
if not exist "%~dp0logs" mkdir "%~dp0logs"
set "NODE_EXE=C:\Program Files\nodejs\node.exe"
if not exist "%NODE_EXE%" for /f "delims=" %%i in ('where node') do set "NODE_EXE=%%i"
if not exist "%~dp0..\hermes-studio\web\dist\index.html" (
    echo [svc-studio] web/dist missing - run "npm run build" in hermes-studio first.
    exit /b 1
)
"%NODE_EXE%" "%~dp0..\hermes-studio\server\index.mjs" >> "%~dp0logs\studio-svc.log" 2>&1
