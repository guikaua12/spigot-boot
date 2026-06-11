package tech.guilhermekaua.spigotboot.core.context.lifecycle;

public interface Ordered {
    default int getOrder() {
        return 0;
    }
}


