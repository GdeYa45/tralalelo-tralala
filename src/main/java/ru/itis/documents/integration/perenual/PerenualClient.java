package ru.itis.documents.integration.perenual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.itis.documents.exception.ExternalApiUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PerenualClient {

    private static final Logger log = LoggerFactory.getLogger(PerenualClient.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public PerenualClient(
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Value("${app.perenual.apiKey:}") String apiKey,
            @Value("${app.perenual.baseUrl:https://perenual.com/api/v2}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public List<PerenualSpeciesShort> searchSpecies(String q) {
        if (q == null || q.isBlank()) return List.of();
        ensureApiKey();

        String qNorm = normalizeQuery(q);
        if (qNorm == null || qNorm.isBlank()) return List.of();

        String cacheKey = qNorm.trim().toLowerCase(Locale.ROOT);
        List<PerenualSpeciesShort> cached = searchCache.get(cacheKey);
        if (cached != null) return cached;

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/species-list")
                .queryParam("key", apiKey)
                .queryParam("q", qNorm)
                .build()
                .encode()
                .toUriString();

        String json = doGet(url);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                List<PerenualSpeciesShort> empty = List.of();
                searchCache.put(cacheKey, empty);
                return empty;
            }

            List<PerenualSpeciesShort> out = new ArrayList<>();
            for (JsonNode it : data) {
                long id = it.path("id").asLong();
                String commonName = textOrNull(it.get("common_name"));
                List<String> scientificNames = textArray(it.get("scientific_name"));
                String imageUrl = textOrNull(it.path("default_image").get("original_url"));
                out.add(new PerenualSpeciesShort(id, commonName, scientificNames, imageUrl));
            }
            searchCache.put(cacheKey, out);
            return out;
        } catch (Exception e) {
            log.error("Failed to parse Perenual species-list response", e);
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Perenual вернул неожиданный ответ. Попробовать позже.",
                    503,
                    e
            );
        }
    }
    // ==== защита от rate-limit (429): делаем паузу между вызовами Perenual ====
    private final Object perenualLock = new Object();
    private volatile long lastCallMs = 0L;
    private volatile long blockedUntilMs = 0L;
    // безопасно: ~1 запрос / 1.1 сек (можно 800-1200, но 1100 стабильнее)
    private static final long MIN_INTERVAL_MS = 5000L;

    // ==== простой TTL-кэш без сторонних библиотек ====
    private final TtlCache<String, List<PerenualSpeciesShort>> searchCache =
            new TtlCache<>(10 * 60_000L); // 10 минут

    private final TtlCache<Long, PerenualSpeciesDetails> detailsCache =
            new TtlCache<>(24 * 60 * 60_000L); // 24 часа

    private static class TtlCache<K, V> {
        private final long ttlMs;
        private final Map<K, Entry<V>> map = new ConcurrentHashMap<>();

        private record Entry<V>(V value, long expiresAt) {}

        private TtlCache(long ttlMs) {
            this.ttlMs = ttlMs;
        }

        V get(K key) {
            Entry<V> e = map.get(key);
            if (e == null) return null;
            if (System.currentTimeMillis() >= e.expiresAt) {
                map.remove(key);
                return null;
            }
            return e.value;
        }

        void put(K key, V value) {
            map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMs));
        }
    }

    public PerenualSpeciesDetails getSpeciesDetails(long id) {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        ensureApiKey();

        PerenualSpeciesDetails cached = detailsCache.get(id);
        if (cached != null) return cached;

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/species/details/")
                .path(Long.toString(id))
                .queryParam("key", apiKey)
                .build()
                .encode()
                .toUriString();

        String json = doGet(url);

        try {
            JsonNode root = objectMapper.readTree(json);

            long respId = root.path("id").asLong();
            String commonName = textOrNull(root.get("common_name"));
            List<String> scientificNames = textArray(root.get("scientific_name"));
            String description = textOrNull(root.get("description"));
            List<String> sunlight = textArray(root.get("sunlight"));

            PerenualWateringBenchmark wateringBenchmark = parseWateringBenchmark(root.get("watering_general_benchmark"));
            String watering = textOrNull(root.get("watering"));
            String cycle = textOrNull(root.get("cycle"));
            String careLevel = textOrNull(root.get("care_level"));
            String imageUrl = textOrNull(root.path("default_image").get("original_url"));

            PerenualSpeciesDetails details = new PerenualSpeciesDetails(
                    respId,
                    commonName,
                    scientificNames,
                    description,
                    cycle,
                    watering,
                    wateringBenchmark,
                    sunlight,
                    careLevel,
                    imageUrl,
                    root
            );
            detailsCache.put(id, details);
            return details;
        } catch (Exception e) {
            log.error("Failed to parse Perenual species/details response", e);
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Perenual вернул неожиданный ответ. Попробовать позже.",
                    503,
                    e
            );
        }
    }

    private String doGet(String url) {
        // 1 повтор на 429 + уважение Retry-After
        for (int attempt = 0; attempt < 2; attempt++) {
            // cooldown: если недавно получили 429 — не долбим API
            long now = System.currentTimeMillis();
            if (now < blockedUntilMs) {
                throw new ExternalApiUnavailableException(
                        "perenual",
                        "Достигнут лимит запросов к Perenual. Попробовать позже.",
                        429
                );
            }

            try {
                throttle();

                ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    throw new ExternalApiUnavailableException(
                            "perenual",
                            "Perenual временно недоступен. Попробовать позже.",
                            resp.getStatusCode().value()
                    );
                }
                return resp.getBody();

            } catch (HttpStatusCodeException e) {
                int code = e.getStatusCode().value();
                log.warn("Perenual HTTP {} body={}", code, e.getResponseBodyAsString());

                if (code == 429) {
                    long waitMs = 4000L; // дефолтная пауза, если Retry-After нет

                    // пробуем прочитать Retry-After (в секундах)
                    try {
                        String ra = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
                        if (ra != null && !ra.isBlank()) {
                            long sec = Long.parseLong(ra.trim());
                            if (sec > 0) waitMs = Math.min(sec * 1000L, 30_000L);
                        }
                    } catch (Exception ignore) {
                        // оставляем waitMs по умолчанию
                    }

                    // ставим cooldown, чтобы последующие вызовы не спамили API
                    blockedUntilMs = System.currentTimeMillis() + waitMs;

                    // если это первая попытка — ждём и пробуем ещё раз
                    if (attempt == 0) {
                        try {
                            Thread.sleep(waitMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }

                    throw new ExternalApiUnavailableException(
                            "perenual",
                            "Достигнут лимит запросов к Perenual. Попробовать позже.",
                            429,
                            e
                    );
                }

                String msg;
                if (code >= 500) msg = "Perenual временно недоступен. Попробовать позже.";
                else msg = "Ошибка запроса к Perenual (" + code + "). Попробовать позже.";

                throw new ExternalApiUnavailableException("perenual", msg, code, e);

            } catch (ResourceAccessException e) {
                throw new ExternalApiUnavailableException("perenual", "Perenual не отвечает. Попробовать позже.", 503, e);
            } catch (RestClientException e) {
                throw new ExternalApiUnavailableException("perenual", "Perenual временно недоступен. Попробовать позже.", 503, e);
            }
        }

        // сюда не должны доходить
        throw new ExternalApiUnavailableException("perenual", "Perenual временно недоступен. Попробовать позже.", 503);
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            // это тоже проблема интеграции (для пользователя выглядит как “не работает”)
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Интеграция Perenual не настроена (нет ключа).",
                    503
            );
        }
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static List<String> textArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode it : node) {
            if (it != null && !it.isNull()) out.add(it.asText());
        }
        return out;
    }

    private static PerenualWateringBenchmark parseWateringBenchmark(JsonNode node) {
        if (node == null || node.isNull()) return null;

        String unit = textOrNull(node.get("unit"));
        Integer minDays = null;
        Integer maxDays = null;

        JsonNode value = node.get("value");
        if (value != null && !value.isNull()) {
            if (value.isTextual()) {
                int[] mm = tryParseRange(value.asText());
                if (mm != null) {
                    minDays = mm[0];
                    maxDays = mm[1];
                }
            } else if (value.isNumber()) {
                minDays = value.asInt();
                maxDays = value.asInt();
            }
        }

        return new PerenualWateringBenchmark(minDays, maxDays, unit);
    }

    private static int[] tryParseRange(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        String[] parts = t.split("-");
        try {
            if (parts.length == 2) {
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                return new int[]{Math.min(a, b), Math.max(a, b)};
            }
            int x = Integer.parseInt(t);
            return new int[]{x, x};
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private static String normalizeQuery(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        int par = s.indexOf('(');
        if (par > 0) s = s.substring(0, par).trim();

        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[,;]+$", "").trim();

        String[] parts = s.split(" ");
        if (parts.length <= 2) return s;

        // если гибрид: "Genus x species" или "Genus × species" — оставляем 3 слова
        if (parts.length >= 3 && ("x".equalsIgnoreCase(parts[1]) || "×".equals(parts[1]))) {
            return parts[0] + " " + parts[1] + " " + parts[2];
        }

        // иначе оставляем только "Genus species"
        return parts[0] + " " + parts[1];
    }

    private void throttle() {
        synchronized (perenualLock) {
            long now = System.currentTimeMillis();
            long wait = MIN_INTERVAL_MS - (now - lastCallMs);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastCallMs = System.currentTimeMillis();
        }
    }
}