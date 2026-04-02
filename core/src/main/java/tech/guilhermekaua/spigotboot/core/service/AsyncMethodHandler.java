package tech.guilhermekaua.spigotboot.core.service;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Async;
import tech.guilhermekaua.spigotboot.core.context.annotations.RegisterMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
@RegisterMethodHandler
public class AsyncMethodHandler {
    static final String DEFAULT_EXECUTOR_BEAN_NAME = "serviceAsyncExecutor";

    private final Context context;

    @MethodHandler(methodAnnotatedWith = Async.class)
    public Object handle(MethodHandlerContext context) {
        if (context.self() == null || (context.thisMethod() == null && context.proceed() == null)) {
            return null;
        }

        Method invocationMethod = context.thisMethod() != null ? context.thisMethod() : context.proceed();
        if (invocationMethod.getReturnType() != CompletableFuture.class) {
            throw new IllegalArgumentException("Async methods must return CompletableFuture");
        }

        Async async = findAsyncAnnotation(context);
        String executorBeanName = async == null || async.value().trim().isEmpty()
                ? DEFAULT_EXECUTOR_BEAN_NAME
                : async.value().trim();

        ExecutorService executorService = this.context.getBean(ExecutorService.class, executorBeanName);
        if (executorService == null) {
            throw new IllegalStateException(
                    "No ExecutorService bean named '" + executorBeanName + "' found for @Async method: "
                            + invocationMethod.getDeclaringClass().getName() + "#" + invocationMethod.getName()
            );
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<?> result = (CompletableFuture<?>) context.proceed().invoke(context.self(), context.args());
                if (result == null) {
                    return null;
                }

                return result.join();
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke async method: " + invocationMethod.getName(), e);
            }
        }, executorService);
    }

    private Async findAsyncAnnotation(MethodHandlerContext context) {
        Async annotation = findAsyncAnnotation(context.thisMethod());
        if (annotation != null) {
            return annotation;
        }

        annotation = findAsyncAnnotation(context.proceed());
        if (annotation != null) {
            return annotation;
        }

        if (context.self() == null) {
            return null;
        }

        Method signatureSource = context.thisMethod() != null ? context.thisMethod() : context.proceed();
        if (signatureSource == null) {
            return null;
        }

        Class<?> realClass = ProxyUtils.getRealClass(context.self());
        Method realMethod = findMethod(realClass, signatureSource);
        annotation = findAsyncAnnotation(realMethod);
        if (annotation != null) {
            return annotation;
        }

        for (Class<?> iface : realClass.getInterfaces()) {
            annotation = findAsyncAnnotation(findMethod(iface, signatureSource));
            if (annotation != null) {
                return annotation;
            }
        }

        return null;
    }

    private Async findAsyncAnnotation(Method method) {
        return method != null ? method.getAnnotation(Async.class) : null;
    }

    private Method findMethod(Class<?> type, Method signatureSource) {
        if (type == null || signatureSource == null) {
            return null;
        }

        try {
            return type.getMethod(signatureSource.getName(), signatureSource.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
            // fall through
        }

        try {
            return type.getDeclaredMethod(signatureSource.getName(), signatureSource.getParameterTypes());
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
