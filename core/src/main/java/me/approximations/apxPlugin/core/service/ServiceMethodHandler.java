package me.approximations.apxPlugin.core.service;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.annotations.RegisterMethodHandler;
import me.approximations.apxPlugin.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import me.approximations.apxPlugin.core.di.annotations.Service;
import me.approximations.apxPlugin.core.service.configuration.ServiceProperties;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@RegisterMethodHandler
public class ServiceMethodHandler {
    private final ServiceProperties serviceProperties;

    @MethodHandler(
            classAnnotatedWith = Service.class,
            methodAnnotatedWith = Service.class
    )
    public Object handle(MethodHandlerContext context) {
        if (context.self() == null || context.thisMethod() == null) {
            return null;
        }

        if (context.thisMethod().getReturnType() != CompletableFuture.class) {
            throw new IllegalArgumentException("Service methods must return CompletableFuture");
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<?> result = (CompletableFuture<?>) context.proceed().invoke(context.self(), context.args());

                if (result == null) {
                    return null;
                }

                return result.join();
            } catch (Exception e) {
                throw new RuntimeException("Failed to invoke service method: " + context.thisMethod().getName(), e);
            }
        }, serviceProperties.getExecutorService());
    }
}
