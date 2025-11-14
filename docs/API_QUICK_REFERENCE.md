# API Быстрая справка

## REST API

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

