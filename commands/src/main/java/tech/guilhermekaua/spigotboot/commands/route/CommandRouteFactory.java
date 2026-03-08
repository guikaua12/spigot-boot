package tech.guilhermekaua.spigotboot.commands.route;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodKind;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodMetadata;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class CommandRouteFactory {
    private final CommandPatternParser patternParser;
    private final CommandReplacementRegistry replacementRegistry;
    private final CommandInvocationFactory invocationFactory;

    public CommandRouteFactory(CommandPatternParser patternParser,
                               CommandReplacementRegistry replacementRegistry,
                               CommandInvocationFactory invocationFactory) {
        this.patternParser = patternParser;
        this.replacementRegistry = replacementRegistry;
        this.invocationFactory = invocationFactory;
    }

    public List<CompiledRootCommand> create(List<RootCommandMetadata> handlers) {
        List<CompiledRootCommand> roots = new ArrayList<>();
        for (RootCommandMetadata handler : handlers) {
            roots.add(create(handler));
        }
        return roots;
    }

    public CompiledRootCommand create(RootCommandMetadata handler) {
        CommandAliasSet rootAliases = resolveRootAliases(handler.getAliases());
        List<CompiledCommandRoute> routes = new ArrayList<>();
        CompiledCommandRoute defaultRoute = null;
        CompiledCommandRoute unknownRoute = null;

        for (CommandMethodMetadata method : handler.getMethods()) {
            if (method.getKind() == CommandMethodKind.COMMAND) {
                for (CommandPattern pattern : resolvePatterns(method.getAliases())) {
                    routes.add(new CompiledCommandRoute(
                            method.getKind(),
                            pattern,
                            invocationFactory.create(method, pattern),
                            replace(method.getDescription()),
                            resolveUsage(rootAliases.getPrimary(), handler.getUsage(), method.getUsage(), pattern.getSource()),
                            replace(method.getPermission())
                    ));
                }
                continue;
            }

            CommandPattern emptyPattern = patternParser.parse("");
            CompiledCommandRoute route = new CompiledCommandRoute(
                    method.getKind(),
                    emptyPattern,
                    invocationFactory.create(method, emptyPattern),
                    replace(method.getDescription()),
                    resolveUsage(rootAliases.getPrimary(), handler.getUsage(), method.getUsage(), ""),
                    replace(method.getPermission())
            );

            if (method.getKind() == CommandMethodKind.DEFAULT) {
                if (defaultRoute != null) {
                    throw new IllegalStateException("Only one @DefaultCommand may exist per handler: " + handler.getHandlerType().getName());
                }
                defaultRoute = route;
            } else {
                if (unknownRoute != null) {
                    throw new IllegalStateException("Only one @CatchUnknown may exist per handler: " + handler.getHandlerType().getName());
                }
                unknownRoute = route;
            }
        }

        return new CompiledRootCommand(
                handler.getHandlerBean(),
                handler.getHandlerType(),
                rootAliases,
                replace(handler.getDescription()),
                resolveUsage(rootAliases.getPrimary(), handler.getUsage(), "", ""),
                routes,
                defaultRoute,
                unknownRoute
        );
    }

    private CommandAliasSet resolveRootAliases(CommandAliasSet aliases) {
        List<String> expanded = new ArrayList<>();
        for (String rawValue : aliases.allValues()) {
            for (String variant : patternParser.expandAliases(replace(rawValue))) {
                CommandPattern pattern = patternParser.parse(variant);
                if (pattern.getSegments().size() != 1 || !(pattern.getSegments().get(0) instanceof CommandPattern.LiteralSegment)) {
                    throw new IllegalStateException("Root commands must resolve to a single literal token: " + rawValue);
                }
                expanded.add(((CommandPattern.LiteralSegment) pattern.getSegments().get(0)).getLiteral());
            }
        }

        if (expanded.isEmpty()) {
            throw new IllegalStateException("Root command must declare at least one label.");
        }

        return new CommandAliasSet(expanded.get(0), expanded.subList(1, expanded.size()));
    }

    private List<CommandPattern> resolvePatterns(CommandAliasSet aliases) {
        LinkedHashMap<String, CommandPattern> patterns = new LinkedHashMap<>();
        for (String rawValue : aliases.allValues()) {
            for (String variant : patternParser.expandAliases(replace(rawValue))) {
                CommandPattern pattern = patternParser.parse(variant);
                patterns.put(pattern.normalizedSignature() + "::" + pattern.getSource(), pattern);
            }
        }
        return new ArrayList<>(patterns.values());
    }

    private String resolveUsage(String rootLabel, String rootUsage, String routeUsage, String patternSource) {
        String explicitRouteUsage = replace(routeUsage);
        if (!explicitRouteUsage.isEmpty()) {
            return explicitRouteUsage;
        }

        String explicitRootUsage = replace(rootUsage);
        if (!explicitRootUsage.isEmpty() && (patternSource == null || patternSource.isEmpty())) {
            return explicitRootUsage;
        }

        if (patternSource == null || patternSource.trim().isEmpty()) {
            return "/" + rootLabel;
        }
        return "/" + rootLabel + " " + patternSource.trim();
    }

    private String replace(String value) {
        String replaced = replacementRegistry.replace(value);
        return replaced == null ? "" : replaced.trim();
    }
}
