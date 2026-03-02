package ru.itis.documents.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ru.itis.documents.domain.entity.Room;
import ru.itis.documents.repository.RoomRepository;

@Component
@RequiredArgsConstructor
public class RoomConverter implements Converter<String, Room> {

    private final RoomRepository roomRepository;

    @Override
    public Room convert(String source) {
        if (source == null) return null;
        String s = source.trim();
        if (s.isEmpty()) return null;

        Long id;
        try {
            id = Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }

        return roomRepository.findById(id).orElse(null);
    }
}