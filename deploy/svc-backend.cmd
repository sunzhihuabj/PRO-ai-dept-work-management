@echo off
rem svc-backend.cmd - dept-mgmt backend on 8081
"C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe" -jar "%~dp0..\backend\target\dept-mgmt.jar" >> "%~dp0logs\backend-svc.log" 2>&1
