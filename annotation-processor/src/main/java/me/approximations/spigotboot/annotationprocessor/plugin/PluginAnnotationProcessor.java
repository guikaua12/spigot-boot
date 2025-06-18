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
package me.approximations.spigotboot.annotationprocessor.plugin;

import me.approximations.spigotboot.annotationprocessor.annotations.Plugin;

import javax.annotation.processing.*;
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

@SupportedAnnotationTypes("me.approximations.spigotBoot.annotationProcessor.plugin.annotations.Plugin")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PluginAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                Plugin pluginAnnotation = element.getAnnotation(Plugin.class);

                if (pluginAnnotation == null) {
                    continue;
                }

                try {
                    generatePluginYml(pluginAnnotation, element);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not generate plugin.yml: " + e.getMessage(), element);
                    return false;
                }
            }
        }
        return true;
    }

    private void generatePluginYml(Plugin pluginAnnotation, Element element) throws IOException {
        Filer filer = processingEnv.getFiler();
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.yml", element);

        try (PrintWriter writer = new PrintWriter(fileObject.openWriter())) {
            writer.println("name: " + pluginAnnotation.name());
            writer.println("version: " + pluginAnnotation.version());
            writer.println("main: " + ((TypeElement) element).getQualifiedName().toString()); // Assumes the annotated class is the main class

            if (!pluginAnnotation.description().isEmpty()) {
                writer.println("description: " + pluginAnnotation.description());
            }
            if (pluginAnnotation.load() != PluginLoadOrder.POSTWORLD) {
                writer.println("load: " + pluginAnnotation.load().name());
            }
            if (!pluginAnnotation.author().isEmpty()) {
                writer.println("author: " + pluginAnnotation.author());
            }
            if (pluginAnnotation.authors().length > 0) {
                writer.println("authors: [" + String.join(", ", pluginAnnotation.authors()) + "]");
            }
            if (!pluginAnnotation.website().isEmpty()) {
                writer.println("website: " + pluginAnnotation.website());
            }
            if (pluginAnnotation.depend().length > 0) {
                writer.println("depend: [" + String.join(", ", pluginAnnotation.depend()) + "]");
            }
            if (pluginAnnotation.softdepend().length > 0) {
                writer.println("softdepend: [" + String.join(", ", pluginAnnotation.softdepend()) + "]");
            }
            if (pluginAnnotation.loadbefore().length > 0) {
                writer.println("loadbefore: [" + String.join(", ", pluginAnnotation.loadbefore()) + "]");
            }
            if (!pluginAnnotation.prefix().isEmpty()) {
                writer.println("prefix: " + pluginAnnotation.prefix());
            }
            if (pluginAnnotation.libraries().length > 0) {
                writer.println("libraries: [" + Arrays.stream(pluginAnnotation.libraries()).map(lib -> "'" + lib + "'").collect(Collectors.joining(", ")) + "]");
            }
            if (!pluginAnnotation.apiVersion().isEmpty()) {
                writer.println("api-version: " + pluginAnnotation.apiVersion());
            }
        }
    }
}

