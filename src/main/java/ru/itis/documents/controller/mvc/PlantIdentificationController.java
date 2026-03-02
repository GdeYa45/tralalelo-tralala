package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;
import ru.itis.documents.exception.IntegrationException;
import ru.itis.documents.form.PlantCandidateSelectForm;
import ru.itis.documents.form.PlantIdentificationForm;
import ru.itis.documents.service.FileStorageService;
import ru.itis.documents.service.PlantIdentificationService;
import ru.itis.documents.service.PlantRecognitionImportService;

@Controller
@RequestMapping("/app/identify")
public class PlantIdentificationController {

    private final PlantIdentificationService identificationService;
    private final PlantRecognitionImportService importService;
    private final FileStorageService fileStorageService;

    public PlantIdentificationController(PlantIdentificationService identificationService,
                                         PlantRecognitionImportService importService,
                                         FileStorageService fileStorageService) {
        this.identificationService = identificationService;
        this.importService = importService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PlantIdentificationForm());
        }
        return "app/identify/form";
    }

    @GetMapping("/ajax")
    public String ajaxIdentifyPage() {
        return "app/identify/index";
    }

    @PostMapping
    public String submit(
            @Valid @ModelAttribute("form") PlantIdentificationForm form,
            BindingResult bindingResult,
            Authentication auth,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "app/identify/form";
        }

        try {
            Long id = identificationService.identify(auth.getName(), form.getPhoto());
            redirectAttributes.addFlashAttribute("msg", "Распознавание выполнено");
            return "redirect:/app/identify/" + id; // PRG
        } catch (IntegrationException e) {
            // 4.5.5: HTML/JSON ошибка через GlobalExceptionHandler
            throw e;
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "app/identify/form";
        }
    }

    @GetMapping("/history")
    public String history(Authentication auth, Model model) {
        model.addAttribute("items", identificationService.history(auth.getName()));
        return "app/identify/history";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Authentication auth, Model model) {
        var item = identificationService.getDetails(auth.getName(), id);
        model.addAttribute("item", item);
        model.addAttribute("failed", item.getStatus() == PlantIdentificationStatus.FAILED);
        return "app/identify/details";
    }

    @PostMapping("/{id}/select")
    public String selectScientificName(
            @PathVariable Long id,
            @Valid @ModelAttribute("selectForm") PlantCandidateSelectForm form,
            BindingResult bindingResult,
            Authentication auth,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("selectError", "Выбери кандидата");
            return "redirect:/app/identify/" + id; // PRG
        }

        identificationService.selectScientificName(auth.getName(), id, form.getSelectedScientificName());
        redirectAttributes.addFlashAttribute("msg", "Кандидат выбран");
        return "redirect:/app/identify/" + id; // PRG
    }

    @PostMapping("/{id}/import")
    public String importSelected(@PathVariable Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            Long plantSpeciesId = importService.importSelectedCandidateToCatalog(auth.getName(), id);
            redirectAttributes.addFlashAttribute("msg", "Вид импортирован в каталог");
            return "redirect:/app/species/" + plantSpeciesId; // PRG
        } catch (IntegrationException e) {
            throw e;
        } catch (Exception e) {
            // PRG даже на ошибке — чтобы F5 не повторял POST
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return "redirect:/app/identify/" + id;
        }
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable Long id, Authentication auth) {
        String relativePath = identificationService.photoPathFor(auth.getName(), id);
        Resource resource = fileStorageService.load(relativePath);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noCache())
                .body(resource);
    }
}