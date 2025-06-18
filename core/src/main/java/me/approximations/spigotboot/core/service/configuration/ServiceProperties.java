package me.approximations.spigotboot.core.service.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
@Builder
@Getter
public class ServiceProperties {
    private final ExecutorService executorService;
}
