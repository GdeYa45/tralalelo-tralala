package ru.itis.documents.integration.perenual;

import java.util.List;

/**
 * Упрощённый результат поиска по Perenual (species-list).
 */
public record PerenualSpeciesShort(
        long id,
        String commonName,
        List<String> scientificNames,
        String imageUrl
) {
}