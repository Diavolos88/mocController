# Настройка Git репозитория

## Инициализация репозитория

Выполните следующие команды для инициализации git репозитория:

```bash
# Инициализация репозитория
git init

# Добавление удаленного репозитория
git remote add origin git@github.com:Diavolos88/mocController.git

# Добавление всех файлов
git add .

# Первый коммит
git commit -m "Initial commit: MockController service"

# Отправка в репозиторий
git branch -M main
git push -u origin main
```

## Альтернативный способ (через HTTPS)

Если SSH не настроен, используйте HTTPS:

```bash
git remote add origin https://github.com/Diavolos88/mocController.git
git push -u origin main
```

## Проверка статуса

```bash
# Проверка статуса
git status

# Проверка удаленных репозиториев
git remote -v
```

