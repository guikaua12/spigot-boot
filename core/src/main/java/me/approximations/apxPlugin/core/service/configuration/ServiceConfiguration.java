package me.approximations.apxPlugin.core.service.configuration;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import me.approximations.apxPlugin.core.context.configuration.annotations.Bean;
import me.approximations.apxPlugin.core.context.configuration.annotations.Configuration;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Executors;

@Configuration
public class ServiceConfiguration {
    @Bean
    public ServiceProperties serviceConfiguration(Plugin plugin) {
        return ServiceProperties.builder()
                .executorService(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(plugin.getName() + "-Service-Thread-%d").build()))
                .build();
    }
}
