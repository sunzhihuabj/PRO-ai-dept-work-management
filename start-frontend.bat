@echo off
chcp 65001 >nul
title dept-frontend [5173]
cd /d "%~dp0frontend"
call npm run dev >> frontend-console.log 2>&1
pause
