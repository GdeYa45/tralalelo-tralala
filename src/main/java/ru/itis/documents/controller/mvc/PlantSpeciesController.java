package ru.itis.documents.controller.mvc;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.dto.view.PlantSpeciesView;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.repository.TagRepository;
import ru.itis.documents.service.PerenualImportService;
import ru.itis.documents.service.PlantSpeciesService;
import ru.itis.documents.domain.enums.LightLevel;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/app/species")
@RequiredArgsConstructor
public class PlantSpeciesController {

    private final PlantSpeciesService plantSpeciesService;
    private final PerenualImportService perenualImportService;
    private final TagRepository tagRepository;

    @GetMapping
    public String catalog(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "light", required = false) LightLevel light,
            @RequestParam(value = "cap", required = false) String cap,
            /**
             * Этап 10.3 (P1): multi-select тегов.
             * Форма отправляет строку "1,2,3", а сюда приходит List<Tag> через TagIdListToTagListConverter.
             */
            @RequestParam(value = "tags", required = false) List<Tag> tags,
            Model model
    ) {
        List<PlantSpeciesView> species = plantSpeciesService.listCatalog(q, light, cap, tags);
        model.addAttribute("species", species);
        model.addAttribute("q", q);
        model.addAttribute("light", light);
        model.addAttribute("cap", cap);

        // опции тегов для фильтра
        var tagOptions = tagRepository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
        model.addAttribute("tagOptions", tagOptions);

        // выбранные теги: чтобы проставить selected в <option>
        Set<Long> selectedTagIds = (tags == null) ? Set.of() : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Tag::getId)
                .collect(Collectors.toSet());
        model.addAttribute("selectedTagIds", selectedTagIds);

        // строка "1,2,3" — чтобы после submit форма не сбрасывалась
        String tagsRaw = (tags == null || tags.isEmpty())
                ? ""
                : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(t -> String.valueOf(t.getId()))
                .collect(Collectors.joining(","));
        model.addAttribute("tags", tagsRaw);

        return "app/species/catalog";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        return plantSpeciesService.getDetails(id)
                .map(s -> {
                    model.addAttribute("s", s);
                    return "app/species/details";
                })
                .orElseGet(() -> {
                    model.addAttribute("message", "Вид растения не найден");
                    return "app/species/not_found";
                });
    }

    /**
     * Этап 4.2: импорт вида из Perenual.
     * Если вида нет в локальной БД — создаёт PlantSpecies + CareProfile + Tag и редиректит на локальную карточку.
     */
    @PostMapping("/import")
    public String importFromPerenual(
            @RequestParam("perenualId") String perenualIdRaw,
            RedirectAttributes redirectAttributes
    ) {
        if (!StringUtils.hasText(perenualIdRaw)) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        long perenualId;
        try {
            perenualId = Long.parseLong(perenualIdRaw.trim());
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        if (perenualId <= 0) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        Long localId = perenualImportService.importIfMissing(perenualId).getId();
        redirectAttributes.addFlashAttribute("msg", "Вид импортирован");
        return "redirect:/app/species/" + localId; // PRG
    }
}