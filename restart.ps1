# Скрипт для перезапуска MockController сервиса

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$mvnPath = "C:\Program Files\Java\apache-maven-3.9.11\bin\mvn.cmd"

Write-Host "Останавливаю сервис..." -ForegroundColor Yellow

# Ищем процесс Java, который слушает порт 8080
$process = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | 
    Select-Object -ExpandProperty OwningProcess -ErrorAction SilentlyContinue

if ($process) {
    $javaProcess = Get-Process -Id $process -ErrorAction SilentlyContinue
    if ($javaProcess) {
        Write-Host "Найден процесс Java (PID: $($javaProcess.Id)), останавливаю..." -ForegroundColor Yellow
        Stop-Process -Id $javaProcess.Id -Force
        Start-Sleep -Seconds 2
        Write-Host "Процесс остановлен" -ForegroundColor Green
    }
} else {
    # Альтернативный способ: ищем по имени класса
    $javaProcesses = Get-Process java -ErrorAction SilentlyContinue
    foreach ($proc in $javaProcesses) {
        $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($proc.Id)").CommandLine
        if ($cmdLine -and $cmdLine -like "*MockcontrollerApplication*") {
            Write-Host "Найден процесс MockController (PID: $($proc.Id)), останавливаю..." -ForegroundColor Yellow
            Stop-Process -Id $proc.Id -Force
            Start-Sleep -Seconds 2
            Write-Host "Процесс остановлен" -ForegroundColor Green
            break
        }
    }
}

Write-Host "`nЗапускаю сервис заново..." -ForegroundColor Cyan
& $mvnPath spring-boot:run

