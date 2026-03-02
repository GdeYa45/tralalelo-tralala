package ru.itis.documents.dto.view;

import java.time.OffsetDateTime;

/**
 * Этап 9.4 (P0): DTO для страницы/дашборда.
 */
public record StaleWateringPlantView(
        Long plantId,
        String nickname,
        String speciesName,
        OffsetDateTime lastWaterTime,
        long daysWithoutWatering
) {}