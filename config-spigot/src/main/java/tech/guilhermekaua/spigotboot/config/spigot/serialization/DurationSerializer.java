/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
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
package tech.guilhermekaua.spigotboot.config.spigot.serialization;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializer for Duration values.
 * <p>
 * Parses human-readable formats: "30s", "5m", "2h", "1d", "1w"
 */
public class DurationSerializer implements TypeSerializer<Duration> {

    private static final Pattern PATTERN = Pattern.compile(
            "(\\d+)\\s*(ms|s|m|h|d|w)", Pattern.CASE_INSENSITIVE
    );

    @Override
    public Duration deserialize(@NotNull ConfigNode node, @NotNull Class<Duration> type) throws SerializationException {
        Object raw = node.raw();
        if (raw == null) {
            return null;
        }

        if (raw instanceof Number) {
            return Duration.ofSeconds(((Number) raw).longValue());
        }

        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return Duration.ZERO;
        }

        Matcher matcher = PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new SerializationException("Invalid duration format: " + value +
                    ". Expected: <number><unit> (e.g., 30s, 5m, 2h, 1d, 1w)");
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        switch (unit) {
            case "ms":
                return Duration.ofMillis(amount);
            case "s":
                return Duration.ofSeconds(amount);
            case "m":
                return Duration.ofMinutes(amount);
            case "h":
                return Duration.ofHours(amount);
            case "d":
                return Duration.ofDays(amount);
            case "w":
                return Duration.ofDays(amount * 7);
            default:
                throw new SerializationException("Unknown duration unit: " + unit);
        }
    }

    @Override
    public void serialize(@NotNull Duration value, @NotNull MutableConfigNode node) throws SerializationException {
        long millis = value.toMillis();
        if (millis < 1000) {
            node.set(millis + "ms");
        } else if (value.getSeconds() < 60) {
            node.set(value.getSeconds() + "s");
        } else if (value.toMinutes() < 60) {
            node.set(value.toMinutes() + "m");
        } else if (value.toHours() < 24) {
            node.set(value.toHours() + "h");
        } else if (value.toDays() < 7) {
            node.set(value.toDays() + "d");
        } else {
            node.set(value.toDays() / 7 + "w");
        }
    }
}
