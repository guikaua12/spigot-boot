package tech.guilhermekaua.spigotboot.core.test.service;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Async;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.service.AsyncMethodHandler;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncMethodHandlerTest {
    private static final String DEFAULT_EXECUTOR_BEAN_NAME = "serviceAsyncExecutor";

    public static class DefaultAsyncService {
        @Async
        public CompletableFuture<String> load() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }

    public interface InterfaceAsyncService {
        @Async("customExecutor")
        CompletableFuture<String> load();
    }

    public static class InterfaceAsyncServiceImpl implements InterfaceAsyncService {
        @Override
        public CompletableFuture<String> load() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }

    public static class MissingExecutorAsyncService {
        @Async("missingExecutor")
        public CompletableFuture<String> load() {
            return CompletableFuture.completedFuture("never-called");
        }
    }

    public static class InvalidAsyncService {
        @Async
        public String load() {
            return "invalid";
        }
    }

    @Test
    void shouldUseDefaultExecutorBeanWhenAsyncValueIsBlank() throws Throwable {
        Context context = mock(Context.class);
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "default-async"));
        when(context.getBean(ExecutorService.class, DEFAULT_EXECUTOR_BEAN_NAME)).thenReturn(executorService);

        try {
            AsyncMethodHandler handler = new AsyncMethodHandler(context);
            DefaultAsyncService service = new DefaultAsyncService();
            Method method = DefaultAsyncService.class.getMethod("load");

            Object result = handler.handle(new MethodHandlerContext(service, method, method, new Object[0]));

            CompletableFuture<?> future = assertInstanceOf(CompletableFuture.class, result);
            assertEquals("default-async", future.join());
            verify(context).getBean(ExecutorService.class, DEFAULT_EXECUTOR_BEAN_NAME);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldResolveNamedExecutorFromInterfaceAnnotation() throws Throwable {
        Context context = mock(Context.class);
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "custom-async"));
        when(context.getBean(ExecutorService.class, "customExecutor")).thenReturn(executorService);

        try {
            AsyncMethodHandler handler = new AsyncMethodHandler(context);
            InterfaceAsyncService service = new InterfaceAsyncServiceImpl();
            Method interfaceMethod = InterfaceAsyncService.class.getMethod("load");
            Method implementationMethod = InterfaceAsyncServiceImpl.class.getMethod("load");

            Object result = handler.handle(new MethodHandlerContext(service, interfaceMethod, implementationMethod, new Object[0]));

            CompletableFuture<?> future = assertInstanceOf(CompletableFuture.class, result);
            assertEquals("custom-async", future.join());
            verify(context).getBean(ExecutorService.class, "customExecutor");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldFailFastWhenNamedExecutorBeanIsMissing() throws Throwable {
        Context context = mock(Context.class);
        AsyncMethodHandler handler = new AsyncMethodHandler(context);
        MissingExecutorAsyncService service = new MissingExecutorAsyncService();
        Method method = MissingExecutorAsyncService.class.getMethod("load");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new MethodHandlerContext(service, method, method, new Object[0]))
        );

        assertEquals(
                "No ExecutorService bean named 'missingExecutor' found for @Async method: "
                        + MissingExecutorAsyncService.class.getName() + "#load",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMethodsThatDoNotReturnCompletableFuture() throws Throwable {
        Context context = mock(Context.class);
        AsyncMethodHandler handler = new AsyncMethodHandler(context);
        InvalidAsyncService service = new InvalidAsyncService();
        Method method = InvalidAsyncService.class.getMethod("load");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(new MethodHandlerContext(service, method, method, new Object[0]))
        );

        assertEquals("Async methods must return CompletableFuture", exception.getMessage());
    }
}
