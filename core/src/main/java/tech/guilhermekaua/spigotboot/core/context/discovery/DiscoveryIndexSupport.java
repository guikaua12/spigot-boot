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

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime support for the {@link DiscoveryIndex} implementations emitted by the
 * {@code DiscoveryIndexProcessor}. Generated indexes list their discovered classes as fully
 * qualified <em>names</em> and resolve them through {@link #resolve(ClassLoader, String...)} instead
 * of referencing them with eager {@code .class} literals.
 *
 * <p>This is what keeps an optional dependency from poisoning discovery: a discovered class may
 * extend or implement a type supplied only by a soft dependency (for example {@code PAPIExpansion},
 * whose supertype {@code me.clip.placeholderapi.expansion.PlaceholderExpansion} is absent unless
 * PlaceholderAPI is installed). Loading such a class throws {@link NoClassDefFoundError}. Resolving
 * names one at a time and skipping the failures means only the unavailable class is dropped, while
 * every other class in the same index still loads. This mirrors the resilience the runtime
 * classpath-scanning fallback already has (see {@code ClassPathScanner} and {@code ModuleDiscovery}).
 *
 * <p>The {@code .class} literals required so the Maven Shade Plugin's {@code minimizeJar} reachability
 * analyzer retains discovered classes are emitted separately, in a never-invoked method on the
 * generated index, so they are present in the bytecode without ever being linked at runtime.
 */
public final class DiscoveryIndexSupport {
    private static final Logger LOGGER = Logger.getLogger(DiscoveryIndexSupport.class.getName());

    private DiscoveryIndexSupport() {
    }

    /**
     * Resolves the given class names with {@code classLoader}, skipping any that cannot be loaded or
     * linked. The class is loaded but not initialized, matching the behavior of a {@code .class}
     * literal.
     *
     * @param classLoader the class loader to resolve names with; never {@code null}
     * @param classNames  the fully qualified names of the discovered classes; may be empty, and
     *                    {@code null}/blank entries are ignored
     * @return the successfully resolved classes, in the order given, with unresolvable names omitted;
     * never {@code null}
     * @throws NullPointerException if {@code classLoader} is {@code null}
     */
    public static Class<?>[] resolve(@NotNull ClassLoader classLoader, String... classNames) {
        Objects.requireNonNull(classLoader, "classLoader cannot be null");
        if (classNames == null || classNames.length == 0) {
            return new Class<?>[0];
        }

        List<Class<?>> resolved = new ArrayList<>(classNames.length);
        for (String className : classNames) {
            if (className == null || className.isEmpty()) {
                continue;
            }
            try {
                resolved.add(Class.forName(className, false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                // the discovered class references a type from an absent optional dependency (or is
                // itself absent); skip it so one unavailable class does not poison the whole index.
                LOGGER.log(Level.FINE, "Skipping discovered class '" + className
                        + "': not loadable on the classpath", e);
            }
        }
        return resolved.toArray(new Class<?>[0]);
    }
}
