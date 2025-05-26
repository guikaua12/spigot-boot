package me.approximations.apxPlugin.test.persistence.service;

import me.approximations.apxPlugin.di.annotations.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PeopleService {

    public CompletableFuture<String> someMethod() {
        return CompletableFuture.completedFuture("Hello world");
    }
}
