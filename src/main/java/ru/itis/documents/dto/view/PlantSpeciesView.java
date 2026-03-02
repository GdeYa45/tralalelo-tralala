package ru.itis.documents.dto.view;

import java.util.List;

public record PlantSpeciesView(
        Long id,
        String name,
        String latinName,
        String description,
        CareProfileView care,
        CapriciousnessView capriciousness,
        List<String> tags,
        /**
         * Этап 10.3 (P1): ids тегов (для фильтра/выбора в UI).
         */
        List<Long> tagIds
) {}