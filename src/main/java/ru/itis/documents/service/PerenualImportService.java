package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesDetails;
import ru.itis.documents.integration.perenual.PerenualWateringBenchmark;
import ru.itis.documents.repository.CareProfileRepository;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.repository.TagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Этап 4.2/4.3 (P0): импорт вида из Perenual с защитой от лимитов.
 *
 * DoD 4.3:
 *  - если вид уже импортирован (по externalId) — повторно API не вызывается.
 */
@Service
@RequiredArgsConstructor
public class PerenualImportService {

    private final PlantSpeciesRepository plantSpeciesRepository;
    private final CareProfileRepository careProfileRepository;
    private final TagRepository tagRepository;
    private final PerenualClient perenualClient;

    @Transactional
    public PlantSpecies importIfMissing(long perenualSpeciesId) {
        if (perenualSpeciesId <= 0) {
            throw new IllegalArgumentException("perenualSpeciesId must be positive");
        }

        // ✅ 4.3: если уже есть в БД — сразу возвращаем, НЕ трогаем API вообще
        if (plantSpeciesRepository.existsByExternalId(perenualSpeciesId)) {
            return plantSpeciesRepository.findByExternalId(perenualSpeciesId)
                    .orElseThrow(() -> new IllegalStateException("Species exists but not found by externalId"));
        }

        // ⬇️ сюда попадаем только если вида точно нет
        PerenualSpeciesDetails d = perenualClient.getSpeciesDetails(perenualSpeciesId);

        PlantSpecies species = new PlantSpecies();
        species.setExternalId(perenualSpeciesId);

        String name = firstNonBlank(d.commonName(), firstScientific(d.scientificNames()), "Вид #" + perenualSpeciesId);
        species.setName(name);
        species.setLatinName(firstScientific(d.scientificNames()));
        species.setDescription(d.description());

        // Доп. защита: если вид уже есть в каталоге по latin/common name,
        // переиспользуем его и не идем в Perenual (экономим лимит).
        String normalizedName = normalizeName(name);
        String normalizedLatin = normalizeName(species.getLatinName());

        if (normalizedLatin != null) {
            Optional<PlantSpecies> byLatin = plantSpeciesRepository.findFirstByLatinNameIgnoreCase(normalizedLatin);
            if (byLatin.isPresent()) {
                PlantSpecies existing = byLatin.get();
                ensureExternalId(existing, perenualSpeciesId);
                return existing;
            }
        }

        if (normalizedName != null) {
            Optional<PlantSpecies> byName = plantSpeciesRepository.findFirstByNameIgnoreCase(normalizedName);
            if (byName.isPresent()) {
                PlantSpecies existing = byName.get();
                ensureExternalId(existing, perenualSpeciesId);
                return existing;
            }
        }

        species = plantSpeciesRepository.save(species);

        CareProfile careProfile = new CareProfile();
        careProfile.setSpecies(species);
        careProfile.setWaterIntervalDays(guessWaterIntervalDays(d.wateringBenchmark()));
        careProfile.setLightLevel(mapLightLevel(d.sunlight()));
        careProfile.setNotes("Импортировано из Perenual");
        careProfileRepository.save(careProfile);

        Set<Tag> tags = new HashSet<>();

        String lightTag = careProfile.getLightLevel();
        if (lightTag != null) tags.add(ensureTag(lightTag));

        String careTag = mapCareLevelToTag(d.careLevel());
        if (careTag != null) tags.add(ensureTag(careTag));

        String wateringTag = mapWaterToTag(careProfile.getWaterIntervalDays());
        if (wateringTag != null) tags.add(ensureTag(wateringTag));

        if (tags.isEmpty()) tags.add(ensureTag("тропическое"));

        species.getTags().addAll(tags);
        return plantSpeciesRepository.save(species);
    }

    private Tag ensureTag(String name) {
        return tagRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(name);
                    return tagRepository.save(t);
                });
    }

    private void ensureExternalId(PlantSpecies species, long perenualSpeciesId) {
        if (species.getExternalId() == null) {
            species.setExternalId(perenualSpeciesId);
            plantSpeciesRepository.save(species);
        }
    }

    private static String normalizeName(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private static Integer guessWaterIntervalDays(PerenualWateringBenchmark wb) {
        if (wb == null) return null;
        Integer a = wb.minValue();
        Integer b = wb.maxValue();
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return Math.max(1, (a + b) / 2);
    }

    private static String mapLightLevel(List<String> sunlight) {
        if (sunlight == null || sunlight.isEmpty()) return null;

        String joined = String.join(" ", sunlight).toLowerCase(Locale.ROOT);

        if (joined.contains("full") && joined.contains("sun")) return "яркий свет";
        if (joined.contains("part") && (joined.contains("shade") || joined.contains("sun"))) return "полутень";
        if (joined.contains("shade")) return "теневыносливое";
        return null;
    }

    private static String mapCareLevelToTag(String careLevel) {
        if (careLevel == null || careLevel.isBlank()) return null;
        String t = careLevel.trim().toLowerCase(Locale.ROOT);

        if (t.contains("easy") || t.contains("low") || t.contains("begin")) return "для новичков";
        if (t.contains("hard") || t.contains("high") || t.contains("difficult")) return "капризное";
        return null;
    }

    private static String mapWaterToTag(Integer waterIntervalDays) {
        if (waterIntervalDays == null) return null;
        if (waterIntervalDays <= 7) return "влаголюбивое";
        if (waterIntervalDays >= 14) return "засухоустойчивое";
        return null;
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
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }
}