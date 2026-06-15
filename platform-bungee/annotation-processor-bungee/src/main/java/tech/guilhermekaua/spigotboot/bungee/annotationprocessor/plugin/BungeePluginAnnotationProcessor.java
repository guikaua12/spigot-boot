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
