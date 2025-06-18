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
package me.approximations.spigotboot.testPlugin.placeholders;

import lombok.RequiredArgsConstructor;
import me.approximations.spigotboot.placeholder.annotations.Param;
import me.approximations.spigotboot.placeholder.annotations.Placeholder;
import me.approximations.spigotboot.placeholder.annotations.RegisterPlaceholder;
import me.approximations.spigotboot.testPlugin.People;
import me.approximations.spigotboot.testPlugin.services.UserService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@RegisterPlaceholder
@RequiredArgsConstructor
public class Placeholders {
    private final UserService userService;

    @Placeholder(
            value = "user_name",
            description = "Returns the nasme of the user associated with the player."
    )
    public String userNamePlaceholder(Player player) {
        final Optional<People> peopleOptional = userService.getPeople(player.getUniqueId()).join();
        return peopleOptional.map(People::getName).orElse("People not found!");
    }

    @Placeholder(
            value = "format_number_<number>",
            description = "Formats a number to two decimal places."
    )
    public String userNamePlaceholder(Player player, @Param("number") @NotNull Double number) {
        return String.format("%.2f", number);
    }
}
