package tech.guilhermekaua.spigotboot.commands;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CommandInterceptorAnnotationBinding {
    private final Annotation annotation;
    private final Class<? extends Annotation> annotationType;
    private final List<Class<? extends CommandAnnotationInterceptor<?>>> interceptorTypes;

    public CommandInterceptorAnnotationBinding(Annotation annotation,
                                               List<Class<? extends CommandAnnotationInterceptor<?>>> interceptorTypes) {
        this.annotation = Objects.requireNonNull(annotation, "annotation cannot be null.");
        this.annotationType = annotation.annotationType();

        List<Class<? extends CommandAnnotationInterceptor<?>>> copiedTypes = new ArrayList<>(
                Objects.requireNonNull(interceptorTypes, "interceptorTypes cannot be null.")
        );
        if (copiedTypes.isEmpty()) {
            throw new IllegalStateException(
                    "Interceptor annotation @" + annotationType.getSimpleName() + " must declare at least one interceptor type."
            );
        }

        this.interceptorTypes = Collections.unmodifiableList(copiedTypes);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    public List<Class<? extends CommandAnnotationInterceptor<?>>> getInterceptorTypes() {
        return interceptorTypes;
    }
}
