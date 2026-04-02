package tech.guilhermekaua.spigotboot.core.service.configuration;

import lombok.RequiredArgsConstructor;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;
import tech.guilhermekaua.spigotboot.core.plugin.BootPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@RequiredArgsConstructor
public class ServiceAsyncConfig {
    private final BootPlugin plugin;

    @Bean
    public ExecutorService serviceAsyncExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, plugin.getName() + "-Service-Thread-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
