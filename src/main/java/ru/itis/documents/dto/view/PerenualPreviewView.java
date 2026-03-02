package ru.itis.documents.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerenualPreviewView(
        String query,              // нормализованный запрос, который пошёл в Perenual
        long perenualId,
        String name,               // commonName или scientificName (если commonName нет)
        String scientificName,     // первая scientificName
        List<String> scientificNames,
        String imageUrl,

        boolean alreadyImported,
        Long localSpeciesId        // если уже импортирован — id в нашей БД
) {
}