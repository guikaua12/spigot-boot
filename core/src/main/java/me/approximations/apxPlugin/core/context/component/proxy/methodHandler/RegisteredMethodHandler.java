package me.approximations.apxPlugin.core.context.component.proxy.methodHandler;

import lombok.Getter;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.context.MethodHandlerContext;

import java.lang.annotation.Annotation;

@Getter
public class RegisteredMethodHandler {
    private final RegisteredMethodHandlerRunnable runnable;
    private final Class<?> targetClass;
    private final Class<? extends Annotation> classTargetAnnotation;
    private final Class<? extends Annotation> methodTargetAnnotation;

    public RegisteredMethodHandler(RegisteredMethodHandlerRunnable runnable,
                                   Class<?> targetClass,
                                   Class<? extends Annotation> classTargetAnnotation,
                                   Class<? extends Annotation> methodTargetAnnotation
    ) {
        this.runnable = runnable;
        this.targetClass = targetClass != void.class ? targetClass : null;
        this.classTargetAnnotation = classTargetAnnotation != Annotation.class ? classTargetAnnotation : null;
        this.methodTargetAnnotation = methodTargetAnnotation != Annotation.class ? methodTargetAnnotation : null;
    }

    @SuppressWarnings("RedundantIfStatement")
    public boolean canHandle(MethodHandlerContext context) {
        if (targetClass != null && !targetClass.isInstance(context.self())) {
            return false;
        }

        if (classTargetAnnotation != null && context.thisMethod().getAnnotation(classTargetAnnotation) == null) {
            return false;
        }

        if (methodTargetAnnotation != null && context.thisMethod().getAnnotation(methodTargetAnnotation) == null) {
            return false;
        }

        return true;
    }
}
