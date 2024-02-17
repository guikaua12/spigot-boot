package me.approximations.apxPlugin.testPlugin.services;

import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.dependencyInjection.annotations.Inject;
import me.approximations.apxPlugin.testPlugin.Main;
import me.approximations.apxPlugin.testPlugin.repositories.UserRepository;

@RequiredArgsConstructor
@NoArgsConstructor(force=true)
public class PointsService {
    private final String name = "PointsService";

    @Inject
    private final Main plugin;

    @Inject
    private final UserRepository userRepository;
}
