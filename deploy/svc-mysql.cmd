@echo off
rem svc-mysql.cmd - dept_mgmt MySQL on 3307 (run via scheduled task for process persistence)
"D:\build-tools\mysql-8.0-dept\bin\mysqld.exe" --defaults-file="D:\build-tools\mysql-8.0-dept\my.ini" --console
