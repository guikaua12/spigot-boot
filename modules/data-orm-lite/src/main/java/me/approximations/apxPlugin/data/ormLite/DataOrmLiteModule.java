package me.approximations.apxPlugin.data.ormLite;

import lombok.RequiredArgsConstructor;
import me.approximations.apxPlugin.core.module.Module;
import me.approximations.apxPlugin.data.ormLite.repository.registry.OrmLiteRepositoryRegistry;

@RequiredArgsConstructor
public class DataOrmLiteModule implements Module {
    private final OrmLiteRepositoryRegistry ormLiteRepositoryRegistry;

    @Override
    public void initialize() throws Exception {
        ormLiteRepositoryRegistry.initialize();
    }
}
