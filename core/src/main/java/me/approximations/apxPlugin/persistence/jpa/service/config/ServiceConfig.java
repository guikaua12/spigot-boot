package me.approximations.apxPlugin.persistence.jpa.service.config;

import java.util.concurrent.ExecutorService;

public interface ServiceConfig {
    ExecutorService getExecutorService();
}
