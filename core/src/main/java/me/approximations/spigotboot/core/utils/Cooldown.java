package me.approximations.spigotboot.core.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Cooldown {
    public static final Cooldown INSTANCE = new Cooldown();
    private final Map<String, Instant> cooldowns = new HashMap<>();

    public boolean isOnCoolDown(String key, Duration duration) {
        final Instant lastUsed = cooldowns.get(key);
        if (lastUsed == null || lastUsed.plus(duration).isBefore(Instant.now())) {
            updateCooldown(key);
            return false;
        }

        return true;
    }

    public void updateCooldown(String key) {
        cooldowns.put(key, Instant.now());
    }
}