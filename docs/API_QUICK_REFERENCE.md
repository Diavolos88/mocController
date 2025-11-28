# API Быстрая справка

## REST API

### Healthcheck сервиса

```bash
GET /service/healthcheck
```

**cURL:**
```bash
curl -X GET http://localhost:8080/service/healthcheck
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/service/healthcheck" -Method Get
```

**Ответ:**
```
OK
```

---

### Healthcheck от заглушек

```bash
POST /api/healthcheck?systemName={systemName}&instanceId={instanceId}
```

**cURL:**
```bash
curl -X POST "http://localhost:8080/api/healthcheck?systemName=auth-mock&instanceId=instance-1"
```

**PowerShell:**
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/healthcheck?systemName=auth-mock&instanceId=instance-1" -Method Post
```

**Ответ:**
```
OK
```

**Примечания:**
- Заглушки должны отправлять healthcheck каждые 1-2 минуты
- Заглушка считается онлайн, если последний healthcheck был менее 5 минут назад
- Параметр `instanceId` необязателен (по умолчанию "default")

---

### Проверка обновлений

```bash
POST /api/configs/checkUpdate
```

**PowerShell:**
```powershell
$body = @{
    SystemName = "auth-mock"
    version = "v1"
    config = @{
        delays = @{ loginDelayMs = "1000" }
        stringParams = @{ mode = "normal" }
        loggingLv = "ERROR"
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/configs/checkUpdate" `
    -Method Post -ContentType "application/json" -Body $body
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/configs/checkUpdate \
  -H "Content-Type: application/json" \
  -d '{"SystemName":"auth-mock","version":"v1","config":{"delays":{"loginDelayMs":"1000"},"stringParams":{"mode":"normal"},"loggingLv":"ERROR"}}'
```

**Ответ:**
```json
{
  "needUpdate": false,
  "currentVersion": "v1"
}
```

---

### Загрузка конфига
```bash
POST /api/configs
```

**PowerShell:**
```powershell
$body = @{
    SystemName = "auth-mock"
    config = @{
        delays = @{ loginDelayMs = "1000" }
        stringParams = @{ mode = "normal" }
        loggingLv = "ERROR"
    }
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/configs" `
    -Method Post -ContentType "application/json" -Body $body
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/configs \
  -H "Content-Type: application/json" \
  -d '{"SystemName":"auth-mock","config":{"delays":{"loginDelayMs":"1000"},"stringParams":{"mode":"normal"},"loggingLv":"ERROR"}}'
```

**Ответ:**
```json
{
  "status": "START_REGISTERED",
  "message": "Start config saved",
  "currentVersion": "v1"
}
```

---

### Получение конфигурации
```bash
GET /api/configs/{systemName}?version=v1
```

**cURL (текущая версия):**
```bash
curl -X GET http://localhost:8080/api/configs/auth-mock
```

**cURL (конкретная версия):**
```bash
curl -X GET "http://localhost:8080/api/configs/auth-mock?version=v1"
```

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
      "mode": "test"
    },
    "loggingLv": "INFO"
  },
  "updatedAt": "2024-12-20T10:15:30Z"
}
```

---

### Планирование отложенного обновления
```bash
POST /api/configs/schedule
```

**cURL:**
```bash
curl -X POST http://localhost:8080/api/configs/schedule \
  -H "Content-Type: application/json" \
  -d '{"SystemName":"auth-mock","scheduledTime":"14:30:00 25-12-2024","config":{"delays":{"loginDelayMs":"2000"},"stringParams":{"mode":"test"},"loggingLv":"INFO"},"comment":"Увеличение задержек"}'
```

**Ответ:**
```json
{
  "success": true,
  "message": "Обновление успешно запланировано",
  "updateId": "550e8400-e29b-41d4-a716-446655440000",
  "scheduledTime": "14:30:00 25-12-2024"
}
```

---

### Получение списка запланированных обновлений
```bash
GET /api/configs/{systemName}/scheduled
```

**cURL:**
```bash
curl -X GET http://localhost:8080/api/configs/auth-mock/scheduled
```

**Ответ:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "systemName": "auth-mock",
    "scheduledTime": "14:30:00 25-12-2024",
    "comment": "Увеличение задержек",
    "createdAt": "10:15:00 20-12-2024"
  }
]
```

---

### Отмена запланированного обновления
```bash
DELETE /api/configs/scheduled/{updateId}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/configs/scheduled/550e8400-e29b-41d4-a716-446655440000
```

**Ответ:**
```json
{
  "success": true,
  "message": "Запланированное обновление отменено",
  "updateId": "550e8400-e29b-41d4-a716-446655440000",
  "scheduledTime": null
}
```

---

## Веб-интерфейс

- **Главная:** `http://localhost:8080`
- **Конфиг:** `http://localhost:8080/configs/{systemName}`

---

## Статусы ответов

### checkUpdate
- `needUpdate: false` - обновление не требуется
- `needUpdate: true` - требуется обновление

### uploadConfig
- `START_REGISTERED` - первая регистрация
- `UPDATED_START_CONFIG` - стартовый конфиг обновлен
- `UPDATE_AVAILABLE` - доступна новая версия
- `NO_CHANGES` - изменений нет

---

## Структура конфига

```json
{
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
```

**Уровни логирования:** ERROR, WARN, INFO, DEBUG

---

## Шаблоны

### Создание шаблона
```bash
POST /api/templates
```

**PowerShell:**
```powershell
$body = @{
    name = "Долгий отклик"
    systemName = "auth-mock"
    config = @{
        delays = @{ loginDelayMs = "5000" }
        loggingLv = "ERROR"
    }
    description = "Шаблон для тестирования"
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/templates" `
    -Method Post -ContentType "application/json" -Body $body
```

### Применение шаблона
```bash
POST /api/templates/{id}/apply?systemName=auth-mock
```

### Удаление шаблона
```bash
DELETE /api/templates/{id}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/templates/550e8400-e29b-41d4-a716-446655440000
```

---

## Сценарии

### Создание сценария
```bash
POST /api/scenarios
```

**PowerShell:**
```powershell
$body = @{
    groupId = "group-id-1"
    name = "Сценарий теста"
    description = "Описание"
    steps = @(
        @{ templateId = "template-id-1"; delayMs = 0 }
        @{ templateId = "template-id-2"; delayMs = 5000 }
    )
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/scenarios" `
    -Method Post -ContentType "application/json" -Body $body
```

**Важно:** `groupId` обязателен. Комбинация `groupId` + `name` должна быть уникальной.

### Выполнение сценария по ID
```bash
POST /api/scenarios/{id}/execute
```

### Выполнение сценария по группе и названию
```bash
GET /api/scenarios/execute?group={groupId}&name={scenarioName}&startTime={HH:mm:ss dd-MM-yyyy}
```

**PowerShell:**
```powershell
$group = "550e8400-e29b-41d4-a716-446655440000"
$name = "Сценарий теста"
$startTime = "14:30:00 25-12-2024"

$uri = "http://localhost:8080/api/scenarios/execute?group=$([System.Web.HttpUtility]::UrlEncode($group))&name=$([System.Web.HttpUtility]::UrlEncode($name))&startTime=$([System.Web.HttpUtility]::UrlEncode($startTime))"

Invoke-RestMethod -Uri $uri -Method Get
```

**cURL:**
```bash
curl -X GET "http://localhost:8080/api/scenarios/execute?group=550e8400-e29b-41d4-a716-446655440000&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024"
```

### Обновление сценария
```bash
PUT /api/scenarios/{id}
```

### Удаление сценария
```bash
DELETE /api/scenarios/{id}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/scenarios/660e8400-e29b-41d4-a716-446655440001
```

---

## Группы систем

### Получение списка групп
```bash
GET /api/groups
```

### Создание группы
```bash
POST /api/groups
```

**PowerShell:**
```powershell
$body = @{
    name = "Auth системы"
    description = "Группа систем аутентификации"
    systemNames = @("auth", "oauth")
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8080/api/groups" `
    -Method Post -ContentType "application/json" -Body $body
```

### Получение группы по ID
```bash
GET /api/groups/{id}
```

### Обновление группы
```bash
PUT /api/groups/{id}
```

### Удаление группы
```bash
DELETE /api/groups/{id}
```

**cURL:**
```bash
curl -X DELETE http://localhost:8080/api/groups/550e8400-e29b-41d4-a716-446655440000
```

---

## Веб-интерфейс

- **Главная:** `http://localhost:8080`
- **Конфиг:** `http://localhost:8080/configs/{systemName}`
- **Шаблоны:** `http://localhost:8080/templates`
- **Сценарии:** `http://localhost:8080/scenarios`
- **Группы:** `http://localhost:8080/groups`
- **Статусы:** `http://localhost:8080/status` - страница статусов заглушек с группировкой по группам
- **Статус системы:** `http://localhost:8080/status/{systemName}` - детальная информация по инстансам системы

---

## Пример интеграции

Пример реализации заглушки с полной интеграцией к MockController:
- [MocConnectToMockController](https://github.com/Diavolos88/MocConnectToMockController) - библиотека для подключения Spring Boot заглушек к MockController

