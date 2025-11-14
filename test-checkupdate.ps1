# Тестовый запрос для проверки обновлений (PowerShell)

$body = @{
    SystemName = "auth-mock"
    version = "v1"
    config = @{
        delays = @{
            loginDelayMs = "1000"
            tokenDelayMs = "500"
        }
        stringParams = @{
            mode = "normal"
            environment = "test"
        }
        loggingLv = "ERROR"
    }
} | ConvertTo-Json -Depth 10

Write-Host "Проверка обновлений для auth-mock версии v1..." -ForegroundColor Cyan
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/configs/checkUpdate" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

Write-Host "Ответ:" -ForegroundColor Green
$response | ConvertTo-Json

if ($response.needUpdate) {
    Write-Host "`nОбновление требуется! Текущая версия: $($response.currentVersion)" -ForegroundColor Yellow
} else {
    Write-Host "`nОбновление не требуется. Текущая версия: $($response.currentVersion)" -ForegroundColor Green
}

