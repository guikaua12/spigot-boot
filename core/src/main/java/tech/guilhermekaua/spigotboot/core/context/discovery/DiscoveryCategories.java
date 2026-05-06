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
package tech.guilhermekaua.spigotboot.core.context.discovery;

/**
 * Category keys used by {@link DiscoveryIndex} implementations. Each key maps to a set of classes
 * that the runtime would otherwise discover by classpath scanning.
 */
public final class DiscoveryCategories {
    public static final String COMPONENT = "component";
    public static final String CONFIGURATION = "configuration";
    public static final String METHOD_HANDLER = "method-handler";
    public static final String CONFIG = "config";
    public static final String PLACEHOLDER = "placeholder";
    public static final String CONVERTER = "converter";
    public static final String MODULE = "module";
    public static final String JDBC_REPOSITORY = "jdbc-repository";
    public static final String ORM_LITE_REPOSITORY = "orm-lite-repository";
    public static final String PERSISTENCE_CONFIG = "persistence-config";

    private DiscoveryCategories() {
    }
}
