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
package tech.guilhermekaua.spigotboot.testPluginBungee.task;

import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.Context;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;
import tech.guilhermekaua.spigotboot.core.context.lifecycle.listeners.ContextReadyListener;
import tech.guilhermekaua.spigotboot.testPluginBungee.config.NetworkConfig;
import tech.guilhermekaua.spigotboot.testPluginBungee.service.BroadcastService;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules periodic announcements on the native {@link TaskScheduler} once the context is ready,
 * cycling through the messages configured in {@link NetworkConfig.AnnouncementsConfig}. Demonstrates
 * injecting the proxy scheduler bean and the {@link ContextReadyListener} extension point.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementScheduler implements ContextReadyListener {
    private final TaskScheduler scheduler;
    private final Plugin plugin;
    private final NetworkConfig config;
    private final BroadcastService broadcast;

    @Override
    public void onContextReady(@NotNull Context context) {
        NetworkConfig.AnnouncementsConfig announcements = config.getAnnouncements();
        if (!announcements.isEnabled() || announcements.getMessages().isEmpty()) {
            return;
        }

        long seconds = Math.max(1, announcements.getInterval().getSeconds());
        List<String> messages = announcements.getMessages();
        AtomicInteger cursor = new AtomicInteger();

        scheduler.schedule(plugin, () -> {
            String message = messages.get(cursor.getAndIncrement() % messages.size());
            broadcast.broadcast(message);
        }, seconds, seconds, TimeUnit.SECONDS);
    }
}
