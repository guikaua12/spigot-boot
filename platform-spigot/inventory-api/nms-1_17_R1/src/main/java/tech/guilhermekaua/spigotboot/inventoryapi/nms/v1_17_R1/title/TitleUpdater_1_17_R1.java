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
package tech.guilhermekaua.spigotboot.inventoryapi.nms.v1_17_R1.title;

import net.minecraft.network.chat.ChatMessage;
import net.minecraft.network.protocol.game.PacketPlayOutOpenWindow;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.PlayerConnection;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import tech.guilhermekaua.spigotboot.inventoryapi.nms.title.InventoryTitleUpdater;

/**
 * Spigot 1.17.1 title updater. Adapted from the upstream
 * {@code com.henryfabio.minecraft.inventoryapi.nms.v1_17_R1.title.TitleUpdater_1_17_R1}.
 */
public class TitleUpdater_1_17_R1 implements InventoryTitleUpdater {
    @Override
    public void update(Player player, String title) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();

        PacketPlayOutOpenWindow packet = new PacketPlayOutOpenWindow(
                entityPlayer.bV.j,
                entityPlayer.bV.getType(),
                new ChatMessage(title)
        );

        PlayerConnection playerConnection = entityPlayer.b;
        playerConnection.sendPacket(packet);
        player.updateInventory();
    }
}
