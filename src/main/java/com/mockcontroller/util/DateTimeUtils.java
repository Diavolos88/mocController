package com.mockcontroller.util;

import java.time.format.DateTimeFormatter;

/**
 * Утилиты для работы с датой и временем
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        // Утилитный класс - запрещаем создание экземпляров
    }

    /**
     * Форматтер для даты и времени в формате HH:mm:ss dd-MM-yyyy
     */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");

    /**
     * Форматтер для времени в формате HH:mm
     */
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
}

