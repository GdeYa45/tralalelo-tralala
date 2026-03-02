package ru.itis.documents.dto.view;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.itis.documents.domain.enums.CareActionType;

import java.time.OffsetDateTime;

/**
 * DTO для REST API (Этап 8): не отдаём JPA-сущность напрямую (lazy/cycle).
 */
@Schema(name = "CareEventView", description = "Событие ухода за растением (DTO для REST)")
public record CareEventView(
        @Schema(description = "ID события", example = "123")
        Long id,

        @Schema(description = "ID растения пользователя (UserPlant)", example = "45")
        Long userPlantId,

        @Schema(description = "Тип действия", example = "WATER")
        CareActionType type,

        @Schema(description = "Дата/время события", example = "2026-03-02T12:34:56+01:00")
        OffsetDateTime eventTime,

        @Schema(description = "Комментарий (опционально)", example = "Полил после пересадки")
        String comment
) {
}