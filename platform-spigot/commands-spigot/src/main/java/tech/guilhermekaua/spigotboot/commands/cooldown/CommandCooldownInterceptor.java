package tech.guilhermekaua.spigotboot.commands.cooldown;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import tech.guilhermekaua.spigotboot.commands.CommandAnnotationInterceptor;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandExecutionDecision;
import tech.guilhermekaua.spigotboot.commands.annotations.Cooldown;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.message.CommandMessagesProvider;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownManager;
import tech.guilhermekaua.spigotboot.core.cooldown.CooldownState;

import java.time.Duration;
import java.util.*;

public class CommandCooldownInterceptor implements CommandAnnotationInterceptor<Cooldown> {
    private final CooldownManager cooldownManager;
    private final CommandMessagesProvider commandMessagesProvider;
    private final Map<CommandExecutionContext, PendingCooldown> pendingCooldowns =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public CommandCooldownInterceptor(CooldownManager cooldownManager,
                                      CommandMessagesProvider commandMessagesProvider) {
        this.cooldownManager = Objects.requireNonNull(cooldownManager, "cooldownManager cannot be null.");
        this.commandMessagesProvider = Objects.requireNonNull(commandMessagesProvider, "commandMessagesProvider cannot be null.");
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    @Override
    public void validate(Cooldown annotation,
                         Context context,
                         CommandInvocationPlan invocation) {
        CommandCooldownPolicy policy = resolvePolicy(context, annotation.policy());
        policy.validate(annotation, context, invocation);
    }

    @Override
    public CommandExecutionDecision before(Cooldown annotation,
                                           CommandExecutionContext context,
                                           CommandInvocationPlan invocation) {
        CommandCooldownPolicy policy = resolvePolicy(context.getContext(), annotation.policy());
        Duration duration = policy.resolve(annotation, context, invocation);
        if (duration == null || duration.isZero() || duration.isNegative()) {
            pendingCooldowns.remove(context);
            return CommandExecutionDecision.continueExecution();
        }

        String key = buildCooldownKey(context, invocation);
        CooldownState state = cooldownManager.getState(key);
        if (state.isActive()) {
            context.sendMessage(commandMessagesProvider.resolve(context.getContext()).onCooldown(context, state.getRemaining()));
            return CommandExecutionDecision.stopExecution();
        }

        pendingCooldowns.put(context, new PendingCooldown(key, duration));
        return CommandExecutionDecision.continueExecution();
    }

    @Override
    public void after(Cooldown annotation,
                      CommandExecutionContext context,
                      CommandInvocationPlan invocation,
                      Object result) {
        PendingCooldown pendingCooldown = pendingCooldowns.remove(context);
        if (pendingCooldown == null) {
            return;
        }

        cooldownManager.start(pendingCooldown.key, pendingCooldown.duration);
    }

    @Override
    public void onError(Cooldown annotation,
                        CommandExecutionContext context,
                        CommandInvocationPlan invocation,
                        Throwable throwable) {
        pendingCooldowns.remove(context);
    }

    private String buildCooldownKey(CommandExecutionContext context, CommandInvocationPlan invocation) {
        return "commands:" + invocation.getMethod().toGenericString() + ":" + resolveSenderIdentity(context.getSender());
    }

    private String resolveSenderIdentity(CommandSender sender) {
        if (sender instanceof Entity) {
            return ((Entity) sender).getUniqueId().toString();
        }

        String name = sender.getName();
        return sender.getClass().getName() + ":" + (name == null ? "" : name);
    }

    private CommandCooldownPolicy resolvePolicy(Context context,
                                                Class<? extends CommandCooldownPolicy> policyType) {
        DependencyManager dependencyManager = context.getDependencyManager();
        List<BeanDefinition> definitions = dependencyManager.getBeanDefinitionRegistry().getDefinitions(policyType);
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "No bean was found for command cooldown policy type " + policyType.getName() + "."
            );
        }

        BeanDefinition definition = resolveSingleDefinition(definitions, policyType);
        Object instance = resolveInstance(dependencyManager, definition, policyType);
        if (!policyType.isInstance(instance) || !(instance instanceof CommandCooldownPolicy)) {
            throw new IllegalStateException(
                    "Resolved bean for command cooldown policy type " + policyType.getName() +
                            " does not implement CommandCooldownPolicy."
            );
        }

        return (CommandCooldownPolicy) instance;
    }

    private BeanDefinition resolveSingleDefinition(List<BeanDefinition> definitions,
                                                   Class<? extends CommandCooldownPolicy> policyType) {
        if (definitions.size() == 1) {
            return definitions.get(0);
        }

        List<BeanDefinition> primary = new ArrayList<>();
        for (BeanDefinition definition : definitions) {
            if (definition.isPrimary()) {
                primary.add(definition);
            }
        }

        if (primary.size() == 1) {
            return primary.get(0);
        }

        if (primary.isEmpty()) {
            throw new IllegalStateException(
                    "Multiple beans were found for command cooldown policy type " + policyType.getName() +
                            ". Mark exactly one as @Primary."
            );
        }

        throw new IllegalStateException(
                "Multiple primary beans were found for command cooldown policy type " + policyType.getName() + "."
        );
    }

    private Object resolveInstance(DependencyManager dependencyManager,
                                   BeanDefinition definition,
                                   Class<? extends CommandCooldownPolicy> policyType) {
        try {
            @SuppressWarnings("unchecked")
            Class<Object> definitionType = (Class<Object>) definition.getType();
            Object instance = dependencyManager.resolveFromDefinition(definitionType, definition);
            if (instance == null) {
                throw new IllegalStateException(
                        "Command cooldown policy bean " + policyType.getName() + " resolved to null."
                );
            }
            return instance;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to resolve command cooldown policy bean " + policyType.getName() + ".",
                    e
            );
        }
    }

    private static final class PendingCooldown {
        private final String key;
        private final Duration duration;

        private PendingCooldown(String key, Duration duration) {
            this.key = key;
            this.duration = duration;
        }
    }
}
