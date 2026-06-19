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
package tech.guilhermekaua.spigotboot.placeholder.registry.fixture;

import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.placeholder.annotations.Placeholder;
import tech.guilhermekaua.spigotboot.placeholder.annotations.RegisterPlaceholder;

/**
 * Fixture placeholder handler used to reproduce the component-scan/registry double-registration.
 * <p>
 * {@link RegisterPlaceholder} is meta-annotated {@code @Component}, so the core component scan registers
 * this class as a bean on its own; the placeholder registry must not register it a second time.
 */
@RegisterPlaceholder
public class FixturePlaceholderHandler {

    @Placeholder("fixture_value")
    public String fixtureValue(Player player, String params) {
        return "fixture";
    }
}
