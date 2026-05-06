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
package tech.guilhermekaua.spigotboot.spigot.annotationprocessor.discovery;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates a compile-time discovery index covering every class the spigot-boot runtime would
 * otherwise discover by classpath scanning. For each compilation unit that contains at least one
 * indexable class, the processor emits:
 * <ul>
 *   <li>A source file {@code tech.guilhermekaua.spigotboot.generated.DiscoveryIndex_<hash>}
 *       implementing {@code DiscoveryIndex}. The map of categories references each discovered
 *       class via a {@code .class} literal so the Maven Shade Plugin's reachability analyzer
 *       keeps them when {@code minimizeJar} is enabled.</li>
 *   <li>An empty marker resource at
 *       {@code META-INF/spigot-boot/discovery/<GeneratedClassFQCN>} so
 *       {@code DiscoveryIndexReader} can locate the generated class at runtime.</li>
 * </ul>
 *
 * <p>Categories are not hardcoded. The processor discovers them dynamically by reading
 * {@code @SpigotBootDiscoveryCategory} meta-annotations on annotations and supertypes reachable
 * from the compile classpath, so any third-party module can ship its own discovery rules
 * without modifying this processor.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DiscoveryIndexProcessor extends AbstractProcessor {

    private static final String GENERATED_PACKAGE = "tech.guilhermekaua.spigotboot.generated";
    private static final String GENERATED_CLASS_PREFIX = "DiscoveryIndex_";
    private static final String DISCOVERY_INDEX_FQCN = "tech.guilhermekaua.spigotboot.core.context.discovery.DiscoveryIndex";
    private static final String CATEGORY_ANNOTATION_FQCN = "tech.guilhermekaua.spigotboot.core.context.discovery.SpigotBootDiscoveryCategory";
    private static final String MARKER_DIR = "META-INF/spigot-boot/discovery";

    private static final String KIND_ANNOTATION = "ANNOTATION";
    private static final String KIND_SUBTYPE = "SUBTYPE";

    private final Map<String, Set<String>> discovered = new LinkedHashMap<>();
    private final Map<String, CategoryRule> ruleCache = new HashMap<>();
    private Elements elementUtils;
    private boolean generated;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elementUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (generated) {
            return false;
        }

        for (Element root : roundEnv.getRootElements()) {
            collectFromElement(root);
        }

        // Write the index in a non-final round so javac compiles the generated source as
        // part of the same compilation output. Source files created during processingOver()
        // emit a "last round" warning and are unreliable across javac/Gradle incremental
        // builds. Subsequent rounds carry only sources newly emitted by other processors,
        // which should not be re-indexed here.
        if (!roundEnv.processingOver() && !discovered.isEmpty()) {
            writeIndexIfAny();
            generated = true;
        }
        return false;
    }

    private void collectFromElement(Element element) {
        if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) {
            classifyType((TypeElement) element);
            for (Element enclosed : element.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.CLASS || enclosed.getKind() == ElementKind.INTERFACE) {
                    collectFromElement(enclosed);
                }
            }
        }
    }

    private void classifyType(TypeElement type) {
        String fqcn = type.getQualifiedName().toString();
        boolean isAbstract = type.getModifiers().contains(Modifier.ABSTRACT);
        boolean isInterface = type.getKind() == ElementKind.INTERFACE;
        boolean isAnnotation = type.getKind() == ElementKind.ANNOTATION_TYPE;
        boolean isEnum = type.getKind() == ElementKind.ENUM;

        // Only reference classes that are visible from the generated package. Non-public or
        // inaccessible types (package-private test fixtures, inner classes, etc.) are left out
        // of the index; runtime consumers fall back to classpath scanning to pick them up.
        if (!isVisibleFromGenerated(type)) {
            return;
        }

        // Annotation-kind categories apply to real (instantiable) classes only. Annotations,
        // interfaces, enums, and abstract classes can't be registered as beans.
        boolean instantiableType = !isInterface && !isAnnotation && !isEnum && !isAbstract;
        if (instantiableType) {
            for (String category : findAnnotationCategories(type)) {
                add(category, fqcn);
            }
        }

        // Subtype-kind categories apply to anything assignable to the category-bearing type.
        // Filtering (interface-only, impl-class exclusion, etc.) is the runtime consumer's job.
        if (!isAnnotation) {
            for (String category : findSubtypeCategories(type)) {
                add(category, fqcn);
            }
        }
    }

    private boolean isVisibleFromGenerated(TypeElement type) {
        Element current = type;
        while (current != null && current.getKind() != ElementKind.PACKAGE) {
            if (current.getKind() == ElementKind.CLASS
                    || current.getKind() == ElementKind.INTERFACE
                    || current.getKind() == ElementKind.ENUM
                    || current.getKind() == ElementKind.ANNOTATION_TYPE) {
                if (!current.getModifiers().contains(Modifier.PUBLIC)) {
                    return false;
                }
            }
            current = current.getEnclosingElement();
        }
        return true;
    }

    /**
     * Walks every annotation on {@code type} (and their meta-annotations) looking for
     * {@code @SpigotBootDiscoveryCategory(kind=ANNOTATION)}. A rule found on a DIRECT annotation
     * always applies; a rule found on a meta-ancestor applies only if the rule is
     * {@code transitive = true}.
     */
    private Set<String> findAnnotationCategories(TypeElement type) {
        Set<String> categories = new LinkedHashSet<>();
        for (AnnotationMirror direct : type.getAnnotationMirrors()) {
            Element annoElement = direct.getAnnotationType().asElement();
            if (annoElement instanceof TypeElement) {
                walkAnnotationAncestors((TypeElement) annoElement, categories, new HashSet<>(), 0);
            }
        }
        return categories;
    }

    private void walkAnnotationAncestors(TypeElement annoType, Set<String> out,
                                         Set<String> visited, int depth) {
        String annoFqcn = annoType.getQualifiedName().toString();
        if (!visited.add(annoFqcn)) {
            return;
        }

        CategoryRule rule = readRule(annoType);
        if (rule != null && KIND_ANNOTATION.equals(rule.kind)) {
            if (depth == 0 || rule.transitive) {
                out.add(rule.name);
            }
        }

        for (AnnotationMirror meta : annoType.getAnnotationMirrors()) {
            Element metaEl = meta.getAnnotationType().asElement();
            if (metaEl instanceof TypeElement) {
                walkAnnotationAncestors((TypeElement) metaEl, out, visited, depth + 1);
            }
        }
    }

    /**
     * Walks every supertype (superclass and interfaces, transitively) of {@code type} looking
     * for {@code @SpigotBootDiscoveryCategory(kind=SUBTYPE)}. The starting type itself is
     * excluded — category roots are never added to their own category.
     */
    private Set<String> findSubtypeCategories(TypeElement type) {
        Set<String> categories = new LinkedHashSet<>();
        walkSupertypes(type, categories, new HashSet<>(), true);
        return categories;
    }

    private void walkSupertypes(TypeElement type, Set<String> out, Set<String> visited,
                                boolean isSelf) {
        String fqcn = type.getQualifiedName().toString();
        if (!visited.add(fqcn)) {
            return;
        }

        if (!isSelf) {
            CategoryRule rule = readRule(type);
            if (rule != null && KIND_SUBTYPE.equals(rule.kind)) {
                out.add(rule.name);
            }
        }

        TypeMirror superclass = type.getSuperclass();
        TypeElement superEl = asTypeElement(superclass);
        if (superEl != null) {
            walkSupertypes(superEl, out, visited, false);
        }
        for (TypeMirror iface : type.getInterfaces()) {
            TypeElement ifaceEl = asTypeElement(iface);
            if (ifaceEl != null) {
                walkSupertypes(ifaceEl, out, visited, false);
            }
        }
    }

    private TypeElement asTypeElement(TypeMirror mirror) {
        if (mirror == null || mirror.getKind() != TypeKind.DECLARED) {
            return null;
        }
        Element el = ((DeclaredType) mirror).asElement();
        return el instanceof TypeElement ? (TypeElement) el : null;
    }

    /**
     * Reads {@code @SpigotBootDiscoveryCategory} from a type. Returns {@code null} if the type
     * doesn't carry the annotation. Results are cached per type FQCN.
     */
    private CategoryRule readRule(TypeElement type) {
        String fqcn = type.getQualifiedName().toString();
        if (ruleCache.containsKey(fqcn)) {
            return ruleCache.get(fqcn);
        }

        CategoryRule rule = null;
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            Element annoElement = mirror.getAnnotationType().asElement();
            if (!(annoElement instanceof TypeElement)) {
                continue;
            }
            String annoFqcn = ((TypeElement) annoElement).getQualifiedName().toString();
            if (!CATEGORY_ANNOTATION_FQCN.equals(annoFqcn)) {
                continue;
            }

            String name = null;
            String kind = null;
            boolean transitive = false;
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : elementUtils.getElementValuesWithDefaults(mirror).entrySet()) {
                String member = entry.getKey().getSimpleName().toString();
                Object value = entry.getValue().getValue();
                if ("value".equals(member) && value instanceof String) {
                    name = (String) value;
                } else if ("kind".equals(member)) {
                    kind = kindNameFrom(value);
                } else if ("transitive".equals(member) && value instanceof Boolean) {
                    transitive = (Boolean) value;
                }
            }

            if (name != null && kind != null) {
                rule = new CategoryRule(name, kind, transitive);
            }
            break;
        }

        ruleCache.put(fqcn, rule);
        return rule;
    }

    private static String kindNameFrom(Object value) {
        if (value instanceof VariableElement) {
            return ((VariableElement) value).getSimpleName().toString();
        }
        // javac may surface enum constants as their string name directly
        return value != null ? value.toString() : null;
    }

    private void add(String category, String fqcn) {
        discovered.computeIfAbsent(category, k -> new LinkedHashSet<>()).add(fqcn);
    }

    private void writeIndexIfAny() {
        if (discovered.isEmpty()) {
            return;
        }

        String hash = computeHash();
        String generatedClassName = GENERATED_CLASS_PREFIX + hash;
        String generatedFqcn = GENERATED_PACKAGE + "." + generatedClassName;

        try {
            writeGeneratedSource(generatedClassName, generatedFqcn);
            writeMarkerResource(generatedFqcn);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write DiscoveryIndex: " + e.getMessage());
        }
    }

    private String computeHash() {
        List<String> ordered = new ArrayList<>();
        List<String> categoryKeys = new ArrayList<>(discovered.keySet());
        Collections.sort(categoryKeys);
        for (String category : categoryKeys) {
            List<String> classes = new ArrayList<>(discovered.get(category));
            Collections.sort(classes);
            ordered.add(category + "=" + String.join(",", classes));
        }
        String joined = String.join("|", ordered);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(joined.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                hex.append(String.format("%02x", bytes[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            return Integer.toHexString(joined.hashCode());
        }
    }

    private void writeGeneratedSource(String simpleName, String generatedFqcn) throws IOException {
        Filer filer = processingEnv.getFiler();
        JavaFileObject file = filer.createSourceFile(generatedFqcn);
        List<String> upstreamIndexes = findUpstreamIndexFqcns(generatedFqcn);
        try (Writer w = file.openWriter(); PrintWriter pw = new PrintWriter(w)) {
            pw.println("// Generated by spigot-boot DiscoveryIndexProcessor. Do not edit.");
            pw.println("package " + GENERATED_PACKAGE + ";");
            pw.println();
            pw.println("import java.util.Collections;");
            pw.println("import java.util.LinkedHashMap;");
            pw.println("import java.util.Map;");
            pw.println("import " + DISCOVERY_INDEX_FQCN + ";");
            pw.println();
            pw.println("public final class " + simpleName + " implements DiscoveryIndex {");
            pw.println("    private static final Map<String, Class<?>[]> ENTRIES;");
            if (!upstreamIndexes.isEmpty()) {
                // Static references to upstream DiscoveryIndex classes so the Maven Shade Plugin's
                // minimizer treats them (and every component class they list) as reachable.
                pw.println("    @SuppressWarnings(\"unused\")");
                pw.println("    private static final Class<?>[] KEEP_UPSTREAM = {");
                for (int i = 0; i < upstreamIndexes.size(); i++) {
                    String suffix = i == upstreamIndexes.size() - 1 ? "" : ",";
                    pw.println("        " + upstreamIndexes.get(i) + ".class" + suffix);
                }
                pw.println("    };");
            }
            pw.println("    static {");
            pw.println("        Map<String, Class<?>[]> m = new LinkedHashMap<String, Class<?>[]>();");

            List<String> keys = new ArrayList<>(discovered.keySet());
            Collections.sort(keys);
            for (String category : keys) {
                Set<String> fqcns = new TreeSet<>(discovered.get(category));
                pw.println("        m.put(\"" + escape(category) + "\", new Class<?>[] {");
                List<String> fqcnList = new ArrayList<>(fqcns);
                for (int i = 0; i < fqcnList.size(); i++) {
                    String suffix = i == fqcnList.size() - 1 ? "" : ",";
                    pw.println("            " + fqcnList.get(i) + ".class" + suffix);
                }
                pw.println("        });");
            }

            pw.println("        ENTRIES = Collections.unmodifiableMap(m);");
            pw.println("    }");
            pw.println();
            pw.println("    public " + simpleName + "() {}");
            pw.println();
            pw.println("    @Override");
            pw.println("    public Map<String, Class<?>[]> entries() {");
            pw.println("        return ENTRIES;");
            pw.println("    }");
            pw.println("}");
        }
    }

    private List<String> findUpstreamIndexFqcns(String selfFqcn) {
        PackageElement pkg = elementUtils.getPackageElement(GENERATED_PACKAGE);
        if (pkg == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Element enclosed : pkg.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CLASS) {
                continue;
            }
            String fqcn = ((TypeElement) enclosed).getQualifiedName().toString();
            if (!fqcn.startsWith(GENERATED_PACKAGE + "." + GENERATED_CLASS_PREFIX)) {
                continue;
            }
            if (fqcn.equals(selfFqcn)) {
                continue;
            }
            result.add(fqcn);
        }
        Collections.sort(result);
        return result;
    }

    private void writeMarkerResource(String generatedFqcn) throws IOException {
        Filer filer = processingEnv.getFiler();
        FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                MARKER_DIR + "/" + generatedFqcn);
        try (Writer w = resource.openWriter()) {
            w.write("");
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class CategoryRule {
        final String name;
        final String kind;
        final boolean transitive;

        CategoryRule(String name, String kind, boolean transitive) {
            this.name = name;
            this.kind = kind;
            this.transitive = transitive;
        }
    }
}
