package com.trustplatform.auth.config;

import com.trustplatform.auth.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    @Bean
    CommandLineRunner bootstrapAdminUser(
            UserRepository userRepository,
            @Value("${app.bootstrap.admin-email:}") String adminEmail
    ) {
        return args -> {
            if (adminEmail == null || adminEmail.isBlank()) {
                return;
            }

            userRepository.findByEmail(adminEmail.strip())
                    .ifPresentOrElse(user -> {
                        if ("ADMIN".equals(user.getRole())) {
                            log.info("Bootstrap admin '{}' is already assigned ADMIN role", user.getEmail());
                            return;
                        }

                        user.setRole("ADMIN");
                        userRepository.save(user);
                        log.info("Promoted bootstrap admin '{}' to ADMIN role", user.getEmail());
                    }, () -> log.warn(
                            "Bootstrap admin email '{}' was configured but no matching user exists yet",
                            adminEmail.strip()
                    ));
        };
    }
}
