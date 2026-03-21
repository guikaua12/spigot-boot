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

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class CooldownState {
    private static final CooldownState INACTIVE = new CooldownState(false, Duration.ZERO, null);

    private final boolean active;
    private final Duration remaining;
    private final Instant expiresAt;

    private CooldownState(boolean active, Duration remaining, @Nullable Instant expiresAt) {
        this.active = active;
        this.remaining = Objects.requireNonNull(remaining, "remaining cannot be null.");
        this.expiresAt = expiresAt;
    }

    public static CooldownState inactive() {
        return INACTIVE;
    }

    public static CooldownState active(Duration remaining, Instant expiresAt) {
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null.");

        Duration resolvedRemaining = Objects.requireNonNull(remaining, "remaining cannot be null.");
        if (resolvedRemaining.isNegative() || resolvedRemaining.isZero()) {
            return inactive();
        }

        return new CooldownState(true, resolvedRemaining, expiresAt);
    }

    public boolean isActive() {
        return active;
    }

    public Duration getRemaining() {
        return remaining;
    }

    public @Nullable Instant getExpiresAt() {
        return expiresAt;
    }
}
