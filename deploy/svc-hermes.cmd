@echo off
REM Scheduled-task startup script for Hermes Gateway (API Server resident on 127.0.0.1:8642)
REM Registered as scheduled task "DepSvc-svc-hermes" (ONSTART + auto /Run), see restore-services.ps1
REM Env vars (API_SERVER_ENABLED/PORT/KEY) live in %LOCALAPPDATA%\hermes\.env

set HERMES_EXE=%LOCALAPPDATA%\hermes\bin\hermes.exe
if not exist "%HERMES_EXE%" (
    echo [svc-hermes] hermes.exe not found: %HERMES_EXE%
    exit /b 1
)

REM Foreground resident gateway; --external-supervisor declares scheduled-task owns lifecycle
"%HERMES_EXE%" gateway run --external-supervisor >> "%LOCALAPPDATA%\hermes\gateway-svc.log" 2>&1
