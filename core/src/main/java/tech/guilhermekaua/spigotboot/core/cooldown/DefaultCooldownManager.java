/*
 * The MIT License
 * Copyright © 2025 Guilherme Kaua da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.cooldown;

import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultCooldownManager implements CooldownManager {
    private final Clock clock;
    private final ConcurrentMap<String, Instant> cooldowns = new ConcurrentHashMap<>();

    public DefaultCooldownManager() {
        this(Clock.systemUTC());
    }

    public DefaultCooldownManager(@NotNull Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null.");
    }

    @Override
    public CooldownState getState(String key) {
        Objects.requireNonNull(key, "key cannot be null.");

        Instant expiresAt = cooldowns.get(key);
        if (expiresAt == null) {
            return CooldownState.inactive();
        }

        Instant now = Instant.now(clock);
        if (!expiresAt.isAfter(now)) {
            if (!cooldowns.remove(key, expiresAt)) {
                Instant newExpires = cooldowns.get(key);
                if (newExpires != null && newExpires.isAfter(now)) {
                    return CooldownState.active(Duration.between(now, newExpires), newExpires);
                }
            }
            return CooldownState.inactive();
        }

        return CooldownState.active(Duration.between(now, expiresAt), expiresAt);
    }

    @Override
    public CooldownState start(String key, Duration duration) {
        Objects.requireNonNull(key, "key cannot be null.");
        Objects.requireNonNull(duration, "duration cannot be null.");

        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration cannot be negative.");
        }

        if (duration.isZero()) {
            clear(key);
            return CooldownState.inactive();
        }

        Instant expiresAt = Instant.now(clock).plus(duration);
        cooldowns.put(key, expiresAt);
        return CooldownState.active(duration, expiresAt);
    }

    @Override
    public void clear(String key) {
        Objects.requireNonNull(key, "key cannot be null.");
        cooldowns.remove(key);
    }

    @Override
    public void clearAll() {
        cooldowns.clear();
    }
}
