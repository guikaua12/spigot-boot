package tech.guilhermekaua.spigotboot.core.service.configuration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Bean;
import tech.guilhermekaua.spigotboot.core.context.annotations.Configuration;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
public class ServiceAsyncConfig {
    private final Plugin plugin;
    /* debug only */
    private final Set<ExecutorService> executors = new HashSet<>();

    @Bean
    public ExecutorService serviceAsyncExecutor() {
        ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(plugin.getName() + "-Service-Thread-%d").build());
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
