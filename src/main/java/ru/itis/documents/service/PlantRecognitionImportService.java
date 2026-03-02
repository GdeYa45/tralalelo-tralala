package ru.itis.documents.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesShort;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.PlantIdentificationRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlantRecognitionImportService {

    private final AppUserRepository appUserRepository;
    private final PlantIdentificationRepository plantIdentificationRepository;

    private final PerenualClient perenualClient;
    private final PerenualImportService perenualImportService;

    /**
     * 4.5.3: выбранный кандидат -> Perenual species-list?q -> выбор вида -> импорт в локальную БД
     * Возвращает локальный plantSpeciesId.
     */
    @Transactional
    public Long importSelectedCandidateToCatalog(String username, Long identificationId) {
        AppUser user = resolveUser(username);

        PlantIdentification pi = plantIdentificationRepository.findByIdAndUser_Id(identificationId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Распознавание не найдено"));

        String selected = pi.getSelectedScientificName();
        if (selected == null || selected.isBlank()) {
            throw new IllegalStateException("Сначала нужно выбрать кандидата (radio-кнопкой) и нажать «Выбрать».");
        }

        String query = normalizeScientificNameForSearch(selected);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Не получилось подготовить запрос в Perenual из выбранного имени: " + selected);
        }

        // 1) Perenual species-list?q=... (fallback: если "Genus species" не нашлось — пробуем только "Genus")
        SearchAttempt attempt = searchWithFallback(query);
        List<PerenualSpeciesShort> found = attempt.results();
        if (found.isEmpty()) {
            if (!attempt.usedQuery().equalsIgnoreCase(query)) {
                throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query
                        + " (также пробовали: " + attempt.usedQuery() + ")");
            }
            throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query);
        }

        // 2) выбор "подходящего" вида: сначала пытаемся совпасть по scientific name, иначе берём первый
        long perenualId = chooseBestPerenualId(query, found);

        // 3) импорт в локальную БД (PlantSpecies + CareProfile + Tag) по твоему сценарию
        return perenualImportService.importIfMissing(perenualId).getId();
    }

    private long chooseBestPerenualId(String normalizedQuery, List<PerenualSpeciesShort> results) {
        String q = normalizedQuery.trim().toLowerCase(Locale.ROOT);

        // (а) точное совпадение по scientific name
        for (PerenualSpeciesShort r : results) {
            for (String sci : r.scientificNames()) {
                String n = normalizeScientificNameForSearch(sci);
                if (n != null && n.trim().toLowerCase(Locale.ROOT).equals(q)) {
                    return r.id();
                }
            }
        }

        // (б) fallback: первый результат (часто релевантен)
        return results.get(0).id();
    }

    private SearchAttempt searchWithFallback(String normalizedQuery) {
        String q = normalizedQuery.trim();

        List<PerenualSpeciesShort> found = perenualClient.searchSpecies(q);
        if (!found.isEmpty()) {
            return new SearchAttempt(q, found);
        }

        String genus = q.split("\\s+")[0].trim();
        if (genus.isEmpty() || genus.equalsIgnoreCase(q)) {
            return new SearchAttempt(q, found);
        }

        List<PerenualSpeciesShort> byGenus = perenualClient.searchSpecies(genus);
        return new SearchAttempt(genus, byGenus);
    }

    private record SearchAttempt(String usedQuery, List<PerenualSpeciesShort> results) {}

    /**
     * Нормализация scientific name для поиска:
     * - убирает автора/скобки/лишние хвосты
     * - оставляет "Genus species" (или "Genus × species")
     */
    static String normalizeScientificNameForSearch(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        int par = s.indexOf('(');
        if (par > 0) s = s.substring(0, par).trim();

        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[,;]+$", "").trim();

        if (s.isEmpty()) return null;

        String[] parts = s.split(" ");
        if (parts.length == 1) return parts[0];

        // гибрид: Genus × species (или Genus x species)
        if (parts.length >= 3 && ("x".equalsIgnoreCase(parts[1]) || "×".equals(parts[1]))) {
            return parts[0] + " " + parts[1] + " " + parts[2];
        }

        // по умолчанию: Genus species
        return parts[0] + " " + parts[1];
    }

    private AppUser resolveUser(String username) {
        return appUserRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }
}