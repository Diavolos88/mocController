package com.mockcontroller.util;

/**
 * Утилиты для работы с названиями систем (моков)
 */
public final class SystemNameUtils {

    private SystemNameUtils() {
        // Утилитный класс - запрещаем создание экземпляров
    }

    /**
     * Проверяет, соответствует ли название мока шаблону system-integration-mock.
     * Шаблон: минимум 2 тире и заканчивается на -mock
     *
     * @param systemName название системы для проверки
     * @return true, если название соответствует шаблону
     */
    public static boolean isValidTemplate(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return false;
        }
        // Шаблон: название_системы-название_эмуляции-mock
        // Должно быть минимум 2 тире и заканчиваться на -mock
        int dashCount = 0;
        for (char c : systemName.toCharArray()) {
            if (c == '-') {
                dashCount++;
            }
        }
        return dashCount >= 2 && systemName.endsWith("-mock");
    }

    /**
     * Извлекает название системы из шаблона system-integration-mock.
     * Возвращает часть до первого тире.
     *
     * @param systemName полное название системы
     * @return префикс системы (до первого тире) или полное название, если тире нет
     */
    public static String extractSystemPrefix(String systemName) {
        if (systemName == null || systemName.isEmpty()) {
            return systemName;
        }
        // Берем первое слово до первого тире
        int firstDashIndex = systemName.indexOf('-');
        if (firstDashIndex > 0) {
            return systemName.substring(0, firstDashIndex);
        }
        return systemName;
    }

    /**
     * Санитизирует название системы, заменяя недопустимые символы на подчеркивание.
     * Разрешенные символы: буквы, цифры, дефис и подчеркивание.
     *
     * @param name исходное название
     * @return санитизированное название или пустая строка, если входное значение null
     */
    public static String sanitize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}

