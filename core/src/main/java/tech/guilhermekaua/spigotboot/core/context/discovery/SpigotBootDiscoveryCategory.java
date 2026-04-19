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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an annotation or type as the root of a {@link DiscoveryIndex} category. Downstream
 * modules apply this meta-annotation to their own annotations or supertypes to extend
 * discovery without modifying the annotation processor.
 *
 * <p>Example: classify anything implementing a community module's repository interface.
 * <pre>{@code
 * @SpigotBootDiscoveryCategory(value = "my-plugin.repository", kind = Kind.SUBTYPE)
 * public interface CommunityRepository<T, ID> { ... }
 * }</pre>
 *
 * <p>Example: classify anything annotated with a custom stereotype.
 * <pre>{@code
 * @SpigotBootDiscoveryCategory(value = "my-plugin.handler", kind = Kind.ANNOTATION)
 * public @interface MyHandler { }
 * }</pre>
 *
 * <p>At runtime, query the reader with the same category name:
 * <pre>{@code
 * reader.classesInCategory("my-plugin.repository", basePackage);
 * }</pre>
 *
 * <p>{@link RetentionPolicy#CLASS} retention is used so the annotation processor can read this
 * meta-annotation from compiled dependency jars, but the runtime doesn't pay the cost of
 * loading it into reflective metadata.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SpigotBootDiscoveryCategory {

    /**
     * @return the {@link DiscoveryCategories category key} used to register matching classes.
     * Use a namespaced string (e.g. {@code "my-plugin.repository"}) to avoid clashing with
     * spigot-boot's built-in categories or other community modules.
     */
    String value();

    /**
     * @return whether this rule matches annotations on the target class ({@link Kind#ANNOTATION})
     * or supertypes of the target class ({@link Kind#SUBTYPE}).
     */
    Kind kind();

    /**
     * For {@link Kind#ANNOTATION} rules: when {@code true}, classes annotated with any
     * annotation that is (transitively) meta-annotated with the category-bearing annotation
     * also match — mirroring the classic Spring {@code @Component}/{@code @Service} stereotype
     * pattern. Default {@code false} means only direct annotations trigger the rule.
     *
     * <p>Ignored for {@link Kind#SUBTYPE} rules (supertype walks are always transitive).
     */
    boolean transitive() default false;

    enum Kind {
        /**
         * The annotated type is itself an annotation. Classes carrying this annotation directly
         * (or transitively through meta-annotations if {@link #transitive()} is {@code true})
         * are added to the category.
         */
        ANNOTATION,

        /**
         * The annotated type is a class or interface. Classes that extend or implement it are
         * added to the category. The root type itself is excluded.
         */
        SUBTYPE
    }
}
