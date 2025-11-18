# Установка PostgreSQL на Windows

## Вариант 1: Установка через официальный установщик (рекомендуется)

1. **Скачайте установщик:**
   - Перейдите на https://www.postgresql.org/download/windows/
   - Нажмите "Download the installer"
   - Выберите версию (рекомендуется последняя стабильная, например PostgreSQL 16)
   - Скачайте установщик для Windows x86-64

2. **Запустите установщик:**
   - Запустите скачанный файл (например, `postgresql-16.x-windows-x64.exe`)
   - Нажмите "Next" на экране приветствия

3. **Выберите компоненты:**
   - Оставьте все компоненты по умолчанию (включая PostgreSQL Server, pgAdmin, Command Line Tools)
   - Нажмите "Next"

4. **Выберите директорию установки:**
   - Оставьте по умолчанию или выберите свою
   - Нажмите "Next"

5. **Выберите директорию данных:**
   - Оставьте по умолчанию
   - Нажмите "Next"

6. **Установите пароль для пользователя postgres:**
   - **ВАЖНО:** Запомните этот пароль! Он понадобится для подключения к БД
   - Введите пароль (рекомендуется сложный, но запоминаемый)
   - Подтвердите пароль
   - Нажмите "Next"

7. **Выберите порт:**
   - Оставьте порт 5432 по умолчанию
   - Нажмите "Next"

8. **Выберите локаль:**
   - Оставьте по умолчанию (или выберите Russian, Russia)
   - Нажмите "Next"

9. **Завершите установку:**
   - Нажмите "Next" на экране готовности к установке
   - Дождитесь завершения установки
   - Снимите галочку "Launch Stack Builder" (не нужен)
   - Нажмите "Finish"

10. **Проверьте установку:**
    - Откройте PowerShell
    - Выполните: `psql --version`
    - Если команда не найдена, добавьте PostgreSQL в PATH:
      - Путь обычно: `C:\Program Files\PostgreSQL\16\bin`
      - Добавьте в системную переменную PATH

## Вариант 2: Установка через winget (если доступен)

```powershell
winget install PostgreSQL.PostgreSQL
```

## Проверка установки

После установки проверьте:

1. **Проверьте, что служба запущена:**
   ```powershell
   Get-Service -Name "*postgres*"
   ```

2. **Попробуйте подключиться:**
   ```powershell
   psql -U postgres
   ```
   - Введите пароль, который вы установили
   - Если подключение успешно, вы увидите приглашение `postgres=#`
   - Введите `\q` для выхода

## Создание базы данных для проекта

После установки создайте базу данных:

```powershell
# Подключитесь к PostgreSQL
psql -U postgres

# Создайте базу данных
CREATE DATABASE mockcontroller;

# Создайте пользователя (опционально, можно использовать postgres)
CREATE USER mockcontroller_user WITH PASSWORD 'your_password_here';

# Дайте права пользователю
GRANT ALL PRIVILEGES ON DATABASE mockcontroller TO mockcontroller_user;

# Выйдите
\q
```

## Настройка подключения в приложении

После установки PostgreSQL и создания БД, настройте `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mockcontroller
    username: postgres
    password: ваш_пароль
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
        connection:
          characterEncoding: UTF-8
          useUnicode: true
```

## Автоматическое создание таблиц

При первом запуске приложения Hibernate автоматически создаст необходимые таблицы:
- `stored_configs` - для хранения конфигураций
- `scheduled_config_updates` - для хранения запланированных обновлений

Таблицы создаются благодаря настройке `ddl-auto: update` в `application.yml`.

## Миграция с файлового хранения

Если у вас были данные в файлах `data/configs/`, они не будут автоматически перенесены в БД. 
Для миграции данных можно:
1. Использовать существующие REST API эндпоинты для повторной регистрации конфигов
2. Создать скрипт миграции (при необходимости)

