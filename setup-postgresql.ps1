# Скрипт для установки и настройки PostgreSQL для MockController

Write-Host "=== Установка PostgreSQL для MockController ===" -ForegroundColor Green
Write-Host ""

# Проверка наличия winget
$wingetAvailable = $false
try {
    $wingetVersion = winget --version 2>$null
    if ($wingetVersion) {
        $wingetAvailable = $true
        Write-Host "[OK] winget найден" -ForegroundColor Green
    }
} catch {
    Write-Host "[WARN] winget не найден" -ForegroundColor Yellow
}

# Вариант 1: Установка через winget
if ($wingetAvailable) {
    Write-Host ""
    Write-Host "Попытка установки через winget..." -ForegroundColor Cyan
    try {
        winget install PostgreSQL.PostgreSQL --accept-package-agreements --accept-source-agreements
        Write-Host "[OK] PostgreSQL установлен через winget" -ForegroundColor Green
    } catch {
        Write-Host "[ERROR] Ошибка при установке через winget" -ForegroundColor Red
        Write-Host "Попробуйте установить вручную по инструкции в POSTGRESQL_SETUP.md" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host ""
    Write-Host "winget недоступен. Установите PostgreSQL вручную:" -ForegroundColor Yellow
    Write-Host "1. Скачайте установщик с https://www.postgresql.org/download/windows/" -ForegroundColor Yellow
    Write-Host "2. Запустите установщик и следуйте инструкциям" -ForegroundColor Yellow
    Write-Host "3. Запомните пароль для пользователя postgres!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "После установки запустите этот скрипт снова для создания БД" -ForegroundColor Yellow
    $continue = Read-Host "Продолжить создание БД? (y/n)"
    if ($continue -ne "y") {
        exit 0
    }
}

# Проверка установки PostgreSQL
Write-Host ""
Write-Host "Проверка установки PostgreSQL..." -ForegroundColor Cyan

$psqlPath = $null
$possiblePaths = @(
    "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files\PostgreSQL\16\bin\psql.exe",
    "C:\Program Files\PostgreSQL\15\bin\psql.exe",
    "C:\Program Files\PostgreSQL\14\bin\psql.exe",
    "C:\Program Files (x86)\PostgreSQL\18\bin\psql.exe",
    "C:\Program Files (x86)\PostgreSQL\17\bin\psql.exe",
    "C:\Program Files (x86)\PostgreSQL\16\bin\psql.exe",
    "C:\Program Files (x86)\PostgreSQL\15\bin\psql.exe",
    "C:\Program Files (x86)\PostgreSQL\14\bin\psql.exe"
)

foreach ($path in $possiblePaths) {
    if (Test-Path $path) {
        $psqlPath = $path
        break
    }
}

# Проверка через PATH
if (-not $psqlPath) {
    try {
        $psqlVersion = & psql --version 2>$null
        if ($psqlVersion) {
            $psqlPath = "psql"
            Write-Host "[OK] PostgreSQL найден в PATH" -ForegroundColor Green
        }
    } catch {
        # Продолжаем поиск
    }
}

if (-not $psqlPath) {
    Write-Host "[ERROR] PostgreSQL не найден" -ForegroundColor Red
    Write-Host "Добавьте PostgreSQL в PATH или укажите путь вручную" -ForegroundColor Yellow
    Write-Host "Обычный путь: C:\Program Files\PostgreSQL\16\bin" -ForegroundColor Yellow
    exit 1
}

Write-Host "[OK] PostgreSQL найден: $psqlPath" -ForegroundColor Green

# Проверка службы
Write-Host ""
Write-Host "Проверка службы PostgreSQL..." -ForegroundColor Cyan
$service = Get-Service -Name "*postgres*" -ErrorAction SilentlyContinue
if ($service) {
    if ($service.Status -eq "Running") {
        Write-Host "[OK] Служба PostgreSQL запущена" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Служба PostgreSQL не запущена. Попытка запуска..." -ForegroundColor Yellow
        try {
            Start-Service -Name $service.Name
            Write-Host "[OK] Служба запущена" -ForegroundColor Green
        } catch {
            Write-Host "[ERROR] Не удалось запустить службу" -ForegroundColor Red
            Write-Host "Запустите службу вручную через Services (services.msc)" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "[WARN] Служба PostgreSQL не найдена" -ForegroundColor Yellow
}

# Создание базы данных
Write-Host ""
Write-Host "=== Создание базы данных ===" -ForegroundColor Green
Write-Host ""

$dbName = "mockcontroller"
$dbUser = "postgres"

# Проверка переменной окружения для пароля
if ($env:POSTGRES_PASSWORD) {
    Write-Host "[INFO] Используется пароль из переменной окружения POSTGRES_PASSWORD" -ForegroundColor Cyan
    $password = $env:POSTGRES_PASSWORD
} else {
    Write-Host "Введите пароль для пользователя ${dbUser}:" -ForegroundColor Cyan
    $securePassword = Read-Host -AsSecureString
    $password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    )
    # Очищаем SecureString из памяти
    $securePassword.Dispose()
}

# Проверка существования БД
Write-Host ""
Write-Host "Проверка существования базы данных..." -ForegroundColor Cyan

$env:PGPASSWORD = $password
$checkDbQuery = "SELECT 1 FROM pg_database WHERE datname='$dbName'"

if ($psqlPath -eq "psql") {
    $dbExists = & psql -U $dbUser -d postgres -t -c $checkDbQuery 2>$null
} else {
    $dbExists = & $psqlPath -U $dbUser -d postgres -t -c $checkDbQuery 2>$null
}

if ($dbExists -and $dbExists.Trim() -eq "1") {
    Write-Host "[OK] База данных '$dbName' уже существует" -ForegroundColor Green
    $recreate = Read-Host "Пересоздать базу данных? (y/n)"
    if ($recreate -eq "y") {
        Write-Host "Удаление существующей базы данных..." -ForegroundColor Yellow
        $dropQuery = "DROP DATABASE IF EXISTS $dbName;"
        if ($psqlPath -eq "psql") {
            & psql -U $dbUser -d postgres -c $dropQuery 2>&1 | Out-Null
        } else {
            & $psqlPath -U $dbUser -d postgres -c $dropQuery 2>&1 | Out-Null
        }
    } else {
        Write-Host "[OK] Используем существующую базу данных" -ForegroundColor Green
        if ($password) {
            $password = $null
            [System.GC]::Collect()
        }
        exit 0
    }
}

# Создание БД
Write-Host "Создание базы данных '$dbName'..." -ForegroundColor Cyan

$createQuery = "CREATE DATABASE $dbName;"

if ($psqlPath -eq "psql") {
    $result = & psql -U $dbUser -d postgres -c $createQuery 2>&1
} else {
    $result = & $psqlPath -U $dbUser -d postgres -c $createQuery 2>&1
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] База данных '$dbName' успешно создана" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Ошибка при создании базы данных:" -ForegroundColor Red
    Write-Host $result -ForegroundColor Red
    if ($password) {
        $password = $null
        [System.GC]::Collect()
    }
    exit 1
}

# Обновление application.yml
Write-Host ""
Write-Host "=== Обновление конфигурации ===" -ForegroundColor Green

$configFile = "src\main\resources\application.yml"
if (Test-Path $configFile) {
    Write-Host "Обновление $configFile..." -ForegroundColor Cyan
    
    $content = Get-Content $configFile -Raw
    # Обновляем пароль в YAML формате (ищем строку password: и заменяем значение)
    $content = $content -replace "(password:\s*)(.*)", "`$1$password"
    
    Set-Content -Path $configFile -Value $content -NoNewline
    Write-Host "[OK] Конфигурация обновлена" -ForegroundColor Green
    Write-Host "[WARN] ВАЖНО: Пароль сохранен в открытом виде в application.yml" -ForegroundColor Yellow
    Write-Host "   Для продакшена используйте переменные окружения или секреты" -ForegroundColor Yellow
} else {
    Write-Host "[WARN] Файл $configFile не найден" -ForegroundColor Yellow
}

# Очистка (очищаем пароль из памяти для безопасности)
if ($password) {
    $password = $null
    [System.GC]::Collect()
}

Write-Host ""
Write-Host "=== Готово! ===" -ForegroundColor Green
Write-Host "База данных '$dbName' создана и настроена" -ForegroundColor Green
Write-Host "Теперь можно запускать приложение" -ForegroundColor Green
