package tech.guilhermekaua.spigotboot.core.context.lifecycle;

public enum ContextPhase {
    REGISTER_CORE,
    SCAN,
    MODULES,
    DEFINITIONS_READY,
    INSTANTIATE,
    READY,
    RUNNING,
    DESTROY,
    PRE_DESTROY_PROCESSORS,
    CLEARED
}


