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

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.StandardLocation;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BungeePluginAnnotationProcessorTest {

    private Compilation compile(String source) {
        return javac()
                .withProcessors(new BungeePluginAnnotationProcessor())
                .compile(JavaFileObjects.forSourceString("com.example.Main", source));
    }

    @Test
    void generatesFullDescriptorWithAllFields() {
        Compilation compilation = compile(
                "package com.example;\n" +
                "import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;\n" +
                "@BungeePlugin(\n" +
                "        name = \"MyProxyPlugin\",\n" +
                "        version = \"1.0.0\",\n" +
                "        author = \"Approximations\",\n" +
                "        depends = {\"SomeOther\"},\n" +
                "        softDepends = {\"Optional\"},\n" +
                "        description = \"A proxy plugin.\",\n" +
                "        libraries = {\"com.squareup.okhttp3:okhttp:4.12.0\"}\n" +
                ")\n" +
                "public class Main {}\n");

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "", "bungee.yml")
                .contentsAsUtf8String()
                .isEqualTo(
                        "name: MyProxyPlugin\n" +
                        "main: com.example.Main\n" +
                        "version: 1.0.0\n" +
                        "author: Approximations\n" +
                        "depends: [SomeOther]\n" +
                        "softDepends: [Optional]\n" +
                        "description: A proxy plugin.\n" +
                        "libraries: ['com.squareup.okhttp3:okhttp:4.12.0']\n");
    }

    @Test
    void generatesOnlyRequiredFieldsWhenOptionalsAbsent() {
        Compilation compilation = compile(
                "package com.example;\n" +
                "import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;\n" +
                "@BungeePlugin(name = \"MyProxyPlugin\", version = \"1.0.0\")\n" +
                "public class Main {}\n");

        assertThat(compilation).succeeded();
        assertThat(compilation)
                .generatedFile(StandardLocation.CLASS_OUTPUT, "", "bungee.yml")
                .contentsAsUtf8String()
                .isEqualTo(
                        "name: MyProxyPlugin\n" +
                        "main: com.example.Main\n" +
                        "version: 1.0.0\n");
    }

    @Test
    void usesBungeeCamelCaseDependencyKeysNotSpigotKeys() throws Exception {
        Compilation compilation = compile(
                "package com.example;\n" +
                "import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;\n" +
                "@BungeePlugin(name = \"MyProxyPlugin\", version = \"1.0.0\",\n" +
                "        depends = {\"A\", \"B\"}, softDepends = {\"C\"})\n" +
                "public class Main {}\n");

        assertThat(compilation).succeeded();
        String yml = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, "", "bungee.yml")
                .orElseThrow(() -> new AssertionError("bungee.yml was not generated"))
                .getCharContent(true)
                .toString();

        assertTrue(yml.contains("depends: [A, B]"), "should emit camelCase depends as a flow list");
        assertTrue(yml.contains("softDepends: [C]"), "should emit camelCase softDepends as a flow list");
        assertFalse(yml.contains("depend:"), "must not emit the Spigot-style 'depend' key");
        assertFalse(yml.contains("softdepend:"), "must not emit the Spigot-style 'softdepend' key");
    }

    @Test
    void quotesYamlSensitiveValuesSoTheDescriptorStaysParseable() throws Exception {
        Compilation compilation = compile(
                "package com.example;\n" +
                "import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;\n" +
                "@BungeePlugin(\n" +
                "        name = \"MyProxyPlugin\",\n" +
                "        version = \"1.0.0\",\n" +
                "        author = \"O'Brien\",\n" +
                "        description = \"Proxy: auth #1\"\n" +
                ")\n" +
                "public class Main {}\n");

        assertThat(compilation).succeeded();
        String yml = compilation
                .generatedFile(StandardLocation.CLASS_OUTPUT, "", "bungee.yml")
                .orElseThrow(() -> new AssertionError("bungee.yml was not generated"))
                .getCharContent(true)
                .toString();

        // a colon, hash, or apostrophe in a free-text field would otherwise yield invalid/misparsed YAML.
        assertTrue(yml.contains("name: MyProxyPlugin\n"), "plain identifiers must stay unquoted");
        assertTrue(yml.contains("version: 1.0.0\n"), "plain versions must stay unquoted");
        assertTrue(yml.contains("author: 'O''Brien'\n"), "embedded apostrophes must be doubled inside a single-quoted scalar");
        assertTrue(yml.contains("description: 'Proxy: auth #1'\n"), "colon/hash values must be single-quoted");
    }
}
