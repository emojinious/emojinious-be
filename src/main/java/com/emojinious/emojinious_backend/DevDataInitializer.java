package com.emojinious.emojinious_backend;

import com.emojinious.emojinious_backend.service.RedisCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final RedisCleanupService redisCleanupService;

    @Override
    public void run(ApplicationArguments args) {
        redisCleanupService.clearAllKeys();
        System.out.println("All Redis keys have been cleared in the development environment.");
    }
}