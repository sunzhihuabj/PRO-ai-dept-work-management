@echo off
chcp 65001 >nul
title dept-miniapp H5 [5174]
cd /d "%~dp0miniapp"
if not exist node_modules call npm install --legacy-peer-deps
call npm run dev:h5 >> miniapp-console.log 2>&1
pause
