package tech.guilhermekaua.spigotboot.core.module;

import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Order;

@Order(-500)
public class OrderedTestModule implements Module {
    @Override
    public void onInitialize(Context context) throws Exception {
    }
}
