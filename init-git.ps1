# Скрипт для инициализации Git репозитория

Write-Host "Инициализация Git репозитория..." -ForegroundColor Cyan

# Проверка наличия git
try {
    $gitVersion = git --version
    Write-Host "Git найден: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "Ошибка: Git не найден в PATH. Установите Git или добавьте его в PATH." -ForegroundColor Red
    exit 1
}

# Инициализация репозитория
Write-Host "`nИнициализация репозитория..." -ForegroundColor Yellow
git init

# Проверка наличия удаленного репозитория
$remoteExists = git remote get-url origin 2>$null
if ($remoteExists) {
    Write-Host "Удаленный репозиторий уже настроен: $remoteExists" -ForegroundColor Yellow
    $response = Read-Host "Заменить удаленный репозиторий? (y/n)"
    if ($response -eq "y" -or $response -eq "Y") {
        git remote remove origin
        git remote add origin git@github.com:Diavolos88/mocController.git
        Write-Host "Удаленный репозиторий обновлен" -ForegroundColor Green
    }
} else {
    git remote add origin git@github.com:Diavolos88/mocController.git
    Write-Host "Удаленный репозиторий добавлен" -ForegroundColor Green
}

# Добавление файлов
Write-Host "`nДобавление файлов..." -ForegroundColor Yellow
git add .

# Проверка статуса
Write-Host "`nСтатус репозитория:" -ForegroundColor Yellow
git status

# Проверка наличия коммитов
$hasCommits = git log --oneline 2>$null
if (-not $hasCommits) {
    Write-Host "`nСоздание первого коммита..." -ForegroundColor Yellow
    git commit -m "Initial commit: MockController service"
    Write-Host "Первый коммит создан" -ForegroundColor Green
} else {
    Write-Host "`nУже есть коммиты в репозитории" -ForegroundColor Yellow
    $lastCommit = git log --oneline -1
    Write-Host "Последний коммит: $lastCommit" -ForegroundColor Cyan
}

# Переименование ветки в main (если нужно)
$currentBranch = git branch --show-current
if ($currentBranch -ne "main") {
    Write-Host "`nПереименование ветки в main..." -ForegroundColor Yellow
    git branch -M main
    Write-Host "Ветка переименована в main" -ForegroundColor Green
}

# Вопрос об отправке в репозиторий
Write-Host "`nНастройка завершена!" -ForegroundColor Green
Write-Host "`nДля отправки в репозиторий выполните:" -ForegroundColor Cyan
Write-Host "  git push -u origin main" -ForegroundColor White

$push = Read-Host "`nОтправить в репозиторий сейчас? (y/n)"
if ($push -eq "y" -or $push -eq "Y") {
    Write-Host "`nОтправка в репозиторий..." -ForegroundColor Yellow
    git push -u origin main
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Успешно отправлено в репозиторий!" -ForegroundColor Green
    } else {
        Write-Host "Ошибка при отправке. Проверьте настройки SSH или используйте HTTPS." -ForegroundColor Red
        Write-Host "Для использования HTTPS выполните:" -ForegroundColor Yellow
        Write-Host "  git remote set-url origin https://github.com/Diavolos88/mocController.git" -ForegroundColor White
        Write-Host "  git push -u origin main" -ForegroundColor White
    }
}

Write-Host "`nГотово!" -ForegroundColor Green

