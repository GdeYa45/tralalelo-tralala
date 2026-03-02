package ru.itis.documents.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.Role;
import ru.itis.documents.dto.RegisterForm;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.RoleRepository;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser register(RegisterForm form) {
        String email = normalizeEmail(form.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("Пользователь с таким email уже существует");
        }

        Role roleUser = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Роль ROLE_USER не найдена. Проверь миграции."));

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.getRoles().add(roleUser);

        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String message) {
            super(message);
        }
    }
}