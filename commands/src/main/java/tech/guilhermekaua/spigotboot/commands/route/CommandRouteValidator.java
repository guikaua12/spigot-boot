package tech.guilhermekaua.spigotboot.commands.route;

import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;
import tech.guilhermekaua.spigotboot.commands.parse.CommandPattern;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRouteValidator {
    public void validate(List<CompiledRootCommand> roots) {
        validateRootCollisions(roots);
        for (CompiledRootCommand root : roots) {
            validateRoot(root);
        }
    }

    private void validateRootCollisions(List<CompiledRootCommand> roots) {
        Map<String, CompiledRootCommand> labels = new HashMap<>();
        for (CompiledRootCommand root : roots) {
            for (String label : root.getAliases().allValues()) {
                String normalized = CommandSupport.normalizeLabel(label);
                CompiledRootCommand existing = labels.put(normalized, root);
                if (existing != null && existing != root) {
                    throw new IllegalStateException(
                            "Root command label collision between " + existing.getHandlerType().getName() + " and " +
                                    root.getHandlerType().getName() + " for '" + label + "'."
                    );
                }
            }
        }
    }

    private void validateRoot(CompiledRootCommand root) {
        if (root.getDefaultRoute() != null) {
            for (CompiledCommandRoute route : root.getRoutes()) {
                if (route.getMinimumTokenCount() == 0) {
                    throw new IllegalStateException("Default command conflicts with a zero-length route in " + root.getHandlerType().getName());
                }
            }
        }

        Map<String, CompiledCommandRoute> signatures = new HashMap<>();
        for (CompiledCommandRoute route : root.getRoutes()) {
            String signature = route.getPattern().normalizedSignature();
            CompiledCommandRoute existing = signatures.put(signature, route);
            if (existing != null) {
                throw new IllegalStateException("Duplicate command route '" + signature + "' in " + root.getHandlerType().getName());
            }
        }

        for (int left = 0; left < root.getRoutes().size(); left++) {
            for (int right = left + 1; right < root.getRoutes().size(); right++) {
                CompiledCommandRoute a = root.getRoutes().get(left);
                CompiledCommandRoute b = root.getRoutes().get(right);
                if (areAmbiguous(a, b)) {
                    throw new IllegalStateException(
                            "Ambiguous command routes in " + root.getHandlerType().getName() + ": '" +
                                    a.getPattern().getSource() + "' and '" + b.getPattern().getSource() + "'."
                    );
                }
            }
        }
    }

    private boolean areAmbiguous(CompiledCommandRoute left, CompiledCommandRoute right) {
        if (!lengthRangesOverlap(left, right)) {
            return false;
        }

        List<CommandPattern.CommandSegment> leftSegments = left.getPattern().getSegments();
        List<CommandPattern.CommandSegment> rightSegments = right.getPattern().getSegments();
        int max = Math.min(leftSegments.size(), rightSegments.size());
        boolean leftMoreSpecific = false;
        boolean rightMoreSpecific = false;

        for (int index = 0; index < max; index++) {
            CommandPattern.CommandSegment leftSegment = leftSegments.get(index);
            CommandPattern.CommandSegment rightSegment = rightSegments.get(index);

            if (leftSegment instanceof CommandPattern.LiteralSegment && rightSegment instanceof CommandPattern.LiteralSegment) {
                String leftLiteral = ((CommandPattern.LiteralSegment) leftSegment).getLiteral();
                String rightLiteral = ((CommandPattern.LiteralSegment) rightSegment).getLiteral();
                if (!leftLiteral.equals(rightLiteral)) {
                    return false;
                }
                continue;
            }

            if (leftSegment instanceof CommandPattern.LiteralSegment) {
                leftMoreSpecific = true;
            } else if (rightSegment instanceof CommandPattern.LiteralSegment) {
                rightMoreSpecific = true;
            }
        }

        if (leftMoreSpecific && rightMoreSpecific) {
            return true;
        }
        if (!leftMoreSpecific && !rightMoreSpecific) {
            return true;
        }
        return false;
    }

    private boolean lengthRangesOverlap(CompiledCommandRoute left, CompiledCommandRoute right) {
        int minimum = Math.max(left.getMinimumTokenCount(), right.getMinimumTokenCount());
        int maximum = Math.min(left.getMaximumTokenCount(), right.getMaximumTokenCount());
        return maximum >= minimum;
    }
}
