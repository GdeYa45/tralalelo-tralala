package ru.itis.documents.integration.perenual;

/**
 * Полезная часть поля watering_general_benchmark из Perenual.
 */
public record PerenualWateringBenchmark(
        Integer minValue,
        Integer maxValue,
        String unit
) {
}