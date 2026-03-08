package tech.guilhermekaua.spigotboot.commands.binding;

import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandPlatformSupport;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandMethodMetadata;
import tech.guilhermekaua.spigotboot.commands.metadata.CommandParameterMetadata;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;

import java.util.*;

public class CommandParameterRoleResolver {
    private final CommandPlatformSupport commandPlatformSupport;

    public CommandParameterRoleResolver(CommandPlatformSupport commandPlatformSupport) {
        this.commandPlatformSupport = commandPlatformSupport;
    }

    public List<CommandParameterBinding> resolve(CommandMethodMetadata method, CommandPattern pattern) {
        Map<String, CommandPattern.ArgumentSegment> segmentsByName = new LinkedHashMap<>();
        List<String> tokenOrder = new ArrayList<>();
        for (CommandPattern.CommandSegment segment : pattern.getSegments()) {
            if (segment instanceof CommandPattern.ArgumentSegment) {
                CommandPattern.ArgumentSegment argumentSegment = (CommandPattern.ArgumentSegment) segment;
                segmentsByName.put(argumentSegment.getName(), argumentSegment);
                tokenOrder.add(argumentSegment.getName());
            }
        }

        List<CommandParameterBinding> bindings = new ArrayList<>(Collections.nCopies(method.getParameters().size(), null));
        Map<String, Integer> tokenToIndex = new HashMap<>();
        List<Integer> senderCandidates = new ArrayList<>();
        Integer explicitSenderIndex = null;

        for (CommandParameterMetadata parameter : method.getParameters()) {
            if (parameter.isSenderMarked()) {
                if (segmentsByName.containsKey(parameter.getLookupName())) {
                    throw new IllegalStateException("@Sender parameter cannot also map to a command token in " + method.getMethod());
                }
                if (explicitSenderIndex != null) {
                    throw new IllegalStateException("Multiple @Sender parameters found in " + method.getMethod());
                }
                explicitSenderIndex = parameter.getIndex();
                bindings.set(parameter.getIndex(), new CommandParameterBinding(parameter, CommandParameterRole.SENDER, null, false, false, null));
                continue;
            }

            if (CommandExecutionContext.class.isAssignableFrom(parameter.getRawType())) {
                bindings.set(parameter.getIndex(), new CommandParameterBinding(parameter, CommandParameterRole.CONTEXT, null, false, false, null));
                continue;
            }

            CommandPattern.ArgumentSegment segment = segmentsByName.get(parameter.getLookupName());
            if (segment != null) {
                Integer previous = tokenToIndex.put(parameter.getLookupName(), parameter.getIndex());
                if (previous != null) {
                    throw new IllegalStateException("Multiple parameters map to token '" + parameter.getLookupName() + "' in " + method.getMethod());
                }
                boolean greedy = isGreedy(pattern, segment, parameter);
                if (segment.isOptional() && parameter.isPrimitive() && !parameter.hasDefaultValue()) {
                    throw new IllegalStateException("Optional primitive parameters require @DefaultValue in " + method.getMethod());
                }
                bindings.set(parameter.getIndex(), new CommandParameterBinding(
                        parameter,
                        CommandParameterRole.PARSED,
                        parameter.getLookupName(),
                        segment.isOptional(),
                        greedy,
                        parameter.getExplicitCompletion()
                ));
                continue;
            }

            if (!parameter.isOptionalWrapper() && commandPlatformSupport.isSenderType(parameter.getValueType())) {
                senderCandidates.add(parameter.getIndex());
            }
        }

        for (String tokenName : tokenOrder) {
            if (!tokenToIndex.containsKey(tokenName)) {
                throw new IllegalStateException("No method parameter maps to token '" + tokenName + "' in " + method.getMethod());
            }
        }

        if (explicitSenderIndex != null) {
            if (!senderCandidates.isEmpty()) {
                throw new IllegalStateException("Additional sender-compatible parameters require explicit restructuring in " + method.getMethod());
            }
        } else if (senderCandidates.size() > 1) {
            throw new IllegalStateException("Multiple sender-compatible parameters require exactly one @Sender in " + method.getMethod());
        } else if (senderCandidates.size() == 1) {
            Integer index = senderCandidates.get(0);
            CommandParameterMetadata parameter = method.getParameters().get(index);
            bindings.set(index, new CommandParameterBinding(parameter, CommandParameterRole.SENDER, null, false, false, null));
        }

        for (int index = 0; index < bindings.size(); index++) {
            if (bindings.get(index) == null) {
                CommandParameterMetadata parameter = method.getParameters().get(index);
                bindings.set(index, new CommandParameterBinding(parameter, CommandParameterRole.INJECTED, null, false, false, null));
            }
        }

        applyMethodLevelCompletions(method, tokenOrder, tokenToIndex, bindings);
        return Collections.unmodifiableList(bindings);
    }

    private void applyMethodLevelCompletions(CommandMethodMetadata method,
                                             List<String> tokenOrder,
                                             Map<String, Integer> tokenToIndex,
                                             List<CommandParameterBinding> bindings) {
        List<String> completionIds = method.getCompletionIds();
        for (int index = 0; index < tokenOrder.size(); index++) {
            String tokenName = tokenOrder.get(index);
            Integer parameterIndex = tokenToIndex.get(tokenName);
            if (parameterIndex == null) {
                continue;
            }

            CommandParameterBinding binding = bindings.get(parameterIndex);
            if (!binding.getCompletionId().isEmpty() || index >= completionIds.size()) {
                continue;
            }
            bindings.set(parameterIndex, binding.withCompletionId(completionIds.get(index)));
        }
    }

    private boolean isGreedy(CommandPattern pattern,
                             CommandPattern.ArgumentSegment segment,
                             CommandParameterMetadata parameter) {
        List<CommandPattern.CommandSegment> segments = pattern.getSegments();
        if (segments.isEmpty()) {
            return false;
        }
        return segments.get(segments.size() - 1) == segment && String.class.equals(parameter.getValueType());
    }
}
