package me.approximations.apxPlugin.test.persistence.service;

import me.approximations.apxPlugin.persistence.jpa.service.annotations.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PeopleService {

    @Service
    public CompletableFuture<String> someMethod() {
        return CompletableFuture.completedFuture("Hello world");
    }
}
