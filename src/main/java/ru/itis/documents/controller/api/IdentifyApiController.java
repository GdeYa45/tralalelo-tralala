package ru.itis.documents.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.dto.ApiErrorResponse;
import ru.itis.documents.dto.view.PlantnetCandidateAjaxView;
import ru.itis.documents.service.PlantNetAjaxIdentifyService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/identify")
@RequiredArgsConstructor
public class IdentifyApiController {

    private final PlantNetAjaxIdentifyService plantNetAjaxIdentifyService;

    @PostMapping(
            value = "/plantnet",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> identifyPlantnet(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("FILE_REQUIRED", "Нужно выбрать файл изображения", null));
        }

        String ct = file.getContentType();
        if (ct != null && !(ct.equalsIgnoreCase("image/jpeg") || ct.equalsIgnoreCase("image/png"))) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("BAD_FILE_TYPE", "Разрешены только JPG/PNG", ct));
        }

        // ВАЖНО: IntegrationException НЕ ловим — пусть обработает GlobalExceptionHandler (JSON {code,message,details})
        try {
            List<PlantnetCandidateAjaxView> candidates = plantNetAjaxIdentifyService.identifyCandidates(file);
            return ResponseEntity.ok(candidates);
        } catch (IllegalArgumentException ex) {
            // это не ошибка внешнего API, а ошибка чтения файла
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("BAD_FILE", ex.getMessage(), null));
        }
    }
}