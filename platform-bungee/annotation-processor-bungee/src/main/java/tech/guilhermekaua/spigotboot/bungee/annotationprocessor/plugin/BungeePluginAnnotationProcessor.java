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
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
            Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(annotation);

            // a single bungee.yml is written per build, so more than one @BungeePlugin would otherwise
            // collide in the Filer (a confusing "Attempt to recreate a file" IOException). fail clearly instead.
            if (annotated.size() > 1) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Only one @BungeePlugin is allowed per plugin, but found " + annotated.size()
                                + "; annotate a single main class.");
                return false;
            }

            for (Element element : annotated) {
                // @BungeePlugin is @Target(TYPE); guard the cast so a malformed/erroneous round yields a
                // clean diagnostic instead of an uncaught ClassCastException escaping the processor.
                if (!(element instanceof TypeElement)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "@BungeePlugin is only valid on a type (class).", element);
                    continue;
                }

                BungeePlugin pluginAnnotation = element.getAnnotation(BungeePlugin.class);
                if (pluginAnnotation == null) {
                    continue;
                }

                try {
                    generateBungeeYml(pluginAnnotation, (TypeElement) element);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not generate bungee.yml: " + e.getMessage(), element);
                    return false;
                }
            }
        }
        return true;
    }

    private void generateBungeeYml(BungeePlugin pluginAnnotation, TypeElement element) throws IOException {
        Filer filer = processingEnv.getFiler();
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "bungee.yml", element);

        // explicit UTF-8 (not the platform default) keeps the generated descriptor byte-identical regardless of the
        // build OS/locale, which the byte-exact tests depend on for any non-ASCII metadata.
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(fileObject.openOutputStream(), StandardCharsets.UTF_8))) {
            // explicit '\n' (not println) keeps the generated descriptor byte-identical regardless of the
            // build OS, which the byte-exact tests depend on. the annotated class is assumed to be the main class.
            writer.print("name: " + yamlScalar(pluginAnnotation.name()) + "\n");
            writer.print("main: " + element.getQualifiedName().toString() + "\n");
            writer.print("version: " + yamlScalar(pluginAnnotation.version()) + "\n");

            if (!pluginAnnotation.author().isEmpty()) {
                writer.print("author: " + yamlScalar(pluginAnnotation.author()) + "\n");
            }
            if (pluginAnnotation.depends().length > 0) {
                writer.print("depends: " + yamlFlowList(pluginAnnotation.depends()) + "\n");
            }
            if (pluginAnnotation.softDepends().length > 0) {
                writer.print("softDepends: " + yamlFlowList(pluginAnnotation.softDepends()) + "\n");
            }
            if (!pluginAnnotation.description().isEmpty()) {
                writer.print("description: " + yamlScalar(pluginAnnotation.description()) + "\n");
            }
            if (pluginAnnotation.libraries().length > 0) {
                writer.print("libraries: " + yamlFlowList(pluginAnnotation.libraries()) + "\n");
            }
        }
    }

    /**
     * Renders an annotation value as a YAML scalar. Plain identifiers are emitted verbatim so descriptors stay
     * byte-identical to the Spigot writer; only values carrying YAML-significant characters are single-quoted
     * (with embedded {@code '} doubled), so a description like {@code Proxy: auth} or an author containing
     * {@code '} or {@code #} still produces a parseable {@code bungee.yml}.
     */
    private static String yamlScalar(String value) {
        if (!needsQuoting(value)) {
            return value;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * Renders a string array as a YAML flow list, quoting each entry only when it needs it (see
     * {@link #yamlScalar(String)}). Library coordinates such as {@code group:artifact:version} carry a colon
     * and are therefore quoted, matching the previously hard-coded single-quoting.
     */
    private static String yamlFlowList(String[] values) {
        return Arrays.stream(values)
                .map(BungeePluginAnnotationProcessor::yamlScalar)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static boolean needsQuoting(String value) {
        if (value.isEmpty() || !value.equals(value.trim())) {
            // empty, or has leading/trailing whitespace a plain scalar would silently strip.
            return true;
        }
        switch (value.charAt(0)) {
            // indicators that only carry special meaning when they open a scalar.
            case '!': case '&': case '*': case '?': case '|': case '>':
            case '@': case '%': case '`': case '-':
                return true;
            default:
                break;
        }
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                // characters significant anywhere in a block scalar or a flow-list item.
                case ':': case '#': case '\'': case '"':
                case '\n': case '\r': case '\t':
                case ',': case '[': case ']': case '{': case '}':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
}
