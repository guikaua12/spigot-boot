package tech.guilhermekaua.spigotboot.commands.route;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolverChain;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationFactory;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandAliasSet;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodKind;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodMetadata;
import tech.guilhermekaua.spigotboot.commands.metadata.RootCommandMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPatternParser;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class CommandRouteFactory {
    private final CommandPatternParser patternParser;
    private final CommandReplacementRegistry replacementRegistry;
    private final CommandTextResolverChain commandTextResolverChain;
    private final CommandInvocationFactory invocationFactory;

    public CommandRouteFactory(CommandPatternParser patternParser,
                               CommandReplacementRegistry replacementRegistry,
                               CommandInvocationFactory invocationFactory) {
        this(patternParser, replacementRegistry, CommandTextResolverChain.empty(), invocationFactory);
    }

    public CommandRouteFactory(CommandPatternParser patternParser,
                               CommandReplacementRegistry replacementRegistry,
                               CommandTextResolverChain commandTextResolverChain,
                               CommandInvocationFactory invocationFactory) {
        this.patternParser = patternParser;
        this.replacementRegistry = replacementRegistry;
        this.commandTextResolverChain = commandTextResolverChain == null ? CommandTextResolverChain.empty() : commandTextResolverChain;
        this.invocationFactory = invocationFactory;
    }

    public List<CompiledRootCommand> create(List<RootCommandMetadata> handlers) {
        return create(null, handlers);
    }

    public List<CompiledRootCommand> create(Context context, List<RootCommandMetadata> handlers) {
        List<CompiledRootCommand> roots = new ArrayList<>();
        for (RootCommandMetadata handler : handlers) {
            roots.add(create(context, handler));
        }
        return roots;
    }

    public CompiledRootCommand create(RootCommandMetadata handler) {
        return create(null, handler);
    }

    public CompiledRootCommand create(Context context, RootCommandMetadata handler) {
        CommandAliasSet rootAliases = resolveRootAliases(context, handler);
        List<CompiledCommandRoute> routes = new ArrayList<>();
        CompiledCommandRoute defaultRoute = null;
        CompiledCommandRoute unknownRoute = null;

        for (CommandMethodMetadata method : handler.getMethods()) {
            if (method.getKind() == CommandMethodKind.COMMAND) {
                for (CommandPattern pattern : resolvePatterns(context, method)) {
                    routes.add(new CompiledCommandRoute(
                            method.getKind(),
                            pattern,
                            invocationFactory.create(method, pattern),
                            resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_DESCRIPTION, method.getDescription()),
                            resolveUsage(context, rootAliases.getPrimary(), handler, method, pattern.getSource()),
                            resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_PERMISSION, method.getPermission())
                    ));
                }
                continue;
            }

            CommandPattern emptyPattern = patternParser.parse("");
            CompiledCommandRoute route = new CompiledCommandRoute(
                    method.getKind(),
                    emptyPattern,
                    invocationFactory.create(method, emptyPattern),
                    resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_DESCRIPTION, method.getDescription()),
                    resolveUsage(context, rootAliases.getPrimary(), handler, method, ""),
                    resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_PERMISSION, method.getPermission())
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
                resolveText(context, handler.getHandlerType(), null, CommandTextResolutionContext.TargetType.ROOT_DESCRIPTION, handler.getDescription()),
                resolveUsage(context, rootAliases.getPrimary(), handler, null, ""),
                routes,
                defaultRoute,
                unknownRoute
        );
    }

    private CommandAliasSet resolveRootAliases(Context context, RootCommandMetadata handler) {
        CommandAliasSet aliases = handler.getAliases();
        List<String> expanded = new ArrayList<>();
        for (String rawValue : aliases.allValues()) {
            for (String variant : patternParser.expandAliases(resolveText(context, handler.getHandlerType(), null, CommandTextResolutionContext.TargetType.ROOT_PATH, rawValue))) {
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

    private List<CommandPattern> resolvePatterns(Context context, CommandMethodMetadata method) {
        CommandAliasSet aliases = method.getAliases();
        LinkedHashMap<String, CommandPattern> patterns = new LinkedHashMap<>();
        for (String rawValue : aliases.allValues()) {
            for (String variant : patternParser.expandAliases(resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_PATH, rawValue))) {
                CommandPattern pattern = patternParser.parse(variant);
                patterns.put(pattern.normalizedSignature() + "::" + pattern.getSource(), pattern);
            }
        }
        return new ArrayList<>(patterns.values());
    }

    private String resolveUsage(Context context,
                                String rootLabel,
                                RootCommandMetadata handler,
                                CommandMethodMetadata method,
                                String patternSource) {
        String explicitRouteUsage = method == null
                ? ""
                : resolveText(context, method.getHandlerType(), method.getMethod(), CommandTextResolutionContext.TargetType.COMMAND_USAGE, method.getUsage());
        if (!explicitRouteUsage.isEmpty()) {
            return explicitRouteUsage;
        }

        String explicitRootUsage = resolveText(context, handler.getHandlerType(), null, CommandTextResolutionContext.TargetType.ROOT_USAGE, handler.getUsage());
        if (!explicitRootUsage.isEmpty() && (patternSource == null || patternSource.isEmpty())) {
            return explicitRootUsage;
        }

        if (patternSource == null || patternSource.trim().isEmpty()) {
            return "/" + rootLabel;
        }
        return "/" + rootLabel + " " + patternSource.trim();
    }

    private String resolveText(Context context,
                               Class<?> handlerType,
                               Method method,
                               CommandTextResolutionContext.TargetType targetType,
                               String value) {
        String replaced = replacementRegistry.replace(value);
        CommandTextResolutionContext resolutionContext = method == null
                ? CommandTextResolutionContext.forRoot(context, handlerType, targetType, replaced)
                : CommandTextResolutionContext.forMethod(context, handlerType, method, targetType, replaced);
        String resolved = commandTextResolverChain.resolve(resolutionContext, replaced);
        return resolved == null ? "" : resolved.trim();
    }
}
