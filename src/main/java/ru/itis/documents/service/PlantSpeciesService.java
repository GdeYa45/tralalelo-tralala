package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.dto.view.CareProfileView;
import ru.itis.documents.dto.view.PlantSpeciesView;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.domain.enums.LightLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlantSpeciesService {

    private final PlantSpeciesRepository plantSpeciesRepository;
    private final CapriciousnessService capriciousnessService;

    public List<PlantSpeciesView> listCatalog(String q, LightLevel light, String cap, List<Tag> tags) {
        String qNorm = normalize(q);
        String capNorm = normalize(cap);
        String lightNorm = (light == null) ? null : light.ruLabel();
        if (capNorm != null) capNorm = capNorm.toUpperCase(Locale.ROOT);

        // Этап 10.3: выбранные теги (по id) для фильтра
        Set<Long> selectedTagIds = (tags == null) ? Set.of() : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Tag::getId)
                .collect(Collectors.toSet());

        String finalCapNorm = capNorm;
        return plantSpeciesRepository.findAll().stream()
                .map(this::toView)
                .filter(s -> qNorm == null
                        || containsIgnoreCase(s.name(), qNorm)
                        || containsIgnoreCase(s.latinName(), qNorm)
                        || containsIgnoreCase(s.description(), qNorm))
                .filter(s -> lightNorm == null
                        || (s.care() != null && containsIgnoreCase(s.care().lightLevel(), lightNorm)))
                .filter(s -> finalCapNorm == null
                        || (s.capriciousness() != null && equalsIgnoreCase(s.capriciousness().key(), finalCapNorm)))
                // фильтр по тегам: вид должен содержать ВСЕ выбранные теги
                .filter(s -> selectedTagIds.isEmpty()
                        || (s.tagIds() != null && selectedTagIds.stream().allMatch(id -> s.tagIds().contains(id))))
                .sorted(Comparator.comparing(PlantSpeciesView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<PlantSpeciesView> getDetails(Long id) {
        return plantSpeciesRepository.findById(id).map(this::toView);
    }

    private PlantSpeciesView toView(PlantSpecies s) {
        CareProfile cp = s.getCareProfile();
        CareProfileView care = null;
        if (cp != null) {
            care = new CareProfileView(
                    cp.getWaterIntervalDays(),
                    cp.getLightLevel(),
                    cp.getHumidityPercent(),
                    cp.getNotes()
            );
        }

        List<String> tags = (s.getTags() == null) ? List.of() : s.getTags().stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        // ids тегов нужны для фильтра 10.3 (чтобы не тащить entity наружу)
        List<Long> tagIds = (s.getTags() == null) ? List.of() : s.getTags().stream()
                .map(Tag::getId)
                .filter(id -> id != null)
                .toList();

        CapriciousnessView cap = capriciousnessService.evaluate(s);

        return new PlantSpeciesView(
                s.getId(),
                s.getName(),
                s.getLatinName(),
                s.getDescription(),
                care,
                cap,
                tags,
                tagIds
        );
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}