// Универсальный скрипт для переключения темы
(function() {
    // Применяем тему ДО загрузки DOM, чтобы избежать мерцания
    function applyThemeImmediately() {
        const savedTheme = localStorage.getItem('theme');
        if (savedTheme === 'dark') {
            document.documentElement.classList.add('dark-theme');
            if (document.body) {
                document.body.classList.add('dark-theme');
            }
        }
    }
    
    // Применяем тему сразу
    applyThemeImmediately();
    
    function initTheme() {
        const themeToggle = document.getElementById('themeToggle');
        if (!themeToggle) {
            // Если кнопка не найдена, пробуем еще раз через небольшую задержку
            setTimeout(function() {
                const retryToggle = document.getElementById('themeToggle');
                if (retryToggle) {
                    setupThemeToggle(retryToggle);
                }
            }, 100);
            return;
        }
        
        setupThemeToggle(themeToggle);
    }
    
    function setupThemeToggle(themeToggle) {
        const body = document.body;
        const html = document.documentElement;
        
        // Загружаем сохраненную тему и применяем
        const savedTheme = localStorage.getItem('theme');
        if (savedTheme === 'dark') {
            body.classList.add('dark-theme');
            html.classList.add('dark-theme');
            themeToggle.textContent = '☀️';
        } else {
            body.classList.remove('dark-theme');
            html.classList.remove('dark-theme');
            themeToggle.textContent = '🌙';
        }
        
        // Удаляем старый обработчик, если он был
        const newToggle = themeToggle.cloneNode(true);
        themeToggle.parentNode.replaceChild(newToggle, themeToggle);
        
        // Обработчик переключения темы
        newToggle.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            
            const isDark = body.classList.contains('dark-theme');
            
            if (isDark) {
                // Переключаем на светлую тему
                body.classList.remove('dark-theme');
                html.classList.remove('dark-theme');
                newToggle.textContent = '🌙';
                localStorage.setItem('theme', 'light');
            } else {
                // Переключаем на темную тему
                body.classList.add('dark-theme');
                html.classList.add('dark-theme');
                newToggle.textContent = '☀️';
                localStorage.setItem('theme', 'dark');
            }
        });
    }
    
    // Инициализация при загрузке DOM
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        // Если DOM уже загружен, инициализируем сразу
        initTheme();
    }
})();

