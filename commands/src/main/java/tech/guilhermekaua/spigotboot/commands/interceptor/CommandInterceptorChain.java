package tech.guilhermekaua.spigotboot.commands.interceptor;

import tech.guilhermekaua.spigotboot.commands.*;
import tech.guilhermekaua.spigotboot.commands.execution.CommandInvocationPlan;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.route.CompiledCommandRoute;
import tech.guilhermekaua.spigotboot.commands.route.CompiledRootCommand;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.dependency.BeanDefinition;
import tech.guilhermekaua.spigotboot.core.context.dependency.manager.DependencyManager;
import tech.guilhermekaua.spigotboot.utils.ProxyUtils;

import java.lang.annotation.Annotation;
import java.util.*;

public class CommandInterceptorChain {
    public ResolvedChain resolve(CommandExecutionContext context, CommandInvocationPlan invocation) {
        Objects.requireNonNull(context, "context cannot be null.");
        Objects.requireNonNull(invocation, "invocation cannot be null.");

        List<ResolvedInterceptor> resolved = new ArrayList<>();
        resolved.addAll(resolveGlobalInterceptors(context));
        resolved.addAll(resolveAnnotationInterceptors(context, invocation));
        sortResolvedInterceptors(resolved);
        return new ResolvedChain(resolved);
    }

    public void validate(Context context, Collection<CompiledRootCommand> roots) {
        Objects.requireNonNull(context, "context cannot be null.");
        Objects.requireNonNull(roots, "roots cannot be null.");

        validateGlobalInterceptors(context);

        Map<Class<? extends CommandAnnotationInterceptor<?>>, CommandAnnotationInterceptor<?>> resolved =
                new IdentityHashMap<>();
        for (CompiledRootCommand root : roots) {
            validate(context, root.getRoutes(), resolved);
            validate(context, root.getDefaultRoute(), resolved);
            validate(context, root.getUnknownRoute(), resolved);
        }
    }

    private void validate(Context context,
                          Collection<CompiledCommandRoute> routes,
                          Map<Class<? extends CommandAnnotationInterceptor<?>>, CommandAnnotationInterceptor<?>> resolved) {
        for (CompiledCommandRoute route : routes) {
            validate(context, route, resolved);
        }
    }

    private void validate(Context context,
                          CompiledCommandRoute route,
                          Map<Class<? extends CommandAnnotationInterceptor<?>>, CommandAnnotationInterceptor<?>> resolved) {
        if (route == null) {
            return;
        }

        for (CommandInterceptorAnnotationBinding binding : route.getInvocationPlan().getInterceptorBindings()) {
            for (Class<? extends CommandAnnotationInterceptor<?>> interceptorType : binding.getInterceptorTypes()) {
                CommandAnnotationInterceptor<?> interceptor = resolved.get(interceptorType);
                if (interceptor == null) {
                    interceptor = resolveAnnotationInterceptor(context, interceptorType);
                    resolved.put(interceptorType, interceptor);
                }
                validateAnnotationInterceptor(interceptor, binding, context, route.getInvocationPlan());
            }
        }
    }

    private List<ResolvedInterceptor> resolveGlobalInterceptors(CommandExecutionContext context) {
        Collection<Object> instances = context.getContext()
                .getDependencyManager()
                .getBeanInstanceRegistry()
                .asMapView()
                .values();

        List<ResolvedInterceptor> interceptors = new ArrayList<>();
        for (Object instance : CommandSupport.deduplicateByIdentity(instances)) {
            if (!(instance instanceof CommandInterceptor)) {
                continue;
            }

            validateInterceptorInterfaces(instance, "Global command interceptor");
            interceptors.add(ResolvedInterceptor.global((CommandInterceptor) instance));
        }
        return interceptors;
    }

    private List<ResolvedInterceptor> resolveAnnotationInterceptors(CommandExecutionContext context,
                                                                    CommandInvocationPlan invocation) {
        List<ResolvedInterceptor> interceptors = new ArrayList<>();
        Context containerContext = context.getContext();
        for (CommandInterceptorAnnotationBinding binding : invocation.getInterceptorBindings()) {
            for (Class<? extends CommandAnnotationInterceptor<?>> interceptorType : binding.getInterceptorTypes()) {
                CommandAnnotationInterceptor<?> interceptor = resolveAnnotationInterceptor(containerContext, interceptorType);
                interceptors.add(ResolvedInterceptor.annotation(interceptor, binding));
            }
        }
        return interceptors;
    }

    private void validateGlobalInterceptors(Context context) {
        Collection<Object> instances = context.getDependencyManager().getBeanInstanceRegistry().asMapView().values();
        for (Object instance : CommandSupport.deduplicateByIdentity(instances)) {
            if (instance instanceof CommandInterceptor) {
                validateInterceptorInterfaces(instance, "Global command interceptor");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateAnnotationInterceptor(CommandAnnotationInterceptor<?> interceptor,
                                               CommandInterceptorAnnotationBinding binding,
                                               Context context,
                                               CommandInvocationPlan invocation) {
        ((CommandAnnotationInterceptor<Annotation>) interceptor).validate(
                binding.getAnnotation(),
                context,
                invocation
        );
    }

    private CommandAnnotationInterceptor<?> resolveAnnotationInterceptor(Context context,
                                                                         Class<? extends CommandAnnotationInterceptor<?>> interceptorType) {
        DependencyManager dependencyManager = context.getDependencyManager();
        List<BeanDefinition> definitions = dependencyManager.getBeanDefinitionRegistry().getDefinitions(interceptorType);
        if (definitions.isEmpty()) {
            throw new IllegalStateException(
                    "No bean was found for annotation interceptor type " + interceptorType.getName() + "."
            );
        }

        BeanDefinition definition = resolveSingleDefinition(definitions, interceptorType);
        Object instance = resolveInstance(dependencyManager, definition, interceptorType);
        if (!interceptorType.isInstance(instance) || !(instance instanceof CommandAnnotationInterceptor)) {
            throw new IllegalStateException(
                    "Resolved bean for annotation interceptor type " + interceptorType.getName() +
                            " does not implement CommandAnnotationInterceptor."
            );
        }

        validateInterceptorInterfaces(instance, "Annotation command interceptor " + interceptorType.getName());
        return (CommandAnnotationInterceptor<?>) instance;
    }

    private BeanDefinition resolveSingleDefinition(List<BeanDefinition> definitions,
                                                   Class<? extends CommandAnnotationInterceptor<?>> interceptorType) {
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
                    "Multiple beans were found for annotation interceptor type " + interceptorType.getName() +
                            ". Mark exactly one as @Primary."
            );
        }

        throw new IllegalStateException(
                "Multiple primary beans were found for annotation interceptor type " + interceptorType.getName() + "."
        );
    }

    private Object resolveInstance(DependencyManager dependencyManager,
                                   BeanDefinition definition,
                                   Class<? extends CommandAnnotationInterceptor<?>> interceptorType) {
        try {
            @SuppressWarnings("unchecked")
            Class<Object> definitionType = (Class<Object>) definition.getType();
            Object instance = dependencyManager.resolveFromDefinition(definitionType, definition);
            if (instance == null) {
                throw new IllegalStateException(
                        "Annotation interceptor bean " + interceptorType.getName() + " resolved to null."
                );
            }
            return instance;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to resolve annotation interceptor bean " + interceptorType.getName() + ".",
                    e
            );
        }
    }

    private void validateInterceptorInterfaces(Object instance, String label) {
        if (instance instanceof CommandInterceptor && instance instanceof CommandAnnotationInterceptor) {
            throw new IllegalStateException(
                    label + " " + ProxyUtils.getRealClass(instance).getName() +
                            " cannot implement both CommandInterceptor and CommandAnnotationInterceptor."
            );
        }
    }

    private void sortResolvedInterceptors(List<ResolvedInterceptor> interceptors) {
        interceptors.sort((left, right) -> {
            int leftOrder = CommandSupport.resolveOrder(left.getBean());
            int rightOrder = CommandSupport.resolveOrder(right.getBean());
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }

            int beanNameComparison = left.getBeanClassName().compareTo(right.getBeanClassName());
            if (beanNameComparison != 0) {
                return beanNameComparison;
            }

            return left.getAnnotationTypeName().compareTo(right.getAnnotationTypeName());
        });
    }

    public static final class ResolvedChain {
        private final List<ResolvedInterceptor> interceptors;
        private final List<ResolvedInterceptor> engagedInterceptors = new ArrayList<>();

        private ResolvedChain(List<ResolvedInterceptor> interceptors) {
            this.interceptors = Collections.unmodifiableList(new ArrayList<>(interceptors));
        }

        public CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
            for (ResolvedInterceptor interceptor : interceptors) {
                engagedInterceptors.add(interceptor);

                CommandExecutionDecision decision = interceptor.before(context, invocation);
                if (decision == null) {
                    throw new IllegalStateException(
                            "Command interceptor " + interceptor.getBeanClassName() + " returned a null CommandExecutionDecision."
                    );
                }

                if (!decision.shouldContinue()) {
                    return decision;
                }
            }

            return CommandExecutionDecision.continueExecution();
        }

        public void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) throws Throwable {
            Throwable primaryFailure = null;
            for (int i = engagedInterceptors.size() - 1; i >= 0; i--) {
                ResolvedInterceptor interceptor = engagedInterceptors.get(i);
                try {
                    interceptor.after(context, invocation, result);
                } catch (Throwable afterFailure) {
                    if (primaryFailure == null) {
                        primaryFailure = afterFailure;
                        continue;
                    }

                    primaryFailure.addSuppressed(afterFailure);
                    logAfterFailure(context, interceptor, primaryFailure, afterFailure);
                }
            }

            if (primaryFailure != null) {
                throw primaryFailure;
            }
        }

        public void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable) {
            for (int i = engagedInterceptors.size() - 1; i >= 0; i--) {
                ResolvedInterceptor interceptor = engagedInterceptors.get(i);
                try {
                    interceptor.onError(context, invocation, throwable);
                } catch (Throwable secondaryFailure) {
                    throwable.addSuppressed(secondaryFailure);
                    logSecondaryFailure(context, interceptor, throwable, secondaryFailure);
                }
            }
        }

        private void logSecondaryFailure(CommandExecutionContext context,
                                         ResolvedInterceptor interceptor,
                                         Throwable primaryFailure,
                                         Throwable secondaryFailure) {
            context.getPlugin().getLogger().severe(
                    "Command interceptor " + interceptor.getBeanClassName() +
                            " threw while handling a command failure triggered by " +
                            primaryFailure.getClass().getName() + "."
            );
            secondaryFailure.printStackTrace();
        }

        private void logAfterFailure(CommandExecutionContext context,
                                     ResolvedInterceptor interceptor,
                                     Throwable primaryFailure,
                                     Throwable secondaryFailure) {
            context.getPlugin().getLogger().severe(
                    "Command interceptor " + interceptor.getBeanClassName() +
                            " threw while running after() for a command whose earlier after() interceptor already failed with " +
                            primaryFailure.getClass().getName() + "."
            );
            secondaryFailure.printStackTrace();
        }
    }

    private abstract static class ResolvedInterceptor {
        private final Object bean;
        private final String beanClassName;
        private final String annotationTypeName;

        private ResolvedInterceptor(Object bean, String annotationTypeName) {
            this.bean = bean;
            this.beanClassName = ProxyUtils.getRealClass(bean).getName();
            this.annotationTypeName = annotationTypeName == null ? "" : annotationTypeName;
        }

        static ResolvedInterceptor global(CommandInterceptor interceptor) {
            return new GlobalResolvedInterceptor(interceptor);
        }

        static ResolvedInterceptor annotation(CommandAnnotationInterceptor<?> interceptor,
                                              CommandInterceptorAnnotationBinding binding) {
            return new AnnotationResolvedInterceptor(interceptor, binding);
        }

        Object getBean() {
            return bean;
        }

        String getBeanClassName() {
            return beanClassName;
        }

        String getAnnotationTypeName() {
            return annotationTypeName;
        }

        abstract CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation);

        abstract void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) throws Throwable;

        abstract void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable);
    }

    private static final class GlobalResolvedInterceptor extends ResolvedInterceptor {
        private final CommandInterceptor interceptor;

        private GlobalResolvedInterceptor(CommandInterceptor interceptor) {
            super(interceptor, "");
            this.interceptor = interceptor;
        }

        @Override
        CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
            return interceptor.before(context, invocation);
        }

        @Override
        void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) {
            interceptor.after(context, invocation, result);
        }

        @Override
        void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable) {
            interceptor.onError(context, invocation, throwable);
        }
    }

    private static final class AnnotationResolvedInterceptor extends ResolvedInterceptor {
        private final CommandAnnotationInterceptor<Annotation> interceptor;
        private final Annotation annotation;

        @SuppressWarnings("unchecked")
        private AnnotationResolvedInterceptor(CommandAnnotationInterceptor<?> interceptor,
                                              CommandInterceptorAnnotationBinding binding) {
            super(interceptor, binding.getAnnotationType().getName());
            this.interceptor = (CommandAnnotationInterceptor<Annotation>) interceptor;
            this.annotation = binding.getAnnotation();
        }

        @Override
        CommandExecutionDecision before(CommandExecutionContext context, CommandInvocationPlan invocation) {
            return interceptor.before(annotation, context, invocation);
        }

        @Override
        void after(CommandExecutionContext context, CommandInvocationPlan invocation, Object result) {
            interceptor.after(annotation, context, invocation, result);
        }

        @Override
        void onError(CommandExecutionContext context, CommandInvocationPlan invocation, Throwable throwable) {
            interceptor.onError(annotation, context, invocation, throwable);
        }
    }
}
