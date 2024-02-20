package me.approximations.apxPlugin.testPlugin.configuration;


import me.approximations.apxPlugin.di.annotations.DependencyRegister;
import me.approximations.apxPlugin.testPlugin.services.PointsService;

@DependencyRegister
public class DependencyRegistrar {
    public PointsService getPointsService() {
        return new PointsService();
    }
}
