package ru.itis.documents.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${app.storage.baseDir:./data}")
    private String baseDir;

    public String saveUserUpload(Long userId, MultipartFile file) {
        validateImage(file);

        String ext = guessExtension(file.getOriginalFilename());
        String relative = "uploads/" + userId + "/" + UUID.randomUUID() + ext;

        Path target = Paths.get(baseDir).resolve(relative).normalize().toAbsolutePath();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return relative;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save file");
        }
    }

    public Resource load(String relativePath) {
        Path path = Paths.get(baseDir).resolve(relativePath).normalize().toAbsolutePath();
        return new FileSystemResource(path);
    }

    private static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }
        String ct = file.getContentType();
        if (ct == null || !(ct.equals("image/jpeg") || ct.equals("image/png"))) {
            throw new IllegalArgumentException("Нужен JPG или PNG");
        }
    }

    private static String guessExtension(String name) {
        if (name == null) return ".jpg";
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return ".png";
        return ".jpg";
    }
}