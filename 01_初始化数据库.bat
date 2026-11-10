@echo off
chcp 65001 >nul
echo ==============================================
echo  初始化数据库（创建 dept_mgmt + 导入种子数据）
echo  仅首次部署或需要重置数据库时执行
echo ==============================================
echo.
set MYSQL_USER=root
set MYSQL_PWD=root
set DB_NAME=dept_mgmt

where mysql >nul 2>nul
if %errorlevel% neq 0 (
  echo [错误] 未找到 mysql 客户端，请先安装 MySQL 8 或将其加入 PATH
  pause
  exit /b 1
)

echo [1/2] 创建数据库与表结构...
mysql -u%MYSQL_USER% -p%MYSQL_PWD% < "%~dp0database\01_schema.sql"
if %errorlevel% neq 0 ( echo [错误] 建库建表失败 & pause & exit /b 1 )

echo [2/2] 导入种子数据...
mysql -u%MYSQL_USER% -p%MYSQL_PWD% %DB_NAME% < "%~dp0database\02_seed_data.sql"
if %errorlevel% neq 0 ( echo [错误] 种子数据导入失败 & pause & exit /b 1 )

echo.
echo 数据库初始化完成！
pause
