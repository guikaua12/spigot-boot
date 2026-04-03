package tech.guilhermekaua.spigotboot.data.jdbc.methodHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Async;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.ComponentProxy;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.MethodHandlerRegistry;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.RegisteredMethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.annotations.MethodHandler;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.core.service.AsyncMethodHandler;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionCallback;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionCallbackWithoutResult;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionManager;
import tech.guilhermekaua.spigotboot.data.transaction.Transactional;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AsyncTransactionalMethodHandlerIntegrationTest {

    static class AsyncTransactionalService {
        private final List<String> callLog;

        AsyncTransactionalService(List<String> callLog) {
            this.callLog = callLog;
        }

        @Async
        @Transactional
        public CompletableFuture<String> saveUser() {
            callLog.add("target:" + Thread.currentThread().getName());
            return CompletableFuture.completedFuture("saved");
        }
    }

    @AfterEach
    void tearDown() {
        MethodHandlerRegistry.clear();
    }

    @Test
    void shouldRunTransactionalHandlerInsideAsyncExecutorThread() throws Throwable {
        MethodHandlerRegistry.clear();

        Context context = mock(Context.class);
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> new Thread(r, "async-transactional"));
        when(context.getBean(ExecutorService.class, "serviceAsyncExecutor")).thenReturn(executorService);

        List<String> callLog = Collections.synchronizedList(new ArrayList<>());
        TrackingTransactionManager transactionManager = new TrackingTransactionManager(callLog);

        try {
            AsyncMethodHandler asyncHandler = new AsyncMethodHandler(context);
            TransactionalMethodHandler transactionalHandler = new TransactionalMethodHandler(transactionManager);

            MethodHandlerRegistry.registerAll(Arrays.asList(
                    registeredHandler(asyncHandler, AsyncMethodHandler.class.getMethod("handle", MethodHandlerContext.class)),
                    registeredHandler(transactionalHandler, TransactionalMethodHandler.class.getMethod("handle", MethodHandlerContext.class))
            ));

            AsyncTransactionalService proxy = ComponentProxy.createProxy(
                    AsyncTransactionalService.class,
                    new AsyncTransactionalService(callLog),
                    new Class<?>[0],
                    new Object[0]
            );

            String callerThread = Thread.currentThread().getName();
            Object result = proxy.saveUser();

            CompletableFuture<?> future = assertInstanceOf(CompletableFuture.class, result);
            assertEquals("saved", future.join());
            assertEquals(
                    Arrays.asList("transaction:async-transactional", "target:async-transactional"),
                    callLog
            );
            assertEquals("async-transactional", transactionManager.getExecutionThreadName());
            assertNotEquals(callerThread, transactionManager.getExecutionThreadName());
        } finally {
            executorService.shutdownNow();
        }
    }

    private RegisteredMethodHandler registeredHandler(Object handlerInstance, Method method) {
        MethodHandler annotation = method.getAnnotation(MethodHandler.class);
        return new RegisteredMethodHandler(
                context -> method.invoke(handlerInstance, context),
                annotation.targetClass(),
                annotation.classAnnotatedWith(),
                annotation.methodAnnotatedWith(),
                annotation.order()
        );
    }

    private static final class TrackingTransactionManager implements TransactionManager {
        private final List<String> callLog;
        private String executionThreadName;

        private TrackingTransactionManager(List<String> callLog) {
            this.callLog = callLog;
        }

        @Override
        public <T> T execute(TransactionCallback<T> callback) {
            executionThreadName = Thread.currentThread().getName();
            callLog.add("transaction:" + executionThreadName);

            try {
                return callback.execute();
            } catch (Throwable throwable) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }
                throw new RuntimeException(throwable);
            }
        }

        @Override
        public void execute(TransactionCallbackWithoutResult callback) {
            execute(() -> {
                callback.execute();
                return null;
            });
        }

        private String getExecutionThreadName() {
            return executionThreadName;
        }
    }
}
