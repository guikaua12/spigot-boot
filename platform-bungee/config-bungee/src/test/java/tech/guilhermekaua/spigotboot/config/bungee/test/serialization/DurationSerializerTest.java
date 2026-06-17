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
package tech.guilhermekaua.spigotboot.config.bungee.test.serialization;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.exception.SerializationException;
import tech.guilhermekaua.spigotboot.config.bungee.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.config.bungee.serialization.DurationSerializer;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationSerializerTest {

    private final DurationSerializer serializer = new DurationSerializer();

    @Test
    void deserializesHumanReadableUnits() {
        assertEquals(Duration.ofMillis(500), serializer.deserialize(new YamlConfigNode("500ms"), Duration.class));
        assertEquals(Duration.ofSeconds(30), serializer.deserialize(new YamlConfigNode("30s"), Duration.class));
        assertEquals(Duration.ofMinutes(5), serializer.deserialize(new YamlConfigNode("5m"), Duration.class));
        assertEquals(Duration.ofHours(2), serializer.deserialize(new YamlConfigNode("2h"), Duration.class));
        assertEquals(Duration.ofDays(1), serializer.deserialize(new YamlConfigNode("1d"), Duration.class));
        assertEquals(Duration.ofDays(7), serializer.deserialize(new YamlConfigNode("1w"), Duration.class));
    }

    @Test
    void deserializesPlainNumberAsSeconds() {
        assertEquals(Duration.ofSeconds(45), serializer.deserialize(new YamlConfigNode(45), Duration.class));
    }

    @Test
    void rejectsInvalidFormat() {
        assertThrows(SerializationException.class,
                () -> serializer.deserialize(new YamlConfigNode("not-a-duration"), Duration.class));
    }

    @Test
    void serializesToCompactUnit() {
        YamlConfigNode node = new YamlConfigNode();
        serializer.serialize(Duration.ofMinutes(5), node);
        assertEquals("5m", node.raw());
    }
}
