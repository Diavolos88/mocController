# API Документация MockController

## Базовый URL

Базовый URL настраивается через параметр `app.api.base-url` в `application.yml` (по умолчанию `http://localhost:8085`).

```
http://localhost:8085
```

**Настройка через переменную окружения:**
```bash
export API_BASE_URL=http://your-host:8085
```

**Или в application.yml:**
```yaml
app:
  api:
    base-url: http://your-host:8085
```

## REST API Эндпоинты

### 1. Healthcheck сервиса

Проверка работоспособности сервиса.

**Эндпоинт:** `GET /service/healthcheck`

**Описание:**
- Простой эндпоинт для проверки доступности сервиса
- Возвращает статус 200 OK при успешном ответе

**Ответ:**
```
OK
```

**Примеры запросов:**

**cURL:**
```bash
curl -X GET http://localhost:8085/service/healthcheck
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8085/service/healthcheck" -Method Get
```

**HTTP:**
```http
GET /service/healthcheck HTTP/1.1
Host: localhost:8085
```

**Коды ответа:**
- `200 OK` - сервис работает

---

### 1.1. Healthcheck от заглушек

Регистрация healthcheck от заглушек для отслеживания их статуса.

**Эндпоинт:** `POST /api/healthcheck`

**Описание:**
- Заглушки отправляют healthcheck для регистрации своего статуса
- Сервис отслеживает время последнего healthcheck
- Заглушки считаются онлайн, если последний healthcheck был менее времени, указанного в `app.status.offline-threshold-seconds` (по умолчанию 300 секунд = 5 минут)
- Поддерживается несколько инстансов одной заглушки (через параметр `instanceId`)

**Параметры:**
- `systemName` (query, обязательное) - название заглушки
- `instanceId` (query, необязательное) - идентификатор инстанса (если не указан, используется "default")

**Ответ:**
```
OK
```

**Примеры запросов:**

**cURL:**
```bash
curl -X POST "http://localhost:8085/api/healthcheck?systemName=auth-mock&instanceId=instance-1"
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8085/api/healthcheck?systemName=auth-mock&instanceId=instance-1" -Method Post
```

**HTTP:**
```http
POST /api/healthcheck?systemName=auth-mock&instanceId=instance-1 HTTP/1.1
Host: localhost:8085
```

**Альтернативный эндпоинт:**
```bash
POST /api/healthcheck/{systemName}?instanceId=instance-1
```

**Коды ответа:**
- `200 OK` - healthcheck зарегистрирован

**Примечания:**
- Заглушки должны отправлять healthcheck периодически (рекомендуется каждые 1-2 минуты)
- Если инстанс (под) заглушки не отправлял healthcheck дольше времени, указанного в `app.status.offline-threshold-seconds` (по умолчанию 300 секунд = 5 минут), он считается офлайн
- Инстансы, не отправлявшие healthcheck дольше времени, указанного в `app.status.cleanup-threshold-seconds` (по умолчанию 3600 секунд = 1 час), автоматически удаляются из базы
- **Важно:** Удаляются именно инстансы (поды), а не заглушки (системы). Одна заглушка может иметь несколько инстансов, и если все инстансы удалятся, заглушка просто не будет отображаться на странице статусов, но сама система останется зарегистрированной
- Время офлайн и время очистки можно настроить в `application.yml` (см. раздел "Конфигурация приложения")

---

### 1.2. Проверка healthcheck группы

Проверка статуса всех заглушек в группе.

**Эндпоинт:** `GET /api/groups/healthcheck`

**Параметры:**
- `groupName` (query, обязательное) - название группы

**Описание:**
- Проверяет, есть ли хотя бы одна онлайн под для каждой заглушки в группе
- Учитываются только поды, которые отправляли healthcheck за последние `app.status.stats-window-seconds` (по умолчанию 300 секунд = 5 минут)
- Под считается онлайн, если последний healthcheck был менее `app.status.offline-threshold-seconds` назад

**Ответ при успехе (200 OK):**
```json
{
  "status": "OK",
  "message": "Все заглушки запущены",
  "groupName": "Auth системы"
}
```
- Возвращается, если для всех заглушек в группе есть хотя бы одна онлайн под

**Поля ответа (200):**
- `status` (string) - статус проверки: `OK`
- `message` (string) - описание результата: "Все заглушки запущены"
- `groupName` (string) - название группы

**Ответ при проблемах (201 Created):**
```json
{
  "status": "NOT_ALL_HEALTHY",
  "message": "Не все системы в группе имеют онлайн поды",
  "groupName": "Auth системы",
  "systems": [
    {
      "systemName": "auth",
      "hasOnlinePods": true,
      "onlineCount": 2,
      "offlineCount": 1,
      "totalCount": 3
    },
    {
      "systemName": "user",
      "hasOnlinePods": false,
      "onlineCount": 0,
      "offlineCount": 2,
      "totalCount": 2
    }
  ]
}
```

**Поля ответа (201):**
- `status` (string) - статус проверки: `NOT_ALL_HEALTHY`
- `message` (string) - описание результата
- `groupName` (string) - название группы
- `systems` (array) - массив информации о системах:
  - `systemName` (string) - название системы
  - `hasOnlinePods` (boolean) - есть ли хотя бы одна онлайн под
  - `onlineCount` (number) - количество онлайн подов
  - `offlineCount` (number) - количество офлайн подов
  - `totalCount` (number) - общее количество подов (учитываются только за последние `stats-window-seconds`)

**Примеры запросов:**

**cURL:**
```bash
curl -X GET "http://localhost:8085/api/groups/healthcheck?groupName=Auth%20системы"
```

**PowerShell:**
```powershell
$groupName = "Auth системы"
$uri = "http://localhost:8085/api/groups/healthcheck?groupName=$([System.Web.HttpUtility]::UrlEncode($groupName))"
Invoke-RestMethod -Uri $uri -Method Get
```

**HTTP:**
```http
GET /api/groups/healthcheck?groupName=Auth%20системы HTTP/1.1
Host: localhost:8085
```

**Коды ответа:**
- `200 OK` - все системы в группе имеют хотя бы одну онлайн под
- `201 Created` - не все системы имеют онлайн поды (возвращается детальная статистика)
- `400 Bad Request` - не указано название группы
- `404 Not Found` - группа не найдена
- `500 Internal Server Error` - внутренняя ошибка сервера

**Примечания:**
- Поиск группы выполняется без учета регистра (case-insensitive)
- Учитываются только поды, которые отправляли healthcheck за последние `app.status.stats-window-seconds`
- Если у заглушки нет подов (0 подов), она считается нездоровой

---

### 2. Проверка обновлений конфигурации

Проверяет, требуется ли заглушке обновление конфигурации.

**Эндпоинт:** `POST /api/configs/checkUpdate`

**Описание:**
- Заглушка отправляет свой текущий конфиг и версию
- Сервис сравнивает версию и конфиг с сохраненными
- Если конфига нет и версия 1 - сохраняется автоматически
- Если версия 1, но конфиг отличается от стартового - обновляется стартовый и версия повышается
- Если версия заглушки ниже сохраненной - возвращается `needUpdate: true`

**Запрос:**

```json
{
  "SystemName": "auth-mock",
  "version": "v1",
  "config": {
    "delays": {
      "loginDelayMs": "1000",
      "tokenDelayMs": "500"
    },
    "stringParams": {
      "mode": "normal",
      "environment": "test"
    },
    "loggingLv": "ERROR"
  }
}
```

**Поля запроса:**
- `SystemName` (string, обязательное) - название заглушки
- `version` (string, обязательное) - текущая версия конфига заглушки (например, "v1", "1", "v2")
- `config` (object, обязательное) - текущий конфиг заглушки

**Ответ:**

```json
{
  "needUpdate": false,
  "currentVersion": "v1"
}
```

**Поля ответа:**
- `needUpdate` (boolean) - требуется ли обновление
  - `false` - обновление не требуется
  - `true` - требуется обновление (версия заглушки ниже сохраненной)
- `currentVersion` (string) - текущая версия конфига в сервисе

**Примеры ответов:**

1. **Первая регистрация (конфига нет, версия 1):**
```json
{
  "needUpdate": false,
  "currentVersion": "v1"
}
```
Конфиг автоматически сохраняется как стартовый.

2. **Конфиг совпадает, версия 1:**
```json
{
  "needUpdate": false,
  "currentVersion": "v1"
}
```

3. **Конфиг изменился, версия 1:**
```json
{
  "needUpdate": false,
  "currentVersion": "v2"
```
Стартовый конфиг обновлен, версия повышена.

4. **Требуется обновление (версия заглушки ниже):**
```json
{
  "needUpdate": true,
  "currentVersion": "v3"
}
```

**Примеры запросов:**

**PowerShell:**
```powershell
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

$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/checkUpdate" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$response | ConvertTo-Json
```

**cURL:**
```bash
curl -X POST http://localhost:8085/api/configs/checkUpdate \
  -H "Content-Type: application/json" \
  -d '{
    "SystemName": "auth-mock",
    "version": "v1",
    "config": {
      "delays": {
        "loginDelayMs": "1000",
        "tokenDelayMs": "500"
      },
      "stringParams": {
        "mode": "normal",
        "environment": "test"
      },
      "loggingLv": "ERROR"
    }
  }'
```

**HTTP:**
```http
POST /api/configs/checkUpdate HTTP/1.1
Host: localhost:8085
Content-Type: application/json

{
  "SystemName": "auth-mock",
  "version": "v1",
  "config": {
    "delays": {
      "loginDelayMs": "1000",
      "tokenDelayMs": "500"
    },
    "stringParams": {
      "mode": "normal",
      "environment": "test"
    },
    "loggingLv": "ERROR"
  }
}
```

---

### 3. Загрузка конфигурации

Загружает конфигурацию от заглушки для синхронизации.

**Эндпоинт:** `POST /api/configs`

**Описание:**
- Заглушка отправляет свой конфиг
- Сервис сравнивает с сохраненным стартовым конфигом
- Если стартовый конфиг изменился - обновляется и версия повышается
- Если текущий конфиг отличается - возвращается статус `UPDATE_AVAILABLE`

**Запрос:**

```json
{
  "SystemName": "auth-mock",
  "config": {
    "delays": {
      "loginDelayMs": "1000",
      "tokenDelayMs": "500"
    },
    "stringParams": {
      "mode": "normal",
      "environment": "test"
    },
    "loggingLv": "ERROR"
  }
}
```

**Поля запроса:**
- `SystemName` (string, обязательное) - название заглушки
- `config` (object, обязательное) - конфиг заглушки

**Ответ:**

```json
{
  "status": "START_REGISTERED",
  "message": "Start config saved",
  "currentVersion": "v1"
}
```

**Поля ответа:**
- `status` (enum) - статус синхронизации:
  - `START_REGISTERED` - конфиг зарегистрирован впервые
  - `UPDATED_START_CONFIG` - стартовый конфиг обновлен
  - `UPDATE_AVAILABLE` - доступна новая версия конфига
  - `NO_CHANGES` - изменений нет
- `message` (string) - описание результата
- `currentVersion` (string) - текущая версия конфига в сервисе

**Примеры ответов:**

1. **Первая регистрация:**
```json
{
  "status": "START_REGISTERED",
  "message": "Start config saved",
  "currentVersion": "v1"
}
```

2. **Стартовый конфиг обновлен:**
```json
{
  "status": "UPDATED_START_CONFIG",
  "message": "Start config updated",
  "currentVersion": "v2"
}
```

3. **Доступна новая версия:**
```json
{
  "status": "UPDATE_AVAILABLE",
  "message": "New config version available",
  "currentVersion": "v3"
}
```

4. **Изменений нет:**
```json
{
  "status": "NO_CHANGES",
  "message": "No changes",
  "currentVersion": "v1"
}
```

**Примеры запросов:**

**PowerShell:**
```powershell
$body = @{
    SystemName = "auth-mock"
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

$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$response | ConvertTo-Json
```

**cURL:**
```bash
curl -X POST http://localhost:8085/api/configs \
  -H "Content-Type: application/json" \
  -d '{
    "SystemName": "auth-mock",
    "config": {
      "delays": {
        "loginDelayMs": "1000",
        "tokenDelayMs": "500"
      },
      "stringParams": {
        "mode": "normal",
        "environment": "test"
      },
      "loggingLv": "ERROR"
    }
  }'
```

---

### 4. Получение конфигурации

Получает конфигурацию заглушки по названию системы и опционально по версии.

**Эндпоинт:** `GET /api/configs/{systemName}`

**Параметры:**
- `systemName` (path, обязательное) - название заглушки
- `version` (query, необязательное) - версия конфига (например, "v1", "1", "v2"). Если не указана, возвращается текущая версия

**Описание:**
- Возвращает конфигурацию для указанной системы
- Если версия не указана - возвращается текущий конфиг
- Если указана текущая версия - возвращается текущий конфиг
- Если указана версия "v1" или "1" - возвращается стартовый конфиг
- Если указана несуществующая версия - возвращается текущий конфиг

**Ответ:**

```json
{
  "SystemName": "auth-mock",
  "version": "v2",
  "config": {
    "delays": {
      "loginDelayMs": "2000",
      "tokenDelayMs": "1000"
    },
    "stringParams": {
      "mode": "test",
      "environment": "production"
    },
    "loggingLv": "INFO"
  },
  "updatedAt": "2024-12-20T10:15:30Z"
}
```

**Поля ответа:**
- `SystemName` (string) - название системы
- `version` (string) - версия возвращенного конфига
- `config` (object) - конфигурация
- `updatedAt` (string) - время последнего обновления (ISO 8601)

**Примеры запросов:**

**cURL (текущая версия):**
```bash
curl -X GET http://localhost:8085/api/configs/auth-mock
```

**cURL (конкретная версия):**
```bash
curl -X GET "http://localhost:8085/api/configs/auth-mock?version=v1"
```

**PowerShell:**
```powershell
# Текущая версия
$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/auth-mock" `
    -Method Get

$response | ConvertTo-Json

# Конкретная версия
$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/auth-mock?version=v1" `
    -Method Get

$response | ConvertTo-Json
```

**Ошибки:**
- `404 Not Found` - конфиг с указанным названием системы не найден
- `500 Internal Server Error` - внутренняя ошибка сервера

**Примечания:**
- Версия может быть указана в форматах: "v1", "1", "v2", "2" и т.д.
- Если запрошена несуществующая версия, возвращается текущий конфиг
- Стартовый конфиг (версия 1) всегда доступен, если система зарегистрирована

---

### 5. Планирование отложенного обновления конфигурации

Планирует обновление конфигурации на указанное время в будущем.

**Эндпоинт:** `POST /api/configs/schedule`

**Описание:**
- Позволяет запланировать обновление конфигурации на определенное время
- Обновление будет применено автоматически в указанное время
- Можно указать комментарий для описания изменения
- Поддерживается несколько запланированных обновлений для одной системы

**Запрос:**

```json
{
  "SystemName": "auth-mock",
  "scheduledTime": "14:30:00 25-12-2024",
  "config": {
    "delays": {
      "loginDelayMs": "2000",
      "tokenDelayMs": "1000"
    },
    "stringParams": {
      "mode": "test",
      "environment": "production"
    },
    "loggingLv": "INFO"
  },
  "comment": "Увеличение задержек для тестирования"
}
```

**Поля запроса:**
- `SystemName` (string, обязательное) - название заглушки
- `scheduledTime` (string, обязательное) - дата и время в формате `HH:mm:ss dd-MM-yyyy` (например, "14:30:00 25-12-2024")
- `config` (object, обязательное) - конфигурация, которая будет применена
- `comment` (string, необязательное) - комментарий к запланированному обновлению. При выполнении сценариев автоматически формируется в формате: "Сценарий: {название}|Комментарий ступени: {комментарий шага}"

**Ответ:**

```json
{
  "success": true,
  "message": "Обновление успешно запланировано",
  "updateId": "550e8400-e29b-41d4-a716-446655440000",
  "scheduledTime": "14:30:00 25-12-2024"
}
```

**Поля ответа:**
- `success` (boolean) - успешно ли запланировано обновление
- `message` (string) - сообщение о результате
- `updateId` (string) - уникальный идентификатор запланированного обновления
- `scheduledTime` (string) - запланированное время обновления

**Примеры запросов:**

**cURL:**
```bash
curl -X POST http://localhost:8085/api/configs/schedule \
  -H "Content-Type: application/json" \
  -d '{
    "SystemName": "auth-mock",
    "scheduledTime": "14:30:00 25-12-2024",
    "config": {
      "delays": {
        "loginDelayMs": "2000",
        "tokenDelayMs": "1000"
      },
      "stringParams": {
        "mode": "test"
      },
      "loggingLv": "INFO"
    },
    "comment": "Увеличение задержек"
  }'
```

**PowerShell:**
```powershell
$body = @{
    SystemName = "auth-mock"
    scheduledTime = "14:30:00 25-12-2024"
    config = @{
        delays = @{
            loginDelayMs = "2000"
            tokenDelayMs = "1000"
        }
        stringParams = @{
            mode = "test"
        }
        loggingLv = "INFO"
    }
    comment = "Увеличение задержек"
} | ConvertTo-Json -Depth 10

$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/schedule" `
    -Method Post `
    -ContentType "application/json" `
    -Body $body

$response | ConvertTo-Json
```

**Ошибки:**
- `400 Bad Request` - неверный формат даты или дата в прошлом
- `500 Internal Server Error` - внутренняя ошибка сервера

---

### 6. Получение списка запланированных обновлений

Возвращает список всех запланированных обновлений для указанной системы.

**Эндпоинт:** `GET /api/configs/{systemName}/scheduled`

**Параметры:**
- `systemName` (path) - название заглушки

**Описание:**
- Возвращает все запланированные обновления для указанной системы
- Обновления отсортированы по времени выполнения (от ближайших к дальним)

**Ответ:**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "systemName": "auth-mock",
    "scheduledTime": "14:30:00 25-12-2024",
    "comment": "Увеличение задержек",
    "createdAt": "10:15:00 20-12-2024"
  },
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "systemName": "auth-mock",
    "scheduledTime": "18:00:00 25-12-2024",
    "comment": null,
    "createdAt": "11:20:00 20-12-2024"
  }
]
```

**Поля ответа:**
- `id` (string) - уникальный идентификатор обновления
- `systemName` (string) - название системы
- `scheduledTime` (string) - запланированное время обновления
- `comment` (string, nullable) - комментарий к обновлению. При выполнении сценариев автоматически формируется в формате: "Сценарий: {название}|Комментарий ступени: {комментарий шага}". Формат позволяет отображать название сценария и комментарий шага отдельно в веб-интерфейсе.
- `createdAt` (string) - время создания запланированного обновления

**Примеры запросов:**

**cURL:**
```bash
curl -X GET http://localhost:8085/api/configs/auth-mock/scheduled
```

**PowerShell:**
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/auth-mock/scheduled" `
    -Method Get

$response | ConvertTo-Json
```

---

### 6. Отмена запланированного обновления

Отменяет запланированное обновление по его идентификатору.

**Эндпоинт:** `DELETE /api/configs/scheduled/{updateId}`

**Параметры:**
- `updateId` (path) - уникальный идентификатор запланированного обновления

**Описание:**
- Отменяет указанное запланированное обновление
- Обновление удаляется из списка и не будет выполнено

**Ответ:**

```json
{
  "success": true,
  "message": "Запланированное обновление отменено",
  "updateId": "550e8400-e29b-41d4-a716-446655440000",
  "scheduledTime": null
}
```

**Поля ответа:**
- `success` (boolean) - успешно ли отменено обновление
- `message` (string) - сообщение о результате
- `updateId` (string) - идентификатор отмененного обновления
- `scheduledTime` (string, nullable) - всегда null для отмененных обновлений

**Примеры запросов:**

**cURL:**
```bash
curl -X DELETE http://localhost:8085/api/configs/scheduled/550e8400-e29b-41d4-a716-446655440000
```

**PowerShell:**
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8085/api/configs/scheduled/550e8400-e29b-41d4-a716-446655440000" `
    -Method Delete

$response | ConvertTo-Json
```

**Ошибки:**
- `404 Not Found` - обновление с указанным ID не найдено
- `500 Internal Server Error` - внутренняя ошибка сервера

---

## Веб-интерфейс (Web UI)

### 1. Страница статусов заглушек

Отображает статусы всех заглушек, сгруппированных по системам.

**Эндпоинт:** `GET /status`

**Описание:**
- Показывает все зарегистрированные заглушки с их статусами
- Группирует заглушки по системам
- Отображает количество онлайн и офлайн инстансов
- Показывает время последнего успешного healthcheck
- Статус определяется автоматически: заглушка считается онлайн, если последний healthcheck был менее времени, указанного в `app.status.offline-threshold-seconds` (по умолчанию 300 секунд = 5 минут)
- Страница автоматически обновляется каждые 30 секунд

**Информация на карточке системы:**
- Название системы (ссылка на детальную страницу)
- Статус: Online (все инстансы онлайн), Offline (все офлайн), Mixed (часть онлайн, часть офлайн)
- Количество онлайн инстансов
- Количество офлайн инстансов
- Общее количество инстансов
- Время последнего healthcheck

**Пример:**
```
http://localhost:8085/status
```

**Детальная страница системы:**
- `GET /status/{systemName}` - показывает все инстансы конкретной системы
- Отображает список всех инстансов с их статусами и временем последнего healthcheck

---

### 2. Главная страница (Заглушки)

Отображает список всех зарегистрированных заглушек, сгруппированных по группам.

**Эндпоинт:** `GET /`

**Параметры:**
- `system` (query, необязательное) - название группы для фильтрации

**Описание:**
- Показывает все зарегистрированные заглушки, сгруппированные по группам из базы данных
- Отображает только группы, созданные через интерфейс управления группами
- Каждая вкладка соответствует группе из базы данных
- Во вкладке группы отображаются только те заглушки, которые добавлены в эту группу
- Показывает счетчик заглушек для каждой группы
- Позволяет фильтровать по группе через вкладки
- Отображает последнее время обновления для каждой заглушки
- Каждая заглушка - карточка со ссылкой на страницу редактирования конфига
- **Удаление конфига:** Кнопка "Удалить" в правом нижнем углу каждой карточки
  - При нажатии появляется модальное окно с подтверждением
  - После подтверждения конфиг удаляется безвозвратно
  - Все запланированные обновления для этой системы также удаляются

**Примечания:**
- Группировка происходит только по группам из базы данных
- Автоматическая группировка по префиксу имени заглушки отключена
- Если группа пуста (не содержит заглушек), она не отображается
- Для отображения заглушек в группе их нужно добавить через интерфейс управления группами

**Пример:**
```
http://localhost:8085
```

---

### 3. Страница конфигурации

Отображает и позволяет редактировать конфигурацию заглушки.

**Эндпоинт:** `GET /configs/{systemName}`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)

**Описание:**
- Отображает текущую версию конфига
- Показывает все параметры сгруппированные по типам:
  - `delays` - целочисленные значения задержек
  - `stringParams` - строковые параметры
  - `loggingLv` - уровень логирования (ERROR, WARN, INFO, DEBUG)
- Рядом с каждым параметром показывается стартовое значение
- Можно кликнуть на стартовое значение для автоматической подстановки
- Кнопка "Сохранить изменения" - обновляет конфиг
- Кнопка "Откатить к стартовому" - возвращает конфиг к стартовому состоянию
- Кнопка "Отложенное сохранение" - позволяет запланировать обновление на определенное время с комментарием
- Отображает список всех запланированных обновлений с возможностью отмены

**Пример:**
```
http://localhost:8085/configs/auth-mock
```

---

### 4. Обновление конфигурации

Обновляет конфигурацию через веб-форму.

**Эндпоинт:** `POST /configs/{systemName}?action=update`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)
- `action=update` (query) - действие обновления

**Форма:**
- `delay_{key}` - значения задержек (например, `delay_loginDelayMs=2000`)
- `string_{key}` - строковые параметры (например, `string_mode=test`)
- `loggingLv` - уровень логирования (ERROR, WARN, INFO, DEBUG)

**Описание:**
- Принимает данные из формы
- Обновляет конфиг и увеличивает версию на 1
- Перенаправляет на страницу конфига

**Пример:**
```
POST /configs/auth-mock?action=update
Content-Type: application/x-www-form-urlencoded

delay_loginDelayMs=2000&delay_tokenDelayMs=1000&string_mode=test&loggingLv=INFO
```

---

### 5. Откат к стартовому конфигу

Возвращает конфигурацию к стартовому состоянию.

**Эндпоинт:** `POST /configs/{systemName}?action=revert`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)
- `action=revert` (query) - действие отката

**Описание:**
- Устанавливает текущий конфиг равным стартовому
- Увеличивает версию на 1
- Перенаправляет на страницу конфига

**Пример:**
```
POST /configs/auth-mock?action=revert
```

---

### 6. Планирование отложенного обновления (Web UI)

Планирует обновление конфигурации через веб-форму.

**Эндпоинт:** `POST /configs/{systemName}?action=schedule`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)
- `action=schedule` (query) - действие планирования

**Форма:**
- `scheduledDateTime` (string, обязательное) - дата и время в формате `HH:mm:ss dd-MM-yyyy` (например, "14:30:00 25-12-2024")
- `scheduleComment` (string, необязательное) - комментарий к обновлению
- `delay_{key}` - значения задержек (например, `delay_loginDelayMs=2000`)
- `string_{key}` - строковые параметры (например, `string_mode=test`)
- `loggingLv` - уровень логирования (ERROR, WARN, INFO, DEBUG)

**Описание:**
- Принимает данные из формы
- Планирует обновление конфига на указанное время
- Перенаправляет на страницу конфига

**Пример:**
```
POST /configs/auth-mock?action=schedule
Content-Type: application/x-www-form-urlencoded

scheduledDateTime=14:30:00 25-12-2024&scheduleComment=Увеличение задержек&delay_loginDelayMs=2000&delay_tokenDelayMs=1000&string_mode=test&loggingLv=INFO
```

---

### 7. Отмена запланированного обновления (Web UI)

Отменяет запланированное обновление через веб-интерфейс.

**Эндпоинт:** `POST /configs/{systemName}?action=cancelSchedule`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)
- `action=cancelSchedule` (query) - действие отмены
- `updateId` (form parameter) - идентификатор запланированного обновления

**Описание:**
- Отменяет указанное запланированное обновление
- Перенаправляет на страницу конфига

**Пример:**
```
POST /configs/auth-mock?action=cancelSchedule
Content-Type: application/x-www-form-urlencoded

updateId=550e8400-e29b-41d4-a716-446655440000
```

---

### 8. Удаление конфигурации (Web UI)

Удаляет конфигурацию заглушки через веб-интерфейс.

**Эндпоинт:** `POST /configs/{systemName}?action=delete`

**Параметры:**
- `systemName` (path) - название заглушки (URL-encoded)
- `action=delete` (query) - действие удаления

**Описание:**
- Удаляет конфигурацию из памяти и с диска
- Автоматически удаляет все запланированные обновления для этой системы
- Перенаправляет на главную страницу с сообщением об успехе или ошибке
- Требует подтверждения через модальное окно в веб-интерфейсе

**Доступ:**
- Кнопка "Удалить" находится на главной странице в правом нижнем углу карточки каждой заглушки
- При нажатии появляется модальное окно с подтверждением
- После подтверждения конфиг удаляется безвозвратно

**Пример:**
```
POST /configs/auth-mock?action=delete
```

**Примечания:**
- Операция необратима - конфиг удаляется полностью
- Все запланированные обновления для этой системы также удаляются
- При ошибке пользователь перенаправляется на главную страницу с сообщением об ошибке

---

## Структура конфигурации

Конфигурация заглушки представляет собой JSON объект со следующими полями:

- **`delays`** (object, необязательное) - задержки в миллисекундах. Значения должны быть целыми неотрицательными числами.
- **`intParams`** (object, необязательное) - целочисленные параметры. Значения должны быть целыми числами (могут быть отрицательными).
- **`booleanVariables`** (object, необязательное) - булевы переменные. Значения должны быть `true` или `false` (как булевы значения или строки "true"/"false").
- **`stringParams`** (object, необязательное) - строковые параметры. Значения могут быть любыми строками.
- **`loggingLv`** (string, необязательное) - уровень логирования. Возможные значения: `ERROR`, `WARN`, `INFO`, `DEBUG`.

**Пример полной конфигурации:**

```json
{
  "delays": {
    "loginDelayMs": 1000,
    "tokenDelayMs": 500
  },
  "intParams": {
    "maxRetries": 3,
    "timeout": -1
  },
  "booleanVariables": {
    "isHealthTrue": true,
    "isDataAvailable": false
  },
  "stringParams": {
    "mode": "normal",
    "environment": "test"
  },
  "loggingLv": "INFO"
}
```

**Валидация:**
- `delays`: значения должны быть целыми неотрицательными числами
- `intParams`: значения должны быть целыми числами (могут быть отрицательными)
- `booleanVariables`: значения должны быть `true` или `false` (принимаются как булевы значения или строки "true"/"false")
- `stringParams`: значения могут быть любыми строками
- `loggingLv`: должен быть одним из значений: `ERROR`, `WARN`, `INFO`, `DEBUG`

## Валидация конфигурации

Валидация выполняется как на клиенте (JavaScript), так и на сервере (Java):

- **`delays`**: значения должны быть целыми неотрицательными числами (≥ 0)
- **`intParams`**: значения должны быть целыми числами (могут быть отрицательными)
- **`booleanVariables`**: значения должны быть `true` или `false` (принимаются как булевы значения или строки "true"/"false")
- **`stringParams`**: значения могут быть любыми строками
- **`loggingLv`**: должен быть одним из значений: `ERROR`, `WARN`, `INFO`, `DEBUG`

При нарушении валидации возвращается ошибка с описанием проблемы.

---

## Структура конфигурации (устаревшая секция)

Конфигурация заглушки представляет собой JSON объект со следующей структурой:

```json
{
  "delays": {
    "loginDelayMs": 1000,
    "tokenDelayMs": 500,
    "refreshDelayMs": 300
  },
  "stringParams": {
    "mode": "normal",
    "environment": "test",
    "region": "us-east"
  },
  "intParams": {
    "maxRetries": 3,
    "timeoutSeconds": 30,
    "batchSize": 100
  },
  "loggingLv": "ERROR"
}
```

**Поля:**
- `delays` (object) - задержки в миллисекундах (целочисленные неотрицательные значения, обязательны для валидации)
- `stringParams` (object) - строковые параметры (любые строковые значения)
- `intParams` (object) - целочисленные параметры (любые целые числа, могут быть отрицательными)
- `loggingLv` (string) - уровень логирования, возможные значения:
  - `ERROR`
  - `WARN`
  - `INFO`
  - `DEBUG`

**Валидация:**
- Все значения в `delays` должны быть целыми неотрицательными числами (≥ 0)
- Значения в `stringParams` могут быть любыми строками
- Значения в `intParams` должны быть целыми числами (могут быть отрицательными)
- Значения в `booleanVariables` должны быть `true` или `false` (принимаются как булевы значения или строки "true"/"false")
- Валидация выполняется как на клиенте (JavaScript), так и на сервере (Java)
- При нарушении валидации возвращается ошибка с описанием проблемы

---

## Управление версиями

- Версия хранится в поле `version` объекта `StoredConfig` (не в самом конфиге)
- Версия начинается с 1 для первой регистрации
- Версия увеличивается на 1 при каждом обновлении через UI или при изменении стартового конфига
- Формат версии: `v1`, `v2`, `v3`, и т.д.

**Логика версионирования:**
1. Первая регистрация → версия 1
2. Обновление через UI → версия +1
3. Откат к стартовому → версия +1
4. Изменение стартового конфига (через checkUpdate) → версия +1

---

## Коды ошибок

Все эндпоинты возвращают HTTP 200 OK при успешном выполнении.

При ошибках:
- **400 Bad Request** - неверный формат запроса
- **404 Not Found** - конфиг не найден (для веб-интерфейса)
- **500 Internal Server Error** - внутренняя ошибка сервера

---

## Примеры использования

### Сценарий 1: Первая регистрация заглушки

1. Заглушка отправляет `checkUpdate` с версией 1:
```json
POST /api/configs/checkUpdate
{
  "SystemName": "auth-mock",
  "version": "v1",
  "config": { ... }
}
```

2. Сервис отвечает:
```json
{
  "needUpdate": false,
  "currentVersion": "v1"
}
```

3. Конфиг автоматически сохраняется.

---

### Сценарий 2: Обновление конфига через UI

1. Открыть страницу конфига: `http://localhost:8085/configs/auth-mock`
2. Изменить значения параметров
3. Нажать "Сохранить изменения"
4. Версия увеличивается на 1

---

### Сценарий 3: Проверка обновлений

1. Заглушка отправляет `checkUpdate` с версией 1, но конфиг изменился:
```json
POST /api/configs/checkUpdate
{
  "SystemName": "auth-mock",
  "version": "v1",
  "config": { ... новый конфиг ... }
}
```

2. Сервис отвечает:
```json
{
  "needUpdate": false,
  "currentVersion": "v2"
}
```

3. Стартовый конфиг обновлен, версия повышена.

---

### Сценарий 4: Требуется обновление

1. Заглушка отправляет `checkUpdate` с версией 2, а в сервисе версия 3:
```json
POST /api/configs/checkUpdate
{
  "SystemName": "auth-mock",
  "version": "v2",
  "config": { ... }
}
```

2. Сервис отвечает:
```json
{
  "needUpdate": true,
  "currentVersion": "v3"
}
```

3. Заглушка должна запросить новый конфиг (эндпоинт будет реализован позже).

---

## Примечания

- Все названия заглушек (`SystemName`) автоматически санитизируются (специальные символы заменяются на `_`)
- **Хранение данных**: Конфиги сохраняются в PostgreSQL (таблицы `stored_configs` и `scheduled_config_updates`)
- Версия хранится отдельно от конфига в поле `version` объекта `StoredConfig`
- Конфиг не содержит поля "Config version" - версия управляется только сервисом
- Таблицы создаются автоматически при первом запуске приложения (Hibernate `ddl-auto: update`)

## База данных

### Структура таблиц

**stored_configs:**
- `system_name` (VARCHAR, PRIMARY KEY) - название системы
- `start_config` (TEXT) - стартовый конфиг в формате JSON
- `current_config` (TEXT) - текущий конфиг в формате JSON
- `updated_at` (TIMESTAMP) - время последнего обновления
- `version` (INTEGER) - версия конфига

**scheduled_config_updates:**
- `id` (VARCHAR, PRIMARY KEY) - UUID обновления
- `system_name` (VARCHAR) - название системы
- `new_config` (TEXT) - новый конфиг в формате JSON
- `scheduled_time` (TIMESTAMP) - запланированное время
- `created_at` (TIMESTAMP) - время создания
- `comment` (VARCHAR) - комментарий

### Настройка подключения

Подключение к БД настраивается в `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mockcontroller
    username: postgres
    password: ваш_пароль
  jpa:
    hibernate:
      ddl-auto: update
```

Подробнее об установке PostgreSQL: [POSTGRESQL_SETUP.md](../../POSTGRESQL_SETUP.md)

---

## Шаблоны конфигураций (Templates)

### 8. Получение списка шаблонов

Возвращает список всех шаблонов или шаблонов для конкретной системы.

**Эндпоинт:** `GET /api/templates`

**Параметры:**
- `systemName` (query, необязательное) - фильтр по названию системы

**Ответ:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Долгий отклик",
    "systemName": "auth-mock",
    "config": {
      "delays": {
        "loginDelayMs": "5000"
      },
      "loggingLv": "ERROR"
    },
    "description": "Шаблон для тестирования высокой нагрузки",
    "createdAt": "2024-12-20T10:15:30Z"
  }
]
```

---

### 9. Создание шаблона

Создает новый шаблон конфигурации для заглушки.

**Эндпоинт:** `POST /api/templates`

**Запрос:**
```json
{
  "name": "Долгий отклик",
  "systemName": "auth-mock",
  "config": {
    "delays": {
      "loginDelayMs": "5000"
    },
    "loggingLv": "ERROR"
  },
  "description": "Шаблон для тестирования высокой нагрузки"
}
```

**Ответ:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Долгий отклик",
  "systemName": "auth-mock",
  "config": { ... },
  "description": "Шаблон для тестирования высокой нагрузки",
  "createdAt": "2024-12-20T10:15:30Z"
}
```

---

### 10. Применение шаблона

Применяет шаблон к заглушке (обновляет конфиг).

**Эндпоинт:** `POST /api/templates/{id}/apply`

**Параметры:**
- `id` (path) - идентификатор шаблона
- `systemName` (query) - название заглушки

**Ответ:** `200 OK` (без тела)

---

## Сценарии (Scenarios)

### 11. Получение списка сценариев

Возвращает список всех сценариев.

**Эндпоинт:** `GET /api/scenarios`

**Ответ:**
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Сценарий нагрузочного теста",
    "description": "Последовательное применение шаблонов",
    "steps": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440002",
        "templateId": "550e8400-e29b-41d4-a716-446655440000",
        "template": { ... },
        "stepOrder": 1,
        "delayMs": 0,
        "scheduledTime": null
      }
    ],
    "createdAt": "2024-12-20T10:15:30Z"
  }
]
```

**Поля ответа:**
- `id` (string) - уникальный идентификатор сценария
- `groupId` (string) - ID группы систем
- `name` (string) - название сценария
- `description` (string, nullable) - описание
- `steps` (array) - список шагов сценария
- `createdAt` (string) - время создания (ISO 8601)

---

### 12. Создание сценария

Создает новый сценарий с шагами.

**Эндпоинт:** `POST /api/scenarios`

**Запрос:**
```json
{
  "groupId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Сценарий нагрузочного теста",
  "description": "Последовательное применение шаблонов",
  "steps": [
    {
      "templateId": "550e8400-e29b-41d4-a716-446655440000",
      "delayMs": 0,
      "scheduledTime": null
    },
    {
      "templateId": "550e8400-e29b-41d4-a716-446655440001",
      "delayMs": 5000,
      "scheduledTime": null
    }
  ]
}
```

**Поля запроса:**
- `groupId` (string, обязательное) - ID группы систем, к которой относится сценарий
- `name` (string, обязательное) - название сценария
- `description` (string, необязательное) - описание
- `steps` (array, обязательное) - список шагов:
  - `templateId` (string, обязательное) - ID шаблона
  - `delayMs` (number) - задержка перед применением в миллисекундах
  - `scheduledTime` (string, необязательное) - конкретное время в формате "HH:mm:ss dd-MM-yyyy"

**Важно:**
- Комбинация `groupId` + `name` должна быть уникальной
- Группа определяет, какие системы и шаблоны доступны для сценария

**Ответ:**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "name": "Сценарий нагрузочного теста",
  "description": "Последовательное применение шаблонов",
  "steps": [ ... ],
  "createdAt": "2024-12-20T10:15:30Z"
}
```

---

### 13. Выполнение сценария по ID

Выполняет сценарий, применяя все шаги по временной линии.

**Эндпоинт:** `POST /api/scenarios/{id}/execute`

**Параметры:**
- `id` (path) - идентификатор сценария

**Описание:**
- Применяет все шаги сценария последовательно
- Если указан `delayMs` - применяет с задержкой относительно текущего времени
- Если указан `scheduledTime` - извлекает относительное время (HH:mm) и применяет относительно текущего времени
- Каждый шаг создает запланированное обновление через ScheduledConfigService
- Комментарий к запланированному обновлению автоматически формируется в формате: "Сценарий: {название}|Комментарий ступени: {комментарий шага}"

**Ответ:** `200 OK` (без тела)

---

### 13.1. Выполнение сценария по группе и названию

Выполняет сценарий по группе и названию с указанным временем начала.

**Эндпоинт:** `GET /api/scenarios/execute`

**Параметры запроса:**
- `group` (query, обязательное) - ID группы (UUID) или название группы
- `name` (query, обязательное) - название сценария
- `startTime` (query, обязательное) - время начала в формате `HH:mm:ss dd-MM-yyyy` (например, "14:30:00 25-12-2024")

**Описание:**
- Находит сценарий по комбинации группы и названия
- Применяет все шаги сценария относительно указанного времени начала
- Если у шага указан `scheduledTime` - извлекает относительное время (HH:mm) и вычисляет абсолютное время как `startTime + относительное время`
- Если у шага указан `delayMs` - вычисляет абсолютное время как `startTime + delayMs`
- Каждый шаг создает запланированное обновление через ScheduledConfigService
- Время начала не может быть в прошлом

**Ответ:**
```json
"Сценарий 'Название сценария' успешно запланирован на 14:30:00 25-12-2024"
```

**Ошибки:**
- `400 Bad Request` - неверный формат времени или время в прошлом, группа не найдена
- `404 Not Found` - сценарий с указанной группой и названием не найден
- `500 Internal Server Error` - внутренняя ошибка сервера

**Примеры запросов:**

**cURL:**
```bash
curl -X GET "http://localhost:8085/api/scenarios/execute?group=550e8400-e29b-41d4-a716-446655440000&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024"
```

**PowerShell:**
```powershell
$group = "550e8400-e29b-41d4-a716-446655440000"
$name = "Сценарий теста"
$startTime = "14:30:00 25-12-2024"

$uri = "http://localhost:8085/api/scenarios/execute?group=$([System.Web.HttpUtility]::UrlEncode($group))&name=$([System.Web.HttpUtility]::UrlEncode($name))&startTime=$([System.Web.HttpUtility]::UrlEncode($startTime))"

Invoke-RestMethod -Uri $uri -Method Get
```

**Пример с названием группы:**
```bash
curl -X GET "http://localhost:8085/api/scenarios/execute?group=Auth%20системы&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024"
```

**Примечания:**
- Параметр `group` может быть как UUID группы, так и названием группы
- Если группа указана по названию, происходит поиск группы по названию (без учета регистра)
- Все временные точки в сценарии рассчитываются относительно `startTime`
- Если вычисленное время шага оказывается в прошлом, оно автоматически устанавливается на 1 секунду в будущем

---

### 14. Удаление сценария

Удаляет сценарий и все его шаги.

**Эндпоинт:** `DELETE /api/scenarios/{id}`

**Параметры:**
- `id` (path) - идентификатор сценария

**Ответ:** `200 OK` (без тела)

**Пример:**
```bash
curl -X DELETE http://localhost:8085/api/scenarios/660e8400-e29b-41d4-a716-446655440001
```

---

### 15. Удаление шаблона

Удаляет шаблон конфигурации.

**Эндпоинт:** `DELETE /api/templates/{id}`

**Параметры:**
- `id` (path) - идентификатор шаблона

**Ответ:** `200 OK` (без тела)

**Пример:**
```bash
curl -X DELETE http://localhost:8085/api/templates/550e8400-e29b-41d4-a716-446655440000
```

---

## Пример интеграции

Пример реализации заглушки с полной интеграцией к MockController:
- [MocConnectToMockController](https://github.com/Diavolos88/MocConnectToMockController) - библиотека для подключения Spring Boot заглушек к MockController с автоматической синхронизацией конфигурации

---

## Дополнительные примеры

Подробные примеры использования эндпоинта выполнения сценария по группе и названию:
- [API_EXECUTE_SCENARIO_EXAMPLE.md](API_EXECUTE_SCENARIO_EXAMPLE.md)

---

## Функционал применения шаблонов в веб-интерфейсе

### Применение шаблона к конфигурации заглушки

На странице редактирования конфигурации заглушки (`/configs/{systemName}`) доступна правая колонка со списком шаблонов для данной системы.

**Функционал:**
- Отображение всех шаблонов для текущей системы в правой колонке
- Каждый шаблон отображается в виде карточки с названием, описанием и кнопкой "Применить"
- При нажатии на кнопку "Применить" значения из шаблона подставляются в форму конфигурации
- Поля с измененными значениями подсвечиваются синим цветом
- Изменения не сохраняются автоматически - пользователь может просмотреть их и решить, сохранить или запланировать применение

**Особенности:**
- Шаблоны применяются только к заглушкам той же системы
- Применение шаблона не перезаписывает текущие значения, если они не указаны в шаблоне
- После применения шаблона пользователь может сохранить изменения или запланировать их применение на определенное время

---

## Темная тема

Веб-интерфейс поддерживает переключение между светлой и темной темой.

**Функционал:**
- Кнопка переключения темы расположена в правом верхнем углу навигационной панели (справа от кнопки FAQ)
- При переключении темы настройка сохраняется в `localStorage` браузера
- Выбранная тема автоматически применяется при загрузке всех страниц
- Темная тема применяется ко всем элементам интерфейса:
  - Фон страниц и карточек
  - Текст и заголовки
  - Кнопки и формы
  - Навигационные элементы
  - Списки шаблонов и сценариев
  - Шаги сценариев и применяемые шаблоны

**Технические детали:**
- Тема применяется через CSS класс `dark-theme` на элементах `body` и `html`
- Стили определены в файле `dark-theme.css`
- Переключение темы обрабатывается скриптом `theme.js`
- Предотвращается мерцание (FOUC) при загрузке страницы благодаря раннему применению темы

---

## Управление группами систем

Веб-интерфейс для создания, редактирования и удаления групп систем.

**Эндпоинты:**
- `GET /groups` - страница со списком всех групп
- `GET /groups/new` - страница создания новой группы
- `POST /groups` - создание новой группы
- `GET /groups/{id}/edit` - страница редактирования группы
- `POST /groups/{id}` - обновление группы
- `POST /groups/{id}/delete` - удаление группы
- `POST /groups/{groupId}/remove-system` - удаление системы из группы (используется со страницы статусов)

**Функционал:**
- Создание групп с названием, описанием и списком заглушек (полные имена, например `test-integration-mock`)
- Редактирование существующих групп (название, описание, список заглушек)
- Удаление групп с подтверждением
- Отображение всех групп с информацией о количестве заглушек в каждой группе
- Автоматическое создание и обновление групп для заглушек, следующих шаблону `system-integration-mock`
- Возможность удаления заглушки из группы со страницы статусов
- Возможность добавления заглушки обратно в группу через редактирование группы
- Автоматическое извлечение доступных систем из зарегистрированных заглушек
- **Автоматическое создание и обновление групп** для заглушек, следующих шаблону `название_системы-интеграция-mock`

**Автоматическое создание групп:**
При регистрации новой заглушки, которая следует шаблону `название_системы-интеграция-mock` (например, `auth-login-mock`, `user-profile-mock`), система автоматически:
1. Извлекает название системы (часть до первого тире, например `auth` из `auth-login-mock`)
2. Проверяет, существует ли группа с таким названием
3. Если группы нет - создает новую группу с названием системы и добавляет туда этот мок
4. Если группа существует, но мока в ней нет - добавляет мок в существующую группу

**Примеры:**
- При регистрации `auth-login-mock` создается группа `auth` с этим моком
- При регистрации `auth-token-mock` мок добавляется в существующую группу `auth`
- При регистрации `user-profile-mock` создается группа `user` с этим моком
- При регистрации `user-settings-mock` мок добавляется в существующую группу `user`

**Требования к шаблону:**
- Название заглушки должно содержать минимум 2 тире
- Название заглушки должно заканчиваться на `-mock`
- Формат: `название_системы-название_интеграции-mock`

**Создание группы:**
1. Нажмите кнопку "+ Создать группу" на странице списка групп
2. Заполните название группы (обязательно)
3. При необходимости добавьте описание
4. Выберите системы из списка доступных систем
5. Нажмите "Создать группу"

**Редактирование группы:**
1. На странице списка групп нажмите кнопку "Редактировать" на нужной группе
2. Измените название, описание или список систем
3. Нажмите "Сохранить изменения"

**Удаление группы:**
1. На странице списка групп нажмите кнопку "Удалить" на нужной группе
2. Подтвердите удаление в диалоговом окне

**Примечания:**
- Группы используются для организации заглушек на главной странице и странице статусов
- На главной странице отображаются только группы из базы данных
- Во вкладке группы на главной странице показываются только заглушки, добавленные в эту группу
- Системы, не входящие ни в одну группу, отображаются в группе "Без группы" на странице статусов (только работающие заглушки с healthcheck)
- При редактировании группы можно изменить список систем, добавив или удалив системы из группы
- Заглушку можно удалить из группы прямо со страницы статусов, нажав кнопку "×" на карточке заглушки
- Удаление заглушки из группы не удаляет саму заглушку из системы, только убирает её из группы

---

## Страница статусов заглушек

Веб-интерфейс для мониторинга статусов заглушек с группировкой по группам систем.

**Эндпоинты:**
- `GET /status` - общая страница статусов всех заглушек, сгруппированных по группам
- `GET /status/{systemName}` - детальная страница со списком всех инстансов конкретной системы

**Функционал:**
- Отображение статусов заглушек, сгруппированных по группам систем
- Для каждой группы отображается:
  - Название и описание группы
  - Количество активных заглушек (с хотя бы одним онлайн инстансом)
  - Общее количество инстансов (под)
  - Количество онлайн и офлайн инстансов
- Для каждой заглушки в группе отображается:
  - Название системы
  - Статус (Online/Offline/Mixed/Нет подов)
  - Количество онлайн, офлайн и общее количество инстансов
  - Время последнего успешного healthcheck
  - Кнопка удаления из группы (только для заглушек в группах, не для "Без группы")
- Системы без группы отображаются в отдельной группе "Без группы"
- Автоматическое обновление страницы каждые 30 секунд
- Карточки заглушек отображаются в горизонтальном формате (все данные в одну строку)
- Возможность удалить заглушку из группы прямо со страницы статусов

**Статусы:**
- **Online** - все инстансы системы онлайн (последний healthcheck менее времени, указанного в `app.status.offline-threshold-seconds`)
- **Offline** - все инстансы системы офлайн (последний healthcheck более времени, указанного в `app.status.offline-threshold-seconds`)
- **Mixed** - часть инстансов онлайн, часть офлайн

**Примечания:**
- Инстанс (под) заглушки считается онлайн, если последний healthcheck был менее времени, указанного в `app.status.offline-threshold-seconds` (по умолчанию 300 секунд = 5 минут)
- Инстансы, не отправлявшие healthcheck дольше времени, указанного в `app.status.cleanup-threshold-seconds` (по умолчанию 3600 секунд = 1 час), автоматически удаляются из базы
- **Важно:** Удаляются именно инстансы (поды), а не заглушки (системы). Если у заглушки были неактивные инстансы, они удалятся, но сама заглушка останется в системе
- **Логика отображения заглушек:**
  - Заглушки с healthcheck (имеющие хотя бы один инстанс) отображаются всегда
  - Заглушки с 0 подов отображаются только если они добавлены в группу
  - В группе "Без группы" отображаются только работающие заглушки (с healthcheck), которые не добавлены ни в одну группу
  - Заглушки с 0 подов, не добавленные в группы, не отображаются на странице статусов
- **Группировка:** Используются только реальные имена заглушек из базы данных (из `StoredConfig`). Группировка по префиксам не используется.
- Время офлайн и время очистки можно настроить в `application.yml` (см. раздел "Конфигурация приложения")
- Страница автоматически обновляется каждые 30 секунд
- При клике на название системы открывается детальная страница со списком всех инстансов

**Навигация:**
- Основные вкладки (Заглушки, Шаблоны, Сценарии) расположены по центру
- Вторичные вкладки (Группы, Статусы, FAQ) и кнопка переключения темы расположены в правом верхнем углу

---

## История выполнения сценариев

Веб-интерфейс для просмотра истории выполнения сценариев доступен по адресу:
```
GET /history
```

**Описание:**
- Отображает все запланированные и выполненные обновления конфигураций, созданные при выполнении сценариев
- Группирует записи по группам сценариев
- Показывает запланированные обновления (от ближайших к дальним)
- Показывает выполненные обновления (от последних к старым)
- Поддерживает пагинацию по 10 записей на странице
- Позволяет фильтровать по группам

**Параметры:**
- `groupId` (query, необязательное) - ID группы для фильтрации (по умолчанию показываются все группы)
- `page` (query, необязательное) - номер страницы для пагинации (по умолчанию 1)

**Пример:**
```
http://localhost:8085/history
http://localhost:8085/history?groupId=550e8400-e29b-41d4-a716-446655440000
http://localhost:8085/history?groupId=550e8400-e29b-41d4-a716-446655440000&page=2
```

---

## Конфигурация приложения

Параметры конфигурации приложения настраиваются в файле `application.yml`.

### Параметры статусов заглушек

**`app.status.offline-threshold-seconds`** (по умолчанию: `300`)
- Время в секундах, после которого инстанс (под) заглушки считается офлайн
- По умолчанию: 300 секунд (5 минут)
- Если инстанс заглушки не отправлял healthcheck дольше этого времени, он считается офлайн
- **Важно:** Удаляются именно инстансы (поды), а не заглушки (системы). Одна заглушка может иметь несколько инстансов

**`app.status.stats-window-seconds`** (по умолчанию: `300`)
- Время в секундах для подсчета статистики (всего и оффлайн считаются за этот период)
- По умолчанию: 300 секунд (5 минут)
- Учитываются только инстансы, которые отправляли healthcheck за последние `stats-window-seconds`
- Инстансы, которые не отправляли healthcheck за этот период, не учитываются в статистике "всего" и "оффлайн"

**`app.status.cleanup-threshold-seconds`** (по умолчанию: `3600`)
- Время в секундах, после которого инстанс (под) заглушки удаляется из базы данных
- По умолчанию: 3600 секунд (1 час)
- Инстансы, не отправлявшие healthcheck дольше этого времени, автоматически удаляются из базы
- **Важно:** Удаляются именно инстансы (поды), а не заглушки (системы). Если у заглушки были неактивные инстансы, они удалятся, но сама заглушка останется в системе

**Пример конфигурации:**
```yaml
app:
  status:
    # Время в секундах, после которого под считается офлайн (по умолчанию 300 = 5 минут)
    offline-threshold-seconds: 300
    # Время в секундах для подсчета статистики (всего и оффлайн считаются за этот период, по умолчанию 300 = 5 минут)
    stats-window-seconds: 300
    # Время в секундах, после которого под удаляется из базы (по умолчанию 3600 = 1 час)
    cleanup-threshold-seconds: 3600
```

**Примечания:**
- Значения указываются в секундах
- Рекомендуется устанавливать `cleanup-threshold-seconds` больше, чем `offline-threshold-seconds` и `stats-window-seconds`
- Статистика "всего" и "оффлайн" считается только за период, указанный в `stats-window-seconds` (по умолчанию 5 минут)
- Инстансы, которые не отправляли healthcheck за последние `stats-window-seconds`, не учитываются в статистике
- Изменения в конфигурации требуют перезапуска приложения


