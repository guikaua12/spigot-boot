/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.core.pagination;

import java.util.*;

public final class Sort {
    private static final Sort UNSORTED = new Sort(Collections.<Order>emptyList());

    private final List<Order> orders;

    private Sort(List<Order> orders) {
        this.orders = Collections.unmodifiableList(new ArrayList<>(orders));
    }

    public static Sort by(Order... orders) {
        return by(Arrays.asList(orders));
    }

    public static Sort by(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return unsorted();
        }

        for (Order order : orders) {
            Objects.requireNonNull(order, "order must not be null");
        }

        return new Sort(orders);
    }

    public static Sort asc(String... properties) {
        return by(Direction.ASC, properties);
    }

    public static Sort desc(String... properties) {
        return by(Direction.DESC, properties);
    }

    public static Sort unsorted() {
        return UNSORTED;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public boolean isSorted() {
        return !orders.isEmpty();
    }

    private static Sort by(Direction direction, String... properties) {
        if (properties == null || properties.length == 0) {
            return unsorted();
        }

        List<Order> entries = new ArrayList<>(properties.length);
        for (String property : properties) {
            entries.add(new Order(direction, property));
        }

        return new Sort(entries);
    }

    public enum Direction {
        ASC,
        DESC
    }

    public static final class Order {
        private final Direction direction;
        private final String property;

        public Order(Direction direction, String property) {
            this.direction = Objects.requireNonNull(direction, "direction must not be null");
            this.property = requireProperty(property);
        }

        public static Order asc(String property) {
            return new Order(Direction.ASC, property);
        }

        public static Order desc(String property) {
            return new Order(Direction.DESC, property);
        }

        public Direction getDirection() {
            return direction;
        }

        public String getProperty() {
            return property;
        }

        private static String requireProperty(String property) {
            if (property == null || property.trim().isEmpty()) {
                throw new IllegalArgumentException("property must not be blank");
            }
            return property;
        }
    }
}
