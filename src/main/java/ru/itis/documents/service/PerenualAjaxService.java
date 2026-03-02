package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.dto.view.PerenualImportedSpeciesView;
import ru.itis.documents.dto.view.PerenualPreviewView;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesShort;
import ru.itis.documents.repository.PlantSpeciesRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PerenualAjaxService {

    private final PerenualClient perenualClient;
    private final PerenualImportService perenualImportService;
    private final PlantSpeciesRepository plantSpeciesRepository;

    @Transactional(readOnly = true)
    public PerenualPreviewView previewByScientificName(String scientificNameRaw) {
        String query = PlantRecognitionImportService.normalizeScientificNameForSearch(scientificNameRaw);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Не получилось подготовить запрос из имени: " + scientificNameRaw);
        }

        SearchAttempt attempt = searchWithFallback(query);
        List<PerenualSpeciesShort> found = attempt.results();
        if (found.isEmpty()) {
            if (!attempt.usedQuery().equalsIgnoreCase(query)) {
                throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query
                        + " (также пробовали: " + attempt.usedQuery() + ")");
            }
            throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query);
        }

        long perenualId = chooseBestPerenualId(query, found);
        PerenualSpeciesShort chosen = found.stream()
                .filter(x -> x.id() == perenualId)
                .findFirst()
                .orElse(found.get(0));

        String sci = firstScientific(chosen.scientificNames());
        String name = firstNonBlank(chosen.commonName(), sci, "Вид #" + perenualId);

        boolean already = plantSpeciesRepository.existsByExternalId(perenualId);
        Long localId = null;
        if (already) {
            localId = plantSpeciesRepository.findByExternalId(perenualId).map(PlantSpecies::getId).orElse(null);
        }

        return new PerenualPreviewView(
                query,
                perenualId,
                name,
                sci,
                chosen.scientificNames(),
                chosen.imageUrl(),
                already,
                localId
        );
    }

    @Transactional
    public PerenualImportedSpeciesView importByPerenualId(long perenualId) {
        PlantSpecies sp = perenualImportService.importIfMissing(perenualId);
        return new PerenualImportedSpeciesView(
                perenualId,
                sp.getId(),
                sp.getName(),
                sp.getLatinName()
        );
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

    private static long chooseBestPerenualId(String normalizedQuery, List<PerenualSpeciesShort> results) {
        String q = normalizedQuery.trim().toLowerCase(Locale.ROOT);

        for (PerenualSpeciesShort r : results) {
            for (String sci : r.scientificNames()) {
                String n = PlantRecognitionImportService.normalizeScientificNameForSearch(sci);
                if (n != null && n.trim().toLowerCase(Locale.ROOT).equals(q)) {
                    return r.id();
                }
            }
        }
        return results.get(0).id();
    }

    private static String firstScientific(List<String> scientificNames) {
        if (scientificNames == null || scientificNames.isEmpty()) return null;
        for (String s : scientificNames) {
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }
}