package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.dto.view.UserPlantDetailsView;
import ru.itis.documents.domain.entity.Room;
import ru.itis.documents.form.UserPlantCreateForm;
import ru.itis.documents.form.UserPlantUpdateForm;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.UserPlantService;
import ru.itis.documents.form.PlantIdentificationForm;
import ru.itis.documents.form.UserPlantByPhotoForm;
import ru.itis.documents.service.PlantIdentificationService;
import ru.itis.documents.service.PlantRecognitionImportService;

@Controller
@RequestMapping("/app/plants")
@RequiredArgsConstructor
public class UserPlantController {

    private final UserPlantService userPlantService;
    private final PlantIdentificationService plantIdentificationService;
    private final PlantRecognitionImportService plantRecognitionImportService;

    @GetMapping
    public String list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model
    ) {
        Long userId = principal.getUser().getId();
        model.addAttribute("plants", userPlantService.listMyPlants(userId));
        return "app/plants/list";
    }

    @GetMapping("/new")
    public String createPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserPlantCreateForm());
        }
        model.addAttribute("speciesOptions", userPlantService.speciesOptions());
        model.addAttribute("roomOptions", userPlantService.roomOptions(userId));
        return "app/plants/create";
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("form") UserPlantCreateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/new";
        }

        try {
            Long id = userPlantService.create(userId, form);
            redirectAttributes.addFlashAttribute("msg", "Растение добавлено");
            return "redirect:/app/plants/" + id;
        } catch (UserPlantService.SpeciesNotFoundException ex) {
            bindingResult.addError(new FieldError("form", "speciesId", ex.getMessage()));
        } catch (UserPlantService.RoomNotFoundException ex) {
            bindingResult.addError(new FieldError("form", "room", ex.getMessage()));
        }

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/app/plants/new";
    }

    @GetMapping("/{id}")
    public String details(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        return userPlantService.getMyPlantDetails(userId, id)
                .map(p -> {
                    model.addAttribute("p", p);
                    return "app/plants/details";
                })
                .orElseGet(() -> {
                    model.addAttribute("message", "Растение не найдено");
                    return "app/plants/not_found";
                });
    }

    @GetMapping("/{id}/edit")
    public String editPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        if (!model.containsAttribute("form")) {
            UserPlantDetailsView p = userPlantService.getMyPlantDetails(userId, id).orElse(null);
            if (p == null) {
                model.addAttribute("message", "Растение не найдено");
                return "app/plants/not_found";
            }

            UserPlantUpdateForm form = new UserPlantUpdateForm();
            form.setNickname(p.getNickname());
            form.setSpeciesId(p.getSpeciesId());
            if (p.getRoomId() != null) {
                Room r = new Room();
                r.setId(p.getRoomId());
                form.setRoom(r);
            }
            form.setPurchaseDate(p.getPurchaseDate());
            form.setNotes(p.getNotes());
            model.addAttribute("form", form);
        }

        model.addAttribute("id", id);
        model.addAttribute("speciesOptions", userPlantService.speciesOptions());
        model.addAttribute("roomOptions", userPlantService.roomOptions(userId));
        return "app/plants/edit";
    }

    @PostMapping("/{id}")
    public String update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") UserPlantUpdateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/" + id + "/edit";
        }

        try {
            userPlantService.update(userId, id, form);
            redirectAttributes.addFlashAttribute("msg", "Изменения сохранены");
            return "redirect:/app/plants/" + id;
        } catch (UserPlantService.UserPlantNotFoundException ex) {
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
            return "redirect:/app/plants";
        } catch (UserPlantService.SpeciesNotFoundException ex) {
            bindingResult.addError(new FieldError("form", "speciesId", ex.getMessage()));
        } catch (UserPlantService.RoomNotFoundException ex) {
            bindingResult.addError(new FieldError("form", "room", ex.getMessage()));
        }

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/app/plants/" + id + "/edit";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        return userPlantService.getMyPlantDetails(userId, id)
                .map(p -> {
                    model.addAttribute("p", p);
                    return "app/plants/delete";
                })
                .orElseGet(() -> {
                    model.addAttribute("message", "Растение не найдено");
                    return "app/plants/not_found";
                });
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        try {
            userPlantService.delete(userId, id);
            redirectAttributes.addFlashAttribute("msg", "Растение удалено");
        } catch (UserPlantService.UserPlantNotFoundException ex) {
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
        }

        return "redirect:/app/plants";
    }

    @GetMapping("/new/photo")
    public String createByPhotoUploadPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PlantIdentificationForm());
        }
        return "app/plants/create_photo_upload";
    }

    @PostMapping("/new/photo")
    public String createByPhotoUpload(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("form") PlantIdentificationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "app/plants/create_photo_upload";
        }

        Long identificationId = plantIdentificationService.identify(principal.getUsername(), form.getPhoto());
        return "redirect:/app/plants/new/photo/" + identificationId; // PRG
    }

    @GetMapping("/new/photo/{identId}")
    public String createByPhotoSelectPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long identId,
            Model model
    ) {
        var item = plantIdentificationService.getDetails(principal.getUsername(), identId);
        model.addAttribute("item", item);

        Long userId = principal.getUser().getId();
        model.addAttribute("roomOptions", userPlantService.roomOptions(userId));

        if (!model.containsAttribute("plantForm")) {
            UserPlantByPhotoForm plantForm = new UserPlantByPhotoForm();
            String nickname = (item.getBestMatch() == null || item.getBestMatch().isBlank())
                    ? "Моё растение"
                    : item.getBestMatch();
            plantForm.setNickname(nickname);
            plantForm.setSelectedScientificName(item.getSelectedScientificName());

            model.addAttribute("plantForm", plantForm);
        }

        return "app/plants/create_photo_select";
    }

    @PostMapping("/new/photo/{identId}/create")
    public String createByPhotoFinish(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long identId,
            @Valid @ModelAttribute("plantForm") UserPlantByPhotoForm plantForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        var item = plantIdentificationService.getDetails(principal.getUsername(), identId);

        if (bindingResult.hasErrors()) {
            model.addAttribute("item", item);
            Long userId = principal.getUser().getId();
            model.addAttribute("roomOptions", userPlantService.roomOptions(userId));
            return "app/plants/create_photo_select";
        }

        plantIdentificationService.selectScientificName(
                principal.getUsername(),
                identId,
                plantForm.getSelectedScientificName()
        );

        try {
            Long speciesId = plantRecognitionImportService.importSelectedCandidateToCatalog(
                    principal.getUsername(),
                    identId
            );

            UserPlantCreateForm createForm = new UserPlantCreateForm();
            createForm.setSpeciesId(speciesId);
            createForm.setRoom(plantForm.getRoom());
            createForm.setNickname(plantForm.getNickname());
            createForm.setPurchaseDate(plantForm.getPurchaseDate());
            createForm.setNotes(plantForm.getNotes());

            Long userId = principal.getUser().getId();
            Long plantId = userPlantService.create(userId, createForm);

            redirectAttributes.addFlashAttribute("msg", "Растение добавлено по фото");
            return "redirect:/app/plants/" + plantId; // PRG

        } catch (Exception e) {
            model.addAttribute("item", item);
            Long userId = principal.getUser().getId();
            model.addAttribute("roomOptions", userPlantService.roomOptions(userId));
            model.addAttribute("importError", e.getMessage());
            return "app/plants/create_photo_select";
        }
    }
}