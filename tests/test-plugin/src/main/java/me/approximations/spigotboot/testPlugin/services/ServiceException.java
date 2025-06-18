package me.approximations.spigotboot.testPlugin.services;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class ServiceException extends RuntimeException {
    private final String message;
}
