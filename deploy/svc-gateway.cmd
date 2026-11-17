@echo off
rem svc-gateway.cmd - ai-gateway on 8090
rem 日志重定向：计划任务无控制台会话，stdout 缓冲区满会阻塞 JVM（2026-11-17 C3 联调修复）
if not exist "%~dp0logs" mkdir "%~dp0logs"
"C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe" -jar "%~dp0..\ai-gateway\target\ai-gateway.jar" >> "%~dp0logs\gateway-svc.log" 2>&1
