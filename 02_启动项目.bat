@echo off
chcp 65001 >nul
title 项目一键启动 - 部门工作管理平台
echo ==============================================
echo  一键启动：后端(8080/api) + 前端(5173)
echo ==============================================
echo.

setlocal enableextensions
set ROOT=%~dp0

rem --- 1. 检查 node ---
where node >nul 2>nul
if %errorlevel% neq 0 (
  echo [错误] 未检测到 Node.js，请先安装 https://nodejs.org/ （LTS 即可）
  pause
  exit /b 1
)

rem --- 2. 检查 mvn ---
where mvn >nul 2>nul
if %errorlevel% neq 0 (
  echo [警告] 未检测到 Maven，后端将尝试使用 mvnw.cmd（如果项目内含 maven wrapper）
)

rem --- 3. 启动后端（新窗口）---
echo [1/2] 启动后端 Spring Boot...
start "后端 [Spring Boot 8080]" cmd /k "cd /d "%ROOT%backend" && (where mvn >nul 2>nul && mvn spring-boot:run || mvnw.cmd spring-boot:run)"

rem --- 4. 启动前端（新窗口）---
echo [2/2] 启动前端 Vite 开发服务器...
start "前端 [Vite 5173]" cmd /k "cd /d "%ROOT%frontend" && (if not exist node_modules call npm install) && npm run dev"

echo.
echo 启动完毕：
echo   后端接口： http://localhost:8080/api
echo   前端页面： http://localhost:5173
echo   API 文档： http://localhost:8080/api/swagger-ui.html
echo.
pause
