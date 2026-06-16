package tech.guilhermekaua.spigotboot.commands.config.spigot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolutionContext;
import tech.guilhermekaua.spigotboot.commands.CommandTextResolver;
import tech.guilhermekaua.spigotboot.config.ConfigManager;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReference;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.DefaultConfigManager;
import tech.guilhermekaua.spigotboot.config.reference.DefaultConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.core.context.Context;

import java.util.Optional;

public class ConfigBackedCommandTextResolver implements CommandTextResolver {
    private static final String TOKEN_PREFIX = "${";

    private final ConfigReferenceParser parser = new ConfigReferenceParser();

    @Override
    public String resolve(CommandTextResolutionContext resolutionContext, String value) {
        String text = value == null ? "" : value;
        if (!text.contains(TOKEN_PREFIX)) {
            return text;
        }
        String firstToken = firstTokenCandidate(text);

        Context runtimeContext = resolutionContext.getContext();
        if (runtimeContext == null) {
            throw failure(resolutionContext, firstToken,
                    "no runtime Context was provided. Use CommandRouteFactory#create(Context, ...) for context-dependent resolvers.");
        }

        ConfigManager configManager = runtimeContext.getBean(ConfigManager.class);
        if (configManager == null) {
            throw failure(resolutionContext, firstToken, "no ConfigManager bean is available.");
        }
        if (!(configManager instanceof DefaultConfigManager)) {
            throw failure(resolutionContext, firstToken,
                    "the available ConfigManager is not a DefaultConfigManager: " + configManager.getClass().getName());
        }

        DefaultConfigManager defaultConfigManager = (DefaultConfigManager) configManager;
        DefaultConfigReferenceLookup lookup = new DefaultConfigReferenceLookup(defaultConfigManager);
        ConfigReferenceResolver nestedResolver = new ConfigReferenceResolver(
                lookup,
                parser,
                new ThrowingConfigReferenceErrorHandler(resolutionContext)
        );

        StringBuilder resolved = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            int tokenStart = text.indexOf(TOKEN_PREFIX, cursor);
            if (tokenStart < 0) {
                resolved.append(text.substring(cursor));
                break;
            }

            resolved.append(text.substring(cursor, tokenStart));
            int tokenEnd = text.indexOf('}', tokenStart + TOKEN_PREFIX.length());
            if (tokenEnd < 0) {
                throw failure(resolutionContext, text.substring(tokenStart), "unclosed config reference token.");
            }

            String token = text.substring(tokenStart, tokenEnd + 1);
            resolved.append(resolveToken(resolutionContext, lookup, nestedResolver, token));
            cursor = tokenEnd + 1;
        }

        return resolved.toString();
    }

    private String firstTokenCandidate(String text) {
        int tokenStart = text.indexOf(TOKEN_PREFIX);
        if (tokenStart < 0) {
            return null;
        }
        int tokenEnd = text.indexOf('}', tokenStart + TOKEN_PREFIX.length());
        if (tokenEnd < 0) {
            return text.substring(tokenStart);
        }
        return text.substring(tokenStart, tokenEnd + 1);
    }

    private String resolveToken(CommandTextResolutionContext resolutionContext,
                                DefaultConfigReferenceLookup lookup,
                                ConfigReferenceResolver nestedResolver,
                                String token) {
        Optional<ConfigReference> parsedReference = parser.tryParse(token);
        if (!parsedReference.isPresent()) {
            throw failure(resolutionContext, token, "invalid config reference token.");
        }

        ConfigReference reference = parsedReference.get();
        ConfigNode node = lookup.resolve(reference);
        if (node == null) {
            throw failure(resolutionContext, token, "reference not found.");
        }

        ConfigNode resolvedNode = nestedResolver.deepResolve(node, ReferenceKey.fromReference(reference));
        if (resolvedNode == null || resolvedNode.isNull() || resolvedNode.isVirtual()) {
            throw failure(resolutionContext, token, "resolved to a null value.");
        }
        if (!resolvedNode.isScalar()) {
            throw failure(resolutionContext, token, "resolved to a " + describeNode(resolvedNode) + " value.");
        }

        Object rawValue = resolvedNode.raw();
        if (rawValue == null) {
            throw failure(resolutionContext, token, "resolved to a null value.");
        }
        return String.valueOf(rawValue);
    }

    private String describeNode(ConfigNode node) {
        if (node.isMap()) {
            return "map";
        }
        if (node.isList()) {
            return "list";
        }
        if (node.isScalar()) {
            return "scalar";
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isVirtual()) {
            return "virtual";
        }
        return "non-scalar";
    }

    private IllegalStateException failure(CommandTextResolutionContext resolutionContext,
                                          @Nullable String token,
                                          String reason) {
        StringBuilder builder = new StringBuilder("Failed to resolve config-backed command text in ")
                .append(resolutionContext.describeLocation());
        if (token != null && !token.trim().isEmpty()) {
            builder.append(" for token '").append(token).append('\'');
        }
        builder.append(": ").append(reason);
        return new IllegalStateException(builder.toString());
    }

    private final class ThrowingConfigReferenceErrorHandler implements ConfigReferenceErrorHandler {
        private final CommandTextResolutionContext resolutionContext;

        private ThrowingConfigReferenceErrorHandler(CommandTextResolutionContext resolutionContext) {
            this.resolutionContext = resolutionContext;
        }

        @Override
        public @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context) {
            String message = "reference not found";
            if (!context.getAvailableAlternatives().isEmpty()) {
                message += ". Available alternatives: " + context.getAvailableAlternatives();
            }
            throw failure(resolutionContext, context.getFullReference(), message + ".");
        }

        @Override
        public void onCircularReference(@NotNull ConfigCircularReferenceContext context) {
            throw failure(
                    resolutionContext,
                    context.getFullReference(),
                    "circular reference detected: " + context.formatChain() + '.'
            );
        }

        @Override
        public @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context) {
            throw failure(
                    resolutionContext,
                    context.getFullReference(),
                    "type mismatch. Expected " + context.getExpectedType() + " but got " + context.getActualType().getName() + '.'
            );
        }
    }
}
