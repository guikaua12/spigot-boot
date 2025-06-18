package me.approximations.spigotboot.data.ormLite;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.core.module.Module;
import me.approximations.spigotboot.data.ormLite.registry.OrmLiteRepositoryRegistry;

@RequiredArgsConstructor
public class DataOrmLiteModule implements Module {
    private final OrmLiteRepositoryRegistry ormLiteRepositoryRegistry;

    @Override
    public void initialize() throws Exception {
        ormLiteRepositoryRegistry.initialize();
    }
}
