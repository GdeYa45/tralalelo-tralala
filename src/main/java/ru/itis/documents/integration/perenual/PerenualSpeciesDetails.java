package ru.itis.documents.integration.perenual;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Детали вида растения из Perenual (species/details/{id}).
 *
 * rawNode хранится для отладки/логов (и чтобы при необходимости можно было
 * доставать дополнительные поля без переписывания клиента).
 */
public record PerenualSpeciesDetails(
        long id,
        String commonName,
        List<String> scientificNames,
        String description,
        String cycle,
        String watering,
        PerenualWateringBenchmark wateringBenchmark,
        List<String> sunlight,
        String careLevel,
        String imageUrl,
        JsonNode rawNode
) {
}