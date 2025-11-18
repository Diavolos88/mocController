# MockController

Сервис для централизованного управления конфигурациями заглушек в нагрузочном тестировании.

## Быстрый старт

### Требования
- Java 17+
- PostgreSQL 14+ (или используйте Docker)
- Maven 3.6+

### Установка и настройка

1. **Установите PostgreSQL** (если еще не установлен):
   ```powershell
   .\setup-postgresql.ps1
   ```
   Или следуйте инструкции в [POSTGRESQL_SETUP.md](POSTGRESQL_SETUP.md)

2. **Настройте подключение к БД** в `src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/mockcontroller
       username: postgres
       password: ваш_пароль
   ```

3. **Запустите приложение**:
   ```bash
   .\restart.bat
   # или
   mvn spring-boot:run
   ```

4. **Откройте веб-интерфейс**:
   ```
   http://localhost:8080
   ```

## Основные возможности

- ✅ Прием конфигов от заглушек через REST API
- ✅ Автоматическое управление версиями (v1, v2, ...)
- ✅ Веб-интерфейс для редактирования параметров
- ✅ Отображение стартовых значений с возможностью подстановки
- ✅ Откат конфига к стартовому состоянию
- ✅ Отложенное сохранение конфигураций с комментариями
- ✅ Удаление конфигураций через веб-интерфейс
- ✅ Группировка заглушек по системам на главной странице
- ✅ Хранение данных в PostgreSQL

## Технологии

- **Backend**: Java 17, Spring Boot 3.3.1, Spring Data JPA
- **Database**: PostgreSQL
- **Frontend**: Thymeleaf, HTML/CSS/JavaScript
- **Build**: Maven

## Структура проекта

- `src/main/java` - Java код (Spring Boot)
- `src/main/resources/templates` - HTML шаблоны
- `src/main/resources/application.yml` - конфигурация приложения
- `docs/` - документация

## База данных

Приложение использует PostgreSQL для хранения данных. Таблицы создаются автоматически при первом запуске.

**Таблицы:**
- `stored_configs` - конфигурации заглушек
- `scheduled_config_updates` - запланированные обновления

Подробнее: [POSTGRESQL_SETUP.md](POSTGRESQL_SETUP.md)

## Документация

- [API Документация](docs/API_DOCUMENTATION.md) - полное описание REST API и веб-интерфейса с примерами
- [API Быстрая справка](docs/API_QUICK_REFERENCE.md) - краткая шпаргалка по эндпоинтам
- [Проект](PROJECT_SUMMARY.md) - общее описание проекта
- [Установка PostgreSQL](POSTGRESQL_SETUP.md) - инструкция по установке и настройке БД

## Интеграция

Пример реализации заглушки с интеграцией к MockController:
- [MocConnectToMockController](https://github.com/Diavolos88/MocConnectToMockController) - библиотека для подключения Spring Boot заглушек к MockController
