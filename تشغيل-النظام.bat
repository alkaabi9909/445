@echo off
chcp 65001 >nul
title نظام قانون - تشغيل النظام
cd /d "%~dp0"

set "JAVA_HOME=%USERPROFILE%\scoop\apps\openjdk21\current"
set "PATH=%JAVA_HOME%\bin;%USERPROFILE%\scoop\apps\maven\current\bin;%PATH%"

echo ================================================================
echo    نظام قانون - النظام المتكامل لإدارة مكاتب المحاماة
echo ================================================================
echo.
echo   [1] تشغيل بقاعدة بيانات مدمجة  (الأسرع - لا يحتاج SQL Server)
echo   [2] تشغيل بقاعدة SQL Server     (مصادقة ويندوز)
echo.
set /p choice=اختر (1 أو 2) ثم اضغط Enter:

if "%choice%"=="2" goto mssql

echo.
echo جارٍ التشغيل في وضع التطوير...
call mvn spring-boot:run
goto end

:mssql
echo.
echo جارٍ التشغيل بقاعدة SQL Server...
call mvn spring-boot:run -Dspring-boot.run.profiles=mssql

:end
echo.
echo توقف النظام.
pause
