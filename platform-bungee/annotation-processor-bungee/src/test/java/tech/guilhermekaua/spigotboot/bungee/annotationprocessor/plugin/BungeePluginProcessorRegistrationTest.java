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
package tech.guilhermekaua.spigotboot.bungee.annotationprocessor.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeePluginProcessorRegistrationTest {

    // proves the SPI service file is present and names the processor, so javac auto-discovers it from a
    // downstream plugin's annotationProcessorPaths exactly as it does the Spigot PluginAnnotationProcessor.
    @Test
    void processorIsRegisteredAsAnnotationProcessorService() throws IOException {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("META-INF/services/javax.annotation.processing.Processor")) {
            assertNotNull(in, "the SPI registration file must be present on the classpath");
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(content.contains(BungeePluginAnnotationProcessor.class.getName()),
                    "META-INF/services/javax.annotation.processing.Processor must register "
                            + "BungeePluginAnnotationProcessor so javac auto-discovers it");
        }
    }
}
