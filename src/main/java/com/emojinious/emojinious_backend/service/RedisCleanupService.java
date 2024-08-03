package com.emojinious.emojinious_backend.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisCleanupService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void clearAllKeys() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }
    }
}