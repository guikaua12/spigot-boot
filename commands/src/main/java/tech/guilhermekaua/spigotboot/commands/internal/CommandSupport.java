package tech.guilhermekaua.spigotboot.commands.internal;

import tech.guilhermekaua.spigotboot.core.context.annotations.Order;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.Ordered;

import java.util.*;

public final class CommandSupport {
    private CommandSupport() {
    }

    public static String normalizeLabel(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static int resolveOrder(Object bean) {
        if (bean == null) {
            return 0;
        }
        if (bean instanceof Ordered) {
            return ((Ordered) bean).getOrder();
        }

        Order order = bean.getClass().getAnnotation(Order.class);
        return order == null ? 0 : order.value();
    }

    public static <T> List<T> deduplicateByIdentity(Collection<T> values) {
        List<T> result = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return result;
        }

        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (T value : values) {
            if (seen.add(value)) {
                result.add(value);
            }
        }
        return result;
    }

    public static <T> List<T> sortBeans(Collection<T> beans) {
        List<T> result = new ArrayList<>(deduplicateByIdentity(beans));
        result.sort((left, right) -> {
            int leftOrder = resolveOrder(left);
            int rightOrder = resolveOrder(right);
            if (leftOrder != rightOrder) {
                return Integer.compare(leftOrder, rightOrder);
            }
            return left.getClass().getName().compareTo(right.getClass().getName());
        });
        return result;
    }

    public static String joinArgs(String[] args, int startInclusive) {
        if (args == null || startInclusive >= args.length) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = startInclusive; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
