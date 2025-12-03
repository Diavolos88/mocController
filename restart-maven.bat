@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17
set M2_HOME=C:\Program Files\Java\apache-maven-3.9.11

echo Останавливаю сервис на порту 8085...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8085 ^| findstr LISTENING') do (
    echo Останавливаю процесс PID: %%a
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 /nobreak >nul
echo.
echo Запускаю сервис через Maven...
REM Установите правильный пароль от БД здесь, если нужно
set DB_PASSWORD=q1w2e3r4
"%M2_HOME%\bin\mvn.cmd" spring-boot:run

