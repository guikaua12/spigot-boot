package tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.impl;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.processors.preDestroy.ContextPreDestroyProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class ShutdownExecutorServicesContextPreDestroyProcessor implements ContextPreDestroyProcessor {

    @Override
    public void onPreDestroy(Context context) {
        for (ExecutorService executorService : context.getBeansByType(ExecutorService.class)) {
            try {
                executorService.shutdown();

                if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

}