package ru.itis.documents.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

public class ValidImageFileValidator implements ConstraintValidator<ValidImageFile, MultipartFile> {

    private static final long MAX_BYTES = 10L * 1024L * 1024L; // 10 MB

    @Override
    public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) return false;
        if (value.getSize() > MAX_BYTES) return false;

        String contentType = value.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }
}