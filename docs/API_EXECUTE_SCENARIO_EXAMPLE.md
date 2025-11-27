# Пример использования эндпоинта выполнения сценария

## Эндпоинт

```
GET /api/scenarios/execute
```

## Параметры

- `group` (обязательный) - ID группы (UUID) или название группы
- `name` (обязательный) - название сценария
- `startTime` (обязательный) - время начала в формате `HH:mm:ss dd-MM-yyyy`

## Примеры запросов

### 1. Использование ID группы (UUID)

**cURL:**
```bash
curl -X GET "http://localhost:8080/api/scenarios/execute?group=550e8400-e29b-41d4-a716-446655440000&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024"
```

**PowerShell:**
```powershell
$group = "550e8400-e29b-41d4-a716-446655440000"
$name = "Сценарий теста"
$startTime = "14:30:00 25-12-2024"

$uri = "http://localhost:8080/api/scenarios/execute?group=$([System.Web.HttpUtility]::UrlEncode($group))&name=$([System.Web.HttpUtility]::UrlEncode($name))&startTime=$([System.Web.HttpUtility]::UrlEncode($startTime))"

Invoke-RestMethod -Uri $uri -Method Get
```

**HTTP:**
```http
GET /api/scenarios/execute?group=550e8400-e29b-41d4-a716-446655440000&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024 HTTP/1.1
Host: localhost:8080
```

### 2. Использование названия группы

**cURL:**
```bash
curl -X GET "http://localhost:8080/api/scenarios/execute?group=Auth%20системы&name=Сценарий%20теста&startTime=14:30:00%2025-12-2024"
```

**PowerShell:**
```powershell
$group = "Auth системы"
$name = "Сценарий теста"
$startTime = "14:30:00 25-12-2024"

$uri = "http://localhost:8080/api/scenarios/execute?group=$([System.Web.HttpUtility]::UrlEncode($group))&name=$([System.Web.HttpUtility]::UrlEncode($name))&startTime=$([System.Web.HttpUtility]::UrlEncode($startTime))"

Invoke-RestMethod -Uri $uri -Method Get
```

## Успешный ответ

```
HTTP/1.1 200 OK
Content-Type: text/plain

Сценарий 'Сценарий теста' успешно запланирован на 14:30:00 25-12-2024
```

## Ошибки

### 400 Bad Request - Неверный формат времени
```
Неверный формат времени. Используйте: HH:mm:ss dd-MM-yyyy
```

### 400 Bad Request - Время в прошлом
```
Время начала не может быть в прошлом
```

### 400 Bad Request - Группа не найдена
```
Группа с названием 'Название группы' не найдена. Используйте /api/groups для получения списка групп.
```

### 404 Not Found - Сценарий не найден
```
Сценарий не найден
```

### 500 Internal Server Error
```
Ошибка при выполнении сценария: [описание ошибки]
```

## Как получить ID группы

Перед использованием эндпоинта можно получить список всех групп:

```bash
curl -X GET http://localhost:8080/api/groups
```

Ответ:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Auth системы",
    "description": "Группа систем аутентификации",
    "systems": ["auth", "oauth"],
    "createdAt": "2024-12-20T10:15:30Z"
  }
]
```

Используйте поле `id` в запросе выполнения сценария.

## Примечания

1. Параметр `group` может быть как UUID группы, так и названием группы (без учета регистра)
2. Если группа указана по названию, происходит поиск группы по названию
3. Все временные точки в сценарии рассчитываются относительно `startTime`
4. Если у шага указан `scheduledTime` - извлекается относительное время (HH:mm) и вычисляется как `startTime + относительное время`
5. Если у шага указан `delayMs` - вычисляется как `startTime + delayMs`
6. Если вычисленное время шага оказывается в прошлом, оно автоматически устанавливается на 1 секунду в будущем

