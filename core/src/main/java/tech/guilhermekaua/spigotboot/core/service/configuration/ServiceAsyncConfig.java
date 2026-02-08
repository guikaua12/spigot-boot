package tech.guilhermekaua.spigotboot.core.service.configuration;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@RequiredArgsConstructor
public class ServiceAsyncConfig {
    private final BootPlugin plugin;
    /* debug only */
    private final Set<ExecutorService> executors = new HashSet<>();

    @Bean
    public ExecutorService serviceAsyncExecutor() {
        AtomicInteger counter = new AtomicInteger();
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, plugin.getName() + "-Service-Thread-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        executors.add(executor);
        return executor;
    }

    @Bean
    public ServiceProperties serviceProperties() {
        return ServiceProperties.builder()
                .executorService(serviceAsyncExecutor())
                .build();
    }
}
