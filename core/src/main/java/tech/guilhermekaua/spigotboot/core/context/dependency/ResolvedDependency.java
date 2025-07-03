package tech.guilhermekaua.spigotboot.core.context.dependency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@RequiredArgsConstructor
public class ResolvedDependency {
    private Object instance;
    private final Dependency dependency;
}