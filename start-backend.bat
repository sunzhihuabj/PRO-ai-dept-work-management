@echo off
chcp 65001 >nul
title dept-backend [8080]
cd /d "%~dp0backend"
"C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot\bin\java.exe" -jar target\dept-mgmt.jar >> backend-console.log 2>&1
pause
