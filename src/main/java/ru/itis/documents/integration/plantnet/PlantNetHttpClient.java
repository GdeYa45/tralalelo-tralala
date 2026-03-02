package ru.itis.documents.integration.plantnet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;
import ru.itis.documents.integration.plantnet.dto.PlantNetIdentifyResponse;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlantNetHttpClient {

    private final RestTemplate restTemplate;

    // ВАЖНО: читаем из app.plantnet.*, чтобы совпадало с application.yml
    @Value("${app.plantnet.baseUrl:https://my-api.plantnet.org}")
    private String baseUrl;

    @Value("${app.plantnet.project:all}")
    private String project;

    // Дефолт пустой, чтобы приложение не падало на старте, если ключ не задан
    @Value("${app.plantnet.apiKey:}")
    private String apiKey;

    @Value("${app.plantnet.lang:en}")
    private String lang;

    @Value("${app.plantnet.nbResults:5}")
    private int nbResults;

    public PlantNetIdentifyResponse identify(MultipartFile file) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("PLANTNET_API_KEY не задан (app.plantnet.apiKey пустой)");
        }

        try {
            String safeApiKey = UriUtils.encodeQueryParam(apiKey, StandardCharsets.UTF_8);
            String safeLang = UriUtils.encodeQueryParam(lang, StandardCharsets.UTF_8);

            String url = baseUrl + "/v2/identify/" + project
                    + "?api-key=" + safeApiKey
                    + "&lang=" + safeLang
                    + "&nb-results=" + nbResults;

            byte[] bytes = file.getBytes();

            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
                            ? "image"
                            : file.getOriginalFilename();
                }
            };

            HttpHeaders partHeaders = new HttpHeaders();
            if (file.getContentType() != null) {
                partHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
            }
            partHeaders.setContentDisposition(ContentDisposition.formData()
                    .name("images")
                    .filename(resource.getFilename())
                    .build());

            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, partHeaders);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("images", filePart);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<PlantNetIdentifyResponse> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    PlantNetIdentifyResponse.class
            );

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.warn("PlantNet identify failed: status={}, body-null={}", resp.getStatusCode(), resp.getBody() == null);
                throw new RuntimeException("Pl@ntNet недоступен или вернул некорректный ответ");
            }

            return resp.getBody();
        } catch (RestClientException ex) {
            log.warn("PlantNet HTTP error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Ошибка запроса к Pl@ntNet");
        } catch (Exception ex) {
            log.warn("PlantNet identify error: {}", ex.getMessage(), ex);
            throw new RuntimeException("Не удалось обработать распознавание");
        }
    }
}