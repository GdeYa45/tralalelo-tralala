package ru.itis.documents.dto.view;

import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;

import java.time.LocalDate;

/**
 * Небольшой view для вывода задач ухода в UI и в AJAX-ответах.
 */
public record CareTaskItemView(
        Long id,
        CareActionType type,
        String typeLabel,
        CareTaskStatus status,
        LocalDate dueDate
) {}