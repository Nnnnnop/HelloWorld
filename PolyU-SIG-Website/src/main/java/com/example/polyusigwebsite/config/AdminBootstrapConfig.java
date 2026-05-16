package com.example.polyusigwebsite.config;

import com.example.polyusigwebsite.entity.RoleType;
import com.example.polyusigwebsite.entity.UserAccount;
import com.example.polyusigwebsite.entity.UserStatus;
import com.example.polyusigwebsite.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrapConfig {

    @Bean
    CommandLineRunner ensureAdmin(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${sig.admin.username:admin}") String adminUsername,
            @Value("${sig.admin.email:admin@sig.local}") String adminEmail,
            @Value("${sig.admin.password:Admin@123456}") String adminPassword
    ) {
        return args -> {
            UserAccount admin = userAccountRepository.findByUsername(adminUsername).orElseGet(UserAccount::new);
            admin.setUsername(adminUsername);
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setRole(RoleType.ADMIN);
            admin.setStatus(UserStatus.APPROVED);
            admin.setProvider("LOCAL");
            userAccountRepository.save(admin);
        };
    }
}
