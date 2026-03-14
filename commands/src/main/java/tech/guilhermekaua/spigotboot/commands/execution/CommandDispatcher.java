package tech.guilhermekaua.spigotboot.commands.execution;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionDecision;
import tech.guilhermekaua.spigotboot.commands.CommandMessages;
import tech.guilhermekaua.spigotboot.commands.CommandSenderHandle;
import tech.guilhermekaua.spigotboot.commands.binding.CommandBindingException;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinder;
import tech.guilhermekaua.spigotboot.commands.binding.CommandParameterBinding;
import tech.guilhermekaua.spigotboot.commands.completion.CompletionResolver;
import tech.guilhermekaua.spigotboot.commands.interceptor.CommandInterceptorChain;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;
import tech.guilhermekaua.spigotboot.commands.route.CompiledCommandRoute;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.*;
import java.util.logging.Level;

public class CommandDispatcher {
    private final CommandParameterBinder parameterBinder;
    private final CommandInvocationExecutor invocationExecutor;
    private final CommandMessagesProvider messagesProvider;
    private final CompletionResolver completionResolver;
    private final CommandInterceptorChain interceptorChain;

    public CommandDispatcher(CommandParameterBinder parameterBinder,
                             CommandInvocationExecutor invocationExecutor,
                             CommandMessagesProvider messagesProvider,
                             CompletionResolver completionResolver) {
        this(parameterBinder, invocationExecutor, messagesProvider, completionResolver, new CommandInterceptorChain());
    }

    public CommandDispatcher(CommandParameterBinder parameterBinder,
                             CommandInvocationExecutor invocationExecutor,
                             CommandMessagesProvider messagesProvider,
                             CompletionResolver completionResolver,
                             CommandInterceptorChain interceptorChain) {
        this.parameterBinder = parameterBinder;
        this.invocationExecutor = invocationExecutor;
        this.messagesProvider = messagesProvider;
        this.completionResolver = completionResolver;
        this.interceptorChain = interceptorChain;
    }

    public boolean dispatch(Context context, CompiledRootCommand root, CommandSenderHandle sender, String label, String[] args) {
        MatchResult match = findBestMatch(root, args);
        if (match == null) {
            if (args.length > 0 && root.getUnknownRoute() != null) {
                DefaultCommandExecutionContext unknownContext = createContext(context, root, root.getUnknownRoute(), sender, label, args, Collections.emptyMap());
                return execute(root.getUnknownRoute(), unknownContext);
            }

            DefaultCommandExecutionContext executionContext = createContext(context, root, null, sender, label, args, Collections.emptyMap());
            CommandMessages messages = messagesProvider.resolve(context);
            if (args.length > 0) {
                executionContext.sendMessage(messages.unknownSubcommand(executionContext));
            }
            if (!executionContext.getUsage().isEmpty()) {
                executionContext.sendMessage(messages.usage(executionContext, executionContext.getUsage()));
            }
            return true;
        }

        DefaultCommandExecutionContext executionContext = createContext(context, root, match.route, sender, label, args, match.parsedArguments);
        return execute(match.route, executionContext);
    }

    public List<String> complete(Context context, CompiledRootCommand root, CommandSenderHandle sender, String label, String[] args) {
        String[] completionArgs = args == null ? new String[0] : args;
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        for (CompiledCommandRoute route : root.getRoutes()) {
            if (!route.getPermission().isEmpty() && !sender.hasPermission(route.getPermission())) {
                continue;
            }

            MatchProgress progress = progress(route, completionArgs);
            if (progress == null || progress.nextSegment == null) {
                continue;
            }

            DefaultCommandExecutionContext executionContext = createContext(context, root, route, sender, label, completionArgs, progress.parsedArguments);
            CommandPattern.CommandSegment segment = progress.nextSegment;
            if (segment instanceof CommandPattern.LiteralSegment) {
                String literal = ((CommandPattern.LiteralSegment) segment).getLiteral();
                String prefix = CommandSupport.normalizeLabel(progress.input);
                if (prefix.isEmpty() || literal.startsWith(prefix)) {
                    suggestions.add(literal);
                }
                continue;
            }

            CommandPattern.ArgumentSegment argumentSegment = (CommandPattern.ArgumentSegment) segment;
            CommandParameterBinding binding = route.getInvocationPlan().getParsedBinding(argumentSegment.getName());
            if (binding != null) {
                suggestions.addAll(completionResolver.resolve(executionContext, binding, progress.input));
            }
        }
        return new ArrayList<>(suggestions);
    }

    private boolean execute(CompiledCommandRoute route, DefaultCommandExecutionContext context) {
        CommandMessages messages = messagesProvider.resolve(context.getContext());
        CommandInvocationPlan invocation = route.getInvocationPlan();
        CommandInterceptorChain.ResolvedChain resolvedChain = interceptorChain.resolve(context, invocation);
        try {
            if (!route.getPermission().isEmpty() && !context.getSender().hasPermission(route.getPermission())) {
                context.sendMessage(messages.noPermission(context, route.getPermission()));
                return true;
            }

            CommandExecutionDecision decision;
            try {
                decision = resolvedChain.before(context, invocation);
            } catch (Throwable throwable) {
                resolvedChain.onError(context, invocation, throwable);
                throw throwable;
            }

            if (!decision.shouldContinue()) {
                return true;
            }

            Object[] arguments;
            try {
                arguments = parameterBinder.bind(context, invocation, context.getParsedArguments());
            } catch (CommandBindingException e) {
                resolvedChain.onError(context, invocation, e);
                handleBindingException(context, messages, e);
                return true;
            }

            invocationExecutor.execute(context, invocation, arguments, resolvedChain);
            return true;
        } catch (Throwable throwable) {
            context.sendMessage(messages.executionError(context, throwable));
            context.getPlugin().getLogger().log(Level.SEVERE, "Error executing command " + context.getInput(), throwable);
            return true;
        }
    }

    private void handleBindingException(DefaultCommandExecutionContext context,
                                        CommandMessages messages,
                                        CommandBindingException exception) {
        switch (exception.getKind()) {
            case MISSING_ARGUMENT:
                context.sendMessage(messages.missingRequiredArgument(context, exception.getParameter()));
                if (!context.getUsage().isEmpty()) {
                    context.sendMessage(messages.usage(context, context.getUsage()));
                }
                return;
            case INVALID_ARGUMENT:
                context.sendMessage(messages.invalidArgumentValue(context, exception.getParameter(), exception.getInput()));
                return;
            case SENDER_MISMATCH:
                context.sendMessage(messages.senderTypeMismatch(context, exception.getExpectedSenderType()));
                return;
            default:
                throw new IllegalStateException("Unhandled binding exception type: " + exception.getKind());
        }
    }

    private MatchResult findBestMatch(CompiledRootCommand root, String[] args) {
        List<MatchResult> matches = new ArrayList<>();
        if (args.length == 0 && root.getDefaultRoute() != null) {
            matches.add(new MatchResult(root.getDefaultRoute(), Collections.emptyMap(), 0, 0, 0));
        }

        for (CompiledCommandRoute route : root.getRoutes()) {
            MatchResult result = match(route, args);
            if (result != null) {
                matches.add(result);
            }
        }

        if (matches.isEmpty()) {
            return null;
        }

        matches.sort(MATCH_COMPARATOR);
        if (matches.size() > 1 && compareSpecificity(matches.get(0), matches.get(1)) == 0) {
            throw new IllegalStateException("Ambiguous command match under root '" + root.getAliases().getPrimary() + "'.");
        }
        return matches.get(0);
    }

    private MatchResult match(CompiledCommandRoute route, String[] args) {
        List<CommandPattern.CommandSegment> segments = route.getPattern().getSegments();
        Map<String, String> parsedArguments = new LinkedHashMap<>();
        int inputIndex = 0;
        int consumedSegments = 0;
        int omittedOptionals = 0;

        for (CommandPattern.CommandSegment segment : segments) {
            if (segment instanceof CommandPattern.LiteralSegment) {
                if (inputIndex >= args.length) {
                    return null;
                }

                String literal = ((CommandPattern.LiteralSegment) segment).getLiteral();
                if (!literal.equals(CommandSupport.normalizeLabel(args[inputIndex]))) {
                    return null;
                }

                inputIndex++;
                consumedSegments++;
                continue;
            }

            CommandPattern.ArgumentSegment argumentSegment = (CommandPattern.ArgumentSegment) segment;
            CommandParameterBinding binding = route.getInvocationPlan().getParsedBinding(argumentSegment.getName());
            boolean greedy = binding != null && binding.isGreedy();
            if (greedy) {
                int remaining = args.length - inputIndex;
                if (remaining <= 0) {
                    if (argumentSegment.isOptional()) {
                        omittedOptionals++;
                        continue;
                    }
                    return null;
                }

                parsedArguments.put(argumentSegment.getName(), CommandSupport.joinArgs(args, inputIndex));
                inputIndex = args.length;
                consumedSegments++;
                continue;
            }

            if (inputIndex >= args.length) {
                if (argumentSegment.isOptional()) {
                    omittedOptionals++;
                    continue;
                }
                return null;
            }

            parsedArguments.put(argumentSegment.getName(), args[inputIndex]);
            inputIndex++;
            consumedSegments++;
        }

        if (inputIndex != args.length) {
            return null;
        }

        return new MatchResult(route, parsedArguments, route.getPattern().getLiteralCount(), consumedSegments, omittedOptionals);
    }

    private MatchProgress progress(CompiledCommandRoute route, String[] args) {
        List<CommandPattern.CommandSegment> segments = route.getPattern().getSegments();
        Map<String, String> parsed = new LinkedHashMap<>();
        int inputIndex = 0;

        if (args.length == 0) {
            return segments.isEmpty() ? null : new MatchProgress(parsed, segments.get(0), "");
        }

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            CommandPattern.CommandSegment segment = segments.get(segmentIndex);
            if (inputIndex >= args.length) {
                return new MatchProgress(parsed, segment, "");
            }

            String current = args[inputIndex];
            boolean lastProvidedToken = inputIndex == args.length - 1;
            if (segment instanceof CommandPattern.LiteralSegment) {
                String literal = ((CommandPattern.LiteralSegment) segment).getLiteral();
                String normalizedCurrent = CommandSupport.normalizeLabel(current);
                if (!literal.equals(normalizedCurrent)) {
                    if (lastProvidedToken && literal.startsWith(normalizedCurrent)) {
                        return new MatchProgress(parsed, segment, current);
                    }
                    return null;
                }

                inputIndex++;
                if (lastProvidedToken) {
                    return segmentIndex + 1 < segments.size()
                            ? new MatchProgress(parsed, segments.get(segmentIndex + 1), "")
                            : null;
                }
                continue;
            }

            CommandPattern.ArgumentSegment argumentSegment = (CommandPattern.ArgumentSegment) segment;
            CommandParameterBinding binding = route.getInvocationPlan().getParsedBinding(argumentSegment.getName());
            if (lastProvidedToken) {
                return new MatchProgress(parsed, segment, current);
            }

            if (binding != null && binding.isGreedy()) {
                return new MatchProgress(parsed, segment, current);
            }

            parsed.put(argumentSegment.getName(), current);
            inputIndex++;
        }

        return null;
    }

    private DefaultCommandExecutionContext createContext(Context context,
                                                         CompiledRootCommand root,
                                                         CompiledCommandRoute route,
                                                         CommandSenderHandle sender,
                                                         String label,
                                                         String[] args,
                                                         Map<String, String> parsedArguments) {
        String usage = route != null && !route.getUsage().isEmpty() ? route.getUsage() : root.getUsage();
        String input = "/" + label + (args.length == 0 ? "" : " " + CommandSupport.joinArgs(args, 0));
        return new DefaultCommandExecutionContext(
                context,
                sender,
                label,
                input,
                Arrays.asList(args.clone()),
                parsedArguments,
                usage
        );
    }

    private static int compareSpecificity(MatchResult left, MatchResult right) {
        if (left.literalMatches != right.literalMatches) {
            return Integer.compare(right.literalMatches, left.literalMatches);
        }
        if (left.consumedSegments != right.consumedSegments) {
            return Integer.compare(right.consumedSegments, left.consumedSegments);
        }
        if (left.omittedOptionals != right.omittedOptionals) {
            return Integer.compare(left.omittedOptionals, right.omittedOptionals);
        }
        return 0;
    }

    private static final Comparator<MatchResult> MATCH_COMPARATOR = (left, right) -> {
        int specificity = compareSpecificity(left, right);
        if (specificity != 0) {
            return specificity;
        }
        return left.route.getPattern().normalizedSignature().compareTo(right.route.getPattern().normalizedSignature());
    };

    private static final class MatchResult {
        private final CompiledCommandRoute route;
        private final Map<String, String> parsedArguments;
        private final int literalMatches;
        private final int consumedSegments;
        private final int omittedOptionals;

        private MatchResult(CompiledCommandRoute route,
                            Map<String, String> parsedArguments,
                            int literalMatches,
                            int consumedSegments,
                            int omittedOptionals) {
            this.route = route;
            this.parsedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(parsedArguments));
            this.literalMatches = literalMatches;
            this.consumedSegments = consumedSegments;
            this.omittedOptionals = omittedOptionals;
        }
    }

    private static final class MatchProgress {
        private final Map<String, String> parsedArguments;
        private final CommandPattern.CommandSegment nextSegment;
        private final String input;

        private MatchProgress(Map<String, String> parsedArguments, CommandPattern.CommandSegment nextSegment, String input) {
            this.parsedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(parsedArguments));
            this.nextSegment = nextSegment;
            this.input = input == null ? "" : input;
        }
    }
}
