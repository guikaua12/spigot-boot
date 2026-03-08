package tech.guilhermekaua.spigotboot.commands.parse;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class CommandPattern {
    private final String source;
    private final List<CommandSegment> segments;
    private final Set<String> parameterNames;

    public CommandPattern(String source, List<CommandSegment> segments, Set<String> parameterNames) {
        this.source = source == null ? "" : source.trim();
        this.segments = Collections.unmodifiableList(segments);
        this.parameterNames = Collections.unmodifiableSet(parameterNames);
    }

    public String getSource() {
        return source;
    }

    public List<CommandSegment> getSegments() {
        return segments;
    }

    public Set<String> getParameterNames() {
        return parameterNames;
    }

    public int getLiteralCount() {
        int count = 0;
        for (CommandSegment segment : segments) {
            if (segment instanceof LiteralSegment) {
                count++;
            }
        }
        return count;
    }

    public int getMinimumTokenCount() {
        int count = 0;
        for (CommandSegment segment : segments) {
            if (!segment.isOptional()) {
                count++;
            }
        }
        return count;
    }

    public String normalizedSignature() {
        StringBuilder builder = new StringBuilder();
        for (CommandSegment segment : segments) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(segment.signatureToken());
        }
        return builder.toString();
    }

    public interface CommandSegment {
        boolean isOptional();

        boolean isArgument();

        String signatureToken();
    }

    public static final class LiteralSegment implements CommandSegment {
        private final String literal;

        public LiteralSegment(String literal) {
            this.literal = literal;
        }

        public String getLiteral() {
            return literal;
        }

        @Override
        public boolean isOptional() {
            return false;
        }

        @Override
        public boolean isArgument() {
            return false;
        }

        @Override
        public String signatureToken() {
            return literal.toLowerCase();
        }
    }

    public abstract static class ArgumentSegment implements CommandSegment {
        private final String name;

        protected ArgumentSegment(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean isArgument() {
            return true;
        }
    }

    public static final class RequiredArgumentSegment extends ArgumentSegment {
        public RequiredArgumentSegment(String name) {
            super(name);
        }

        @Override
        public boolean isOptional() {
            return false;
        }

        @Override
        public String signatureToken() {
            return "<arg>";
        }
    }

    public static final class OptionalArgumentSegment extends ArgumentSegment {
        public OptionalArgumentSegment(String name) {
            super(name);
        }

        @Override
        public boolean isOptional() {
            return true;
        }

        @Override
        public String signatureToken() {
            return "[arg]";
        }
    }
}
