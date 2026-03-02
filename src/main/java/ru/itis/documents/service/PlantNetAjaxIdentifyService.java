package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.dto.view.PlantnetCandidateAjaxView;
import ru.itis.documents.integration.plantnet.PlantNetClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantNetAjaxIdentifyService {

    private final PlantNetClient plantNetClient;

    public List<PlantnetCandidateAjaxView> identifyCandidates(MultipartFile file) {
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось прочитать файл изображения", e);
        }

        var res = plantNetClient.identify(bytes, file.getOriginalFilename());

        if (res.candidates() == null) {
            return List.of();
        }

        return res.candidates().stream()
                .map(c -> new PlantnetCandidateAjaxView(
                        (c.scientificName() == null || c.scientificName().isBlank()) ? "(unknown)" : c.scientificName(),
                        c.score(),
                        (c.commonNames() == null || c.commonNames().isEmpty()) ? null : c.commonNames()
                ))
                .toList();
    }
}