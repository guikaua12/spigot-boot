package me.approximations.apxPlugin.core.reflection;

import java.util.Collection;
import java.util.Optional;

public interface DiscoveryService<T> {
    default Optional<T> discover() {
        throw new UnsupportedOperationException("Method not implemented.");
    }

    default Collection<T> discoverAll() {
        throw new UnsupportedOperationException("Method not implemented.");
    }
}
