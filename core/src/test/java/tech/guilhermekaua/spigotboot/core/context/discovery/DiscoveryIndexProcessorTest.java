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
package tech.guilhermekaua.spigotboot.core.context.discovery;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.spigot.annotationprocessor.discovery.DiscoveryIndexProcessor;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryIndexProcessorTest {

    private static Compilation compileComponent() {
        return javac()
                .withProcessors(new DiscoveryIndexProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.MyComponent",
                        "package com.example;\n" +
                        "import tech.guilhermekaua.spigotboot.core.context.annotations.Component;\n" +
                        "@Component\n" +
                        "public class MyComponent {}\n"));
    }

    private static String generatedIndexSource(Compilation compilation) throws Exception {
        for (JavaFileObject file : compilation.generatedSourceFiles()) {
            if (file.getName().contains("DiscoveryIndex_")) {
                return file.getCharContent(true).toString();
            }
        }
        throw new AssertionError("no DiscoveryIndex_<hash> source was generated");
    }

    @Test
    void generatedIndexResolvesDiscoveredClassesByNameInsteadOfEagerClassLiterals() throws Exception {
        Compilation compilation = compileComponent();
        assertThat(compilation).succeeded();

        String generated = generatedIndexSource(compilation);

        assertTrue(generated.contains("DiscoveryIndexSupport.resolve(cl,"),
                "ENTRIES must be built by resolving class names, so an absent optional dependency cannot crash the index");
        assertTrue(generated.contains("\"com.example.MyComponent\""),
                "the discovered class must be listed as a name string, not an eager .class literal");
        assertFalse(generated.contains("m.put(\"component\", new Class<?>[] {"),
                "ENTRIES must not eagerly link discovered classes via .class literals");
    }

    @Test
    void generatedIndexKeepsClassLiteralsInANeverInvokedAnchorForMinimizeJar() throws Exception {
        Compilation compilation = compileComponent();
        assertThat(compilation).succeeded();

        String generated = generatedIndexSource(compilation);

        assertTrue(generated.contains("private static Class<?>[] keepReachable()"),
                "a never-invoked anchor method must hold the .class literals for the minimizer");
        assertTrue(generated.contains("com.example.MyComponent.class"),
                "the discovered class must appear as a .class literal so minimizeJar keeps it");
    }
}
