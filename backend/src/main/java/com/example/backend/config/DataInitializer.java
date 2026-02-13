package com.example.backend.config;

import com.example.backend.user.AppUser;
import com.example.backend.user.AppUserRepository;
import com.example.backend.user.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createUserIfMissing("admin", "admin123", UserRole.ADMIN);
        createUserIfMissing("operator", "operator123", UserRole.OPERATOR);
        createUserIfMissing("viewer", "viewer123", UserRole.VIEWER);
    }

    private void createUserIfMissing(String username, String rawPassword, UserRole role) {
        if (appUserRepository.existsByUsername(username)) {
            return;
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        appUserRepository.save(user);
    }
}
