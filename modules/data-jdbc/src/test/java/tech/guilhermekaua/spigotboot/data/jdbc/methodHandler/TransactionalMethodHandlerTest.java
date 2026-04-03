package tech.guilhermekaua.spigotboot.data.jdbc.methodHandler;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.core.context.component.proxy.methodHandler.context.MethodHandlerContext;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionCallback;
import tech.guilhermekaua.spigotboot.data.transaction.TransactionManager;
import tech.guilhermekaua.spigotboot.data.transaction.Transactional;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class TransactionalMethodHandlerTest {

    interface TransactionalContract {
        @Transactional
        String save();
    }

    static class TransactionalService {
        @Transactional
        public String save() {
            return "saved";
        }
    }

    static class InterfaceTransactionalService implements TransactionalContract {
        @Override
        public String save() {
            return "saved";
        }
    }

    @Test
    void shouldDelegateToInvokeNextInsideTransaction() throws Throwable {
        TransactionManager transactionManager = mock(TransactionManager.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute();
        }).when(transactionManager).execute(any(TransactionCallback.class));

        TransactionalMethodHandler handler = new TransactionalMethodHandler(transactionManager);
        AtomicBoolean invokedNext = new AtomicBoolean(false);
        Method method = TransactionalService.class.getMethod("save");

        Object result = handler.handle(new MethodHandlerContext(
                new TransactionalService(),
                method,
                method,
                new Object[0],
                () -> {
                    invokedNext.set(true);
                    return "saved";
                }
        ));

        assertEquals("saved", result);
        assertTrue(invokedNext.get());
        verify(transactionManager).execute(any(TransactionCallback.class));
    }

    @Test
    void shouldRejectInterfaceDeclaredTransactionalMethods() throws Throwable {
        TransactionManager transactionManager = mock(TransactionManager.class);
        TransactionalMethodHandler handler = new TransactionalMethodHandler(transactionManager);
        Method interfaceMethod = TransactionalContract.class.getMethod("save");
        Method implementationMethod = InterfaceTransactionalService.class.getMethod("save");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new MethodHandlerContext(
                        new InterfaceTransactionalService(),
                        interfaceMethod,
                        implementationMethod,
                        new Object[0],
                        () -> "ignored"
                ))
        );

        assertEquals("@Transactional only supports concrete class methods", exception.getMessage());
        verifyNoInteractions(transactionManager);
    }
}
