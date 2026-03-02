package ru.itis.documents.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
        import ru.itis.documents.dto.ApiErrorResponse;
import ru.itis.documents.dto.view.CareTaskItemView;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.CareEventService;
import ru.itis.documents.service.UserPlantService;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Этап 6.2 (P0): AJAX-кнопка "Полил" на карточке UserPlant.
 *
 * DoD: нажатие добавляет CareEvent и возвращает JSON (обновляет "следующий полив" и список задач).
 */
@RestController
@RequestMapping("/api/plants")
@RequiredArgsConstructor
public class UserPlantCareApiController {

    private final CareEventService careEventService;
    private final UserPlantService userPlantService;

    @PostMapping(value = "/{plantId}/water", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> water(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long plantId,
            @RequestBody(required = false) WateringRequest body
    ) {
        Long userId = principal.getUser().getId();

        try {
            String comment = body == null ? null : body.comment();
            careEventService.addWatering(userId, plantId, comment);

            // После события план уже пересчитан (CarePlanService.applyEvent).
            // Забираем обновлённые данные для UI.
            var p = userPlantService.getMyPlantDetails(userId, plantId).orElse(null);
            if (p == null) {
                return ResponseEntity.status(NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ApiErrorResponse("PLANT_NOT_FOUND", "Растение не найдено", null));
            }

            return ResponseEntity.ok(new WateringAjaxResponse(
                    p.getNextWateringText(),
                    p.getTasks()
            ));
        } catch (CareEventService.UserPlantNotFoundException ex) {
            return ResponseEntity.status(NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("PLANT_NOT_FOUND", ex.getMessage(), null));
        } catch (Exception ex) {
            // По требованиям из дока: AJAX должен получать JSON, не дефолтную HTML/whitelabel страницу.
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка", null));
        }
    }

    public record WateringRequest(String comment) {}

    public record WateringAjaxResponse(
            String nextWateringText,
            List<CareTaskItemView> tasks
    ) {}
}