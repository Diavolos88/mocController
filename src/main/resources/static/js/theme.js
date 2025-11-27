// Универсальный скрипт для переключения темы
(function() {
    // Применяем тему ДО загрузки DOM, чтобы избежать мерцания
    function applyThemeImmediately() {
        const savedTheme = localStorage.getItem('theme');
        if (savedTheme === 'dark') {
            document.documentElement.classList.add('dark-theme');
            document.body.classList.add('dark-theme');
        }
    }
    
    // Применяем тему сразу, если скрипт загружается до полной загрузки страницы
    if (document.readyState === 'loading') {
        applyThemeImmediately();
    } else {
        // Если страница уже загружена, применяем сразу
        applyThemeImmediately();
    }
    
    function initTheme() {
        const themeToggle = document.getElementById('themeToggle');
        if (!themeToggle) return;
        
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
        
        // Обработчик переключения темы
        themeToggle.addEventListener('click', function() {
            const isDark = body.classList.contains('dark-theme');
            
            if (isDark) {
                // Переключаем на светлую тему
                body.classList.remove('dark-theme');
                html.classList.remove('dark-theme');
                themeToggle.textContent = '🌙';
                localStorage.setItem('theme', 'light');
            } else {
                // Переключаем на темную тему
                body.classList.add('dark-theme');
                html.classList.add('dark-theme');
                themeToggle.textContent = '☀️';
                localStorage.setItem('theme', 'dark');
            }
        });
    }
    
    // Инициализация при загрузке DOM
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initTheme);
    } else {
        initTheme();
    }
})();

