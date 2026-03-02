package ru.itis.documents.repository;

import ru.itis.documents.domain.entity.PlantSpecies;

import java.util.List;

/**
 * Этап 9.3 (P0): нестандартный метод через CriteriaBuilder.
 *
 * "Подбор видов под условия квартиры" с динамическими фильтрами.
 */
public interface PlantSpeciesRepositoryCustom {

    /**
     * Подбор видов по условиям (динамические фильтры).
     *
     * @param q                часть названия/латинского названия (nullable/blank)
     * @param roomLightLevel   уровень света в комнате (nullable/blank)
     * @param minHumidity      минимальная влажность (nullable)
     * @param maxWaterInterval максимальный интервал полива (nullable)
     * @param tag              фильтр по тегу (nullable/blank)
     * @param limit            ограничение результата (если null или <=0, используется 50)
     */
    List<PlantSpecies> findSuitableForApartment(
            String q,
            String roomLightLevel,
            Integer minHumidity,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    );
}