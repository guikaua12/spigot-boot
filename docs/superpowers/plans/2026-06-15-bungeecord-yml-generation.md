# BungeeCord bungee.yml Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `platform-bungee/annotation-processor-bungee` module that generates a valid BungeeCord `bungee.yml` descriptor at build time from a new `@BungeePlugin` annotation, mirroring how the Spigot annotation processor generates `plugin.yml`.

**Architecture:** A new, isolated annotation-processor module under the existing `platform-bungee` tree (parent `spigot-boot-platform-bungee`). It defines the `@BungeePlugin` annotation (Bungee-only descriptor elements) and `BungeePluginAnnotationProcessor`, a near-mechanical mirror of the Spigot `PluginAnnotationProcessor`: the annotated class becomes `main`, and the YAML is hand-written into `StandardLocation.CLASS_OUTPUT` (`bungee.yml` at the jar root). The module is independent of `core-bungee`, the Spigot processor, and the Bungee API — it uses only `javax.annotation.processing`/`javax.lang.model`. This isolation keeps the high-blast-radius shared Spigot processor untouched and lets the work proceed concurrently with the sibling `commands-bungee`/`config-bungee` slices.

**Tech Stack:** Java 8 bytecode (tests Java 17), Maven, `com.google.testing.compile:compile-testing:0.21.0` (test scope, real in-memory annotation-processor TDD), JUnit 5 (inherited from the root pom). No BungeeCord API dependency. Spec: `docs/superpowers/specs/2026-06-15-bungeecord-yml-generation-design.md`.

**Build/JDK notes (read once):**
- Build with **JDK 21** (`JAVA_HOME` must point at JDK 21, e.g. `C:/Users/Guilherme/.jdks/ms-21.0.10`, **not** 25 — Lombok 1.18.36 crashes on 25). This module does not use Lombok, but the reactor build does.
- All commands run from the worktree root: `C:\Users\Guilherme\IdeaProjects\spigot-boot\.claude\worktrees\bungeecord-yml`.
- Every command passes `-Danimal.sniffer.skip=true` defensively (this module declares no animal-sniffer check, but the reactor has the 1.8.8 signature plugin and a stale signature must never block the build).
- Filtered runs (`-pl … -am -Dtest=…`) **must** add `-Dsurefire.failIfNoSpecifiedTests=false`: `-am` also builds the upstream reactor modules (`core`, `utils`, …), none of which contain the named test class, so without this flag surefire fails them with "No tests were executed".
- Every new `.java` file MUST begin with the project's MIT license header (the full block is shown in Task 2, `Copyright © 2025`, to match existing files and the root `license-maven-plugin`). XML and SPI resource files do not take the header.
- `platform-bungee` is **already** registered in the root `pom.xml` `<modules>` (from the core-bungee v1 slice), so the root pom is **not** modified by this plan — only `platform-bungee/pom.xml`.

---

## File Structure

**Created:**
- `platform-bungee/annotation-processor-bungee/pom.xml` — the new processor module; Java 8 main / Java 17 tests, `<proc>none</proc>`, one test-scoped dep (`compile-testing`). No Bungee API, no shade, no animal-sniffer.
- `platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/annotations/BungeePlugin.java` — the `@BungeePlugin` source annotation.
- `platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessor.java` — the descriptor writer.
- `platform-bungee/annotation-processor-bungee/src/main/resources/META-INF/services/javax.annotation.processing.Processor` — SPI registration so `javac` auto-discovers the processor.
- `platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessorTest.java` — generation tests (compile-testing).
- `platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginProcessorRegistrationTest.java` — SPI registration test.

**Modified:**
- `platform-bungee/pom.xml` — add `<module>annotation-processor-bungee</module>` to `<modules>` (**the merge-conflict point** shared with the sibling `commands-bungee`/`config-bungee` slices).

---

## Task 1: Scaffold the `annotation-processor-bungee` Maven module

**Files:**
- Create: `platform-bungee/annotation-processor-bungee/pom.xml`
- Modify: `platform-bungee/pom.xml`

- [ ] **Step 1: Create the module pom `platform-bungee/annotation-processor-bungee/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>tech.guilhermekaua.spigot-boot</groupId>
        <artifactId>spigot-boot-platform-bungee</artifactId>
        <version>3.2.1-SNAPSHOT</version>
    </parent>

    <name>${project.artifactId}</name>
    <description>Annotation processor that generates the BungeeCord bungee.yml descriptor from @BungeePlugin.</description>
    <url>https://github.com/guikaua12/spigot-boot</url>
    <artifactId>spigot-boot-annotation-processor-bungee</artifactId>

    <properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                    <testSource>17</testSource>
                    <testTarget>17</testTarget>
                    <!-- disable annotation processing while compiling this module so javac does not try to
                         run our own (not-yet-compiled) processor on itself, mirroring the Spigot processor pom. -->
                    <proc>none</proc>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>com.google.testing.compile</groupId>
            <artifactId>compile-testing</artifactId>
            <version>0.21.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Note: JUnit 5 (junit-jupiter) is declared `test`-scoped in the **root** pom `<dependencies>`, so it is inherited — do not redeclare it. `compile-testing` transitively brings Truth + Guava (all test scope); `com.google.testing.compile.CompilationSubject` and `Compiler.javac()` come from it.

- [ ] **Step 2: Register the module in `platform-bungee/pom.xml`**

In `platform-bungee/pom.xml`, change the `<modules>` block from:

```xml
    <modules>
        <module>core-bungee</module>
    </modules>
```

to (add the new module immediately after `core-bungee` to minimize conflict churn with the sibling slices):

```xml
    <modules>
        <module>core-bungee</module>
        <module>annotation-processor-bungee</module>
    </modules>
```

- [ ] **Step 3: Verify the scaffolding resolves and the reactor wiring is correct**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS`. The module has no sources yet, so the compiler reports "No sources to compile" — that is fine. This proves the parent resolves, the module is wired into the reactor, and the pom is valid. (`compile` is used deliberately so the `package`-bound javadoc/source jars are not invoked on an empty module. The `compile-testing` test dependency is exercised in Task 3.)

- [ ] **Step 4: Commit**

```bash
git add platform-bungee/pom.xml platform-bungee/annotation-processor-bungee/pom.xml
git commit -m "build(bungee): scaffold annotation-processor-bungee module"
```

---

## Task 2: `@BungeePlugin` descriptor annotation

**Files:**
- Create: `platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/annotations/BungeePlugin.java`

There is no behavior to unit-test in a pure annotation declaration; it is verified by compilation here and exercised end-to-end by the processor tests in Task 3.

- [ ] **Step 1: Write the annotation**

```java
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
package tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the BungeeCord plugin descriptor on the plugin's main class (the class that extends
 * {@code net.md_5.bungee.api.plugin.Plugin}). {@code BungeePluginAnnotationProcessor} reads this
 * annotation at compile time and emits a {@code bungee.yml} into the jar root, mirroring how the
 * Spigot {@code @Plugin} annotation drives {@code plugin.yml}.
 *
 * <p>The annotated class itself becomes the descriptor's {@code main} entry. {@code name} and
 * {@code version} are required; every other element is optional and is omitted from the generated
 * descriptor when left at its blank/empty default.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface BungeePlugin {
    /**
     * The plugin name BungeeCord registers the plugin under.
     *
     * @return the plugin name (required)
     */
    String name();

    /**
     * The plugin version.
     *
     * @return the plugin version (required)
     */
    String version();

    /**
     * The single plugin author. BungeeCord's descriptor has no plural {@code authors} key, unlike
     * Spigot's.
     *
     * @return the author, or an empty string to omit the key
     */
    String author() default "";

    /**
     * Hard dependencies that must be present and load before this plugin. Emitted under the
     * BungeeCord {@code depends} key (camelCase, distinct from Spigot's {@code depend}).
     *
     * @return the hard-dependency plugin names
     */
    String[] depends() default {};

    /**
     * Soft dependencies loaded before this plugin when present. Emitted under the BungeeCord
     * {@code softDepends} key (camelCase, distinct from Spigot's {@code softdepend}).
     *
     * @return the soft-dependency plugin names
     */
    String[] softDepends() default {};

    /**
     * A human-readable description.
     *
     * @return the description, or an empty string to omit the key
     */
    String description() default "";

    /**
     * Runtime Maven library coordinates ({@code group:artifact:version}) that BungeeCord 1.21+
     * downloads via its library loader. Emitted under the {@code libraries} key.
     *
     * @return the library coordinates
     */
    String[] libraries() default {};
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true compile`
Expected: `BUILD SUCCESS` (one source file compiled to Java 8 bytecode).

- [ ] **Step 3: Commit**

```bash
git add platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/annotations/BungeePlugin.java
git commit -m "feat(bungee): add @BungeePlugin descriptor annotation"
```

---

## Task 3: `BungeePluginAnnotationProcessor` (bungee.yml writer)

**Files:**
- Create: `platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessor.java`
- Test: `platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessorTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeePluginAnnotationProcessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILE FAILURE — `BungeePluginAnnotationProcessor` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
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

import tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates a BungeeCord {@code bungee.yml} descriptor at compile time from {@link BungeePlugin}.
 * Mirrors the Spigot {@code PluginAnnotationProcessor}: the annotated type is taken as the descriptor
 * {@code main} class, and the YAML is hand-written into {@link StandardLocation#CLASS_OUTPUT} so it
 * lands at the jar root, where BungeeCord's {@code PluginManager} reads it (preferring {@code bungee.yml}
 * over {@code plugin.yml}).
 */
@SupportedAnnotationTypes("tech.guilhermekaua.spigotboot.bungee.annotationprocessor.annotations.BungeePlugin")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BungeePluginAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                BungeePlugin pluginAnnotation = element.getAnnotation(BungeePlugin.class);

                if (pluginAnnotation == null) {
                    continue;
                }

                try {
                    generateBungeeYml(pluginAnnotation, element);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not generate bungee.yml: " + e.getMessage(), element);
                    return false;
                }
            }
        }
        return true;
    }

    private void generateBungeeYml(BungeePlugin pluginAnnotation, Element element) throws IOException {
        Filer filer = processingEnv.getFiler();
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "bungee.yml", element);

        try (PrintWriter writer = new PrintWriter(fileObject.openWriter())) {
            // explicit '\n' (not println) keeps the generated descriptor byte-identical regardless of the
            // build OS, which the byte-exact tests depend on. the annotated class is assumed to be the main class.
            writer.print("name: " + pluginAnnotation.name() + "\n");
            writer.print("main: " + ((TypeElement) element).getQualifiedName().toString() + "\n");
            writer.print("version: " + pluginAnnotation.version() + "\n");

            if (!pluginAnnotation.author().isEmpty()) {
                writer.print("author: " + pluginAnnotation.author() + "\n");
            }
            if (pluginAnnotation.depends().length > 0) {
                writer.print("depends: [" + String.join(", ", pluginAnnotation.depends()) + "]\n");
            }
            if (pluginAnnotation.softDepends().length > 0) {
                writer.print("softDepends: [" + String.join(", ", pluginAnnotation.softDepends()) + "]\n");
            }
            if (!pluginAnnotation.description().isEmpty()) {
                writer.print("description: " + pluginAnnotation.description() + "\n");
            }
            if (pluginAnnotation.libraries().length > 0) {
                writer.print("libraries: [" + Arrays.stream(pluginAnnotation.libraries()).map(lib -> "'" + lib + "'").collect(Collectors.joining(", ")) + "]\n");
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeePluginAnnotationProcessorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests). A benign note may appear — "Supported source version 'RELEASE_8' … less than -source '21'" — because the processor targets Java 8 while the JDK-21 in-test compiler defaults to source 21. It is a note, not an error, so `succeeded()` still holds. (The processor uses only public `javax.*` APIs, so compile-testing needs no `--add-exports`; if a future change touches `com.sun.source.*` internals and triggers an access error, add the standard `--add-exports jdk.compiler/...=ALL-UNNAMED` flags via a surefire `argLine`.)

- [ ] **Step 5: Commit**

```bash
git add platform-bungee/annotation-processor-bungee/src/main/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessor.java platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginAnnotationProcessorTest.java
git commit -m "feat(bungee): generate bungee.yml from @BungeePlugin"
```

---

## Task 4: Register the processor via SPI

**Files:**
- Create: `platform-bungee/annotation-processor-bungee/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
- Test: `platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginProcessorRegistrationTest.java`

The Task 3 tests instantiate the processor directly, so they pass without SPI. This task adds the registration that lets `javac` auto-discover the processor when a downstream plugin puts the module on its `annotationProcessorPaths`, and guards it with a test.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeePluginProcessorRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `assertNotNull` fails because the SPI resource does not exist yet (`getResourceAsStream` returns `null`).

- [ ] **Step 3: Create the SPI registration resource**

File: `platform-bungee/annotation-processor-bungee/src/main/resources/META-INF/services/javax.annotation.processing.Processor`

Content (single line, the processor FQCN, no license header — this is a service file, not Java):

```
tech.guilhermekaua.spigotboot.bungee.annotationprocessor.plugin.BungeePluginAnnotationProcessor
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test -Dtest=BungeePluginProcessorRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (1 test). If it still fails, confirm the file landed in `target/classes/META-INF/services/` after the build (Maven copies `src/main/resources` during `process-resources`).

- [ ] **Step 5: Commit**

```bash
git add "platform-bungee/annotation-processor-bungee/src/main/resources/META-INF/services/javax.annotation.processing.Processor" platform-bungee/annotation-processor-bungee/src/test/java/tech/guilhermekaua/spigotboot/bungee/annotationprocessor/plugin/BungeePluginProcessorRegistrationTest.java
git commit -m "feat(bungee): register bungee.yml processor via SPI"
```

---

## Task 5: Full-module and reactor verification

**Files:** none (verification only — no new commit).

- [ ] **Step 1: Run the full module test suite**

Run: `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test`
Expected: PASS — all tests across `BungeePluginAnnotationProcessorTest` (3) and `BungeePluginProcessorRegistrationTest` (1) = **4 tests, 0 failures**.

- [ ] **Step 2: Verify the whole reactor still builds**

Run: `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests`
Expected: `BUILD SUCCESS` for every module including `spigot-boot-platform-bungee` and the new `spigot-boot-annotation-processor-bungee`. This confirms adding the module did not disturb the existing reactor and that the license-header check passes on the new `.java` files.

- [ ] **Step 3: Confirm the working tree is clean**

Run: `git status`
Expected: clean (all changes committed across Tasks 1–4). If `target/` artifacts appear, they are build output and are already git-ignored.

---

## Done criteria

- `mvnw.cmd -pl platform-bungee/annotation-processor-bungee -am -Danimal.sniffer.skip=true test` is green (4 tests).
- `mvnw.cmd -Danimal.sniffer.skip=true install -DskipTests` builds the full reactor including the new module.
- A Bungee plugin author can annotate a `net.md_5.bungee.api.plugin.Plugin` subclass with `@BungeePlugin(name = …, version = …, …)`, add `spigot-boot-annotation-processor-bungee` to their `annotationProcessorPaths`, and a correct `bungee.yml` (using `depends`/`softDepends`/`author`/`libraries`) is emitted to the jar root at build time — no hand-written descriptor.
- `@BungeePlugin` is `SOURCE`-retained and the processor module carries no runtime dependency, so nothing new ships inside the downstream plugin jar.

## Out of scope (later slices, separate plans)

- `commands-bungee`, `config-bungee` (concurrent sibling slices).
- `${project.version}` / Maven-property interpolation in descriptor metadata (hardcoded literals, matching `test-plugin`'s `@Plugin`).
- Main-class subtype validation (the processor does not verify the annotated class extends Bungee's `Plugin`, mirroring the Spigot processor).
- A runnable BungeeCord sample/test plugin and on-server end-to-end validation that loads a generated `bungee.yml`.
