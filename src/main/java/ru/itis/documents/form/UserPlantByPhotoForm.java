package ru.itis.documents.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import ru.itis.documents.domain.entity.Room;

import java.time.LocalDate;

@Getter
@Setter
public class UserPlantByPhotoForm {

    @NotBlank(message = "Выбери кандидата")
    @Size(max = 255, message = "Слишком длинное научное название")
    private String selectedScientificName;

    /**
     * Этап 10.2 (P0): id комнаты из формы автоматически конвертируется в Room.
     */
    private Room room;

    @NotBlank(message = "Укажи название (кличку) растения")
    @Size(max = 120, message = "Название слишком длинное (макс 120)")
    private String nickname;

    @PastOrPresent(message = "Дата покупки не может быть в будущем")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    @Size(max = 4000, message = "Заметка слишком длинная")
    private String notes;
}