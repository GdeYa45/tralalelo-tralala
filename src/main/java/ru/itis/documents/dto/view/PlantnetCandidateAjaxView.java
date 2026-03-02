package ru.itis.documents.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY) // commonNames будет пропущено, если null/empty
public record PlantnetCandidateAjaxView(
        String name,
        double score,
        List<String> commonNames
) {}