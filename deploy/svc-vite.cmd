@echo off
rem svc-vite.cmd - vite dev server on 5173
cd /d "%~dp0..\frontend"
call npm run dev
