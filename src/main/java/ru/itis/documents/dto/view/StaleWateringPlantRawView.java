package ru.itis.documents.dto.view;

import java.time.OffsetDateTime;

/**
 * Этап 9.4 (P0): DTO результата JPQL-запроса с подзапросом.
 */
public record StaleWateringPlantRawView(
        Long plantId,
        String nickname,
        String speciesName,
        OffsetDateTime lastWaterTime
) {}