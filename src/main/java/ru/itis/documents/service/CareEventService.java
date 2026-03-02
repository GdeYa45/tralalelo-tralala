package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareEvent;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.repository.CareEventRepository;
import ru.itis.documents.repository.UserPlantRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CareEventService {

    private final UserPlantRepository userPlantRepository;
    private final CareEventRepository careEventRepository;
    private final CarePlanService carePlanService;

    /**
     * Этап 6.2 (P0): AJAX-кнопка "Полил".
     * Создаёт событие полива и пересчитывает план.
     */
    @Transactional
    public void addWatering(Long userId, Long plantId, String comment) {
        UserPlant plant = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        CareEvent e = new CareEvent();
        e.setUserPlant(plant);
        e.setType(CareActionType.WATER);
        e.setComment(normalizeNullable(comment));

        careEventRepository.save(e);

        // ✅ DoD 5.5: пересчёт плана
        carePlanService.applyEvent(plant, CareActionType.WATER, e.getEventTime());
    }

    /**
     * Журнал событий по конкретному растению пользователя (MVC).
     */
    @Transactional(readOnly = true)
    public List<CareEvent> listMyPlantEvents(Long userId, Long plantId) {
        userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        return careEventRepository.findAllByUserPlant_IdOrderByEventTimeDesc(plantId);
    }

    // -------------------------------------------------------------------------
    // Этап 8.2 (P0): REST CRUD/операции для CareEvent
    // -------------------------------------------------------------------------

    /**
     * Список событий текущего пользователя.
     * Если plantId задан — фильтрует по конкретному растению пользователя.
     */
    @Transactional(readOnly = true)
    public List<CareEvent> listMyEvents(Long userId, Long plantId) {
        if (plantId == null) {
            return careEventRepository.findAllByUserPlant_User_IdOrderByEventTimeDesc(userId);
        }
        userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        return careEventRepository.findAllByUserPlant_IdAndUserPlant_User_IdOrderByEventTimeDesc(plantId, userId);
    }

    @Transactional(readOnly = true)
    public CareEvent getMyEvent(Long userId, Long eventId) {
        return careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));
    }

    /**
     * Создать событие ухода (WATER/FERTILIZE/REPOT/...) для растения пользователя.
     * План пересчитывается (закрываем ближайшую задачу и создаём следующую).
     */
    @Transactional
    public CareEvent createEvent(Long userId, Long plantId, CareActionType type, OffsetDateTime eventTime, String comment) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        UserPlant plant = userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));

        CareEvent e = new CareEvent();
        e.setUserPlant(plant);
        e.setType(type);
        e.setEventTime(eventTime); // может быть null → prePersist поставит now()
        e.setComment(normalizeNullable(comment));

        careEventRepository.save(e);

        // ✅ после события актуализируем план
        carePlanService.applyEvent(plant, type, e.getEventTime());

        return e;
    }

    /**
     * Обновить событие пользователя.
     * Важно: план не "откатывается" и не пересчитывается заново (это отдельная сложная логика).
     */
    @Transactional
    public CareEvent updateEvent(Long userId, Long eventId, CareActionType type, OffsetDateTime eventTime, String comment) {
        CareEvent e = careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));

        if (type != null) {
            e.setType(type);
        }
        if (eventTime != null) {
            e.setEventTime(eventTime);
        }
        e.setComment(normalizeNullable(comment));

        return careEventRepository.save(e);
    }

    @Transactional
    public void deleteEvent(Long userId, Long eventId) {
        CareEvent e = careEventRepository.findByIdAndUserPlant_User_Id(eventId, userId)
                .orElseThrow(() -> new CareEventNotFoundException("Событие не найдено"));
        careEventRepository.delete(e);
    }

    private static String normalizeNullable(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static class UserPlantNotFoundException extends RuntimeException {
        public UserPlantNotFoundException(String message) { super(message); }
    }

    public static class CareEventNotFoundException extends RuntimeException {
        public CareEventNotFoundException(String message) { super(message); }
    }
}