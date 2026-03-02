package ru.itis.documents.dto.view;

import java.util.List;

public record CapriciousnessView(
        String key,           // LOW/MID/HIGH
        String label,         // "Низкая/Средняя/Высокая"
        String cssClass,      // можно null
        Integer score,        // 0..100
        List<String> reasons  // причины, почему так посчиталось
) {}