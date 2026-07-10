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
package tech.guilhermekaua.spigotboot.core.spigot.conversation.support;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformScheduler;
import tech.guilhermekaua.spigotboot.core.spigot.scheduler.PlatformTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic {@link PlatformScheduler} for tests: no-delay tasks run inline; delayed tasks
 * are captured so a test can fire them on demand. Counts inline dispatches so tests can prove
 * the async path bypasses the scheduler.
 */
public final class FakeScheduler implements PlatformScheduler {

    public int runOnEntityCount = 0;
    private final List<FakeTask> pendingLater = new ArrayList<>();

    /** Fires every captured, not-yet-cancelled delayed task, then clears the queue. */
    public void fireAllLater() {
        List<FakeTask> snapshot = new ArrayList<>(pendingLater);
        pendingLater.clear();
        for (FakeTask task : snapshot) {
            if (!task.isCancelled()) {
                task.run();
            }
        }
    }

    public int pendingLaterCount() {
        int n = 0;
        for (FakeTask t : pendingLater) {
            if (!t.isCancelled()) {
                n++;
            }
        }
        return n;
    }

    private FakeTask later(Runnable task) {
        FakeTask handle = new FakeTask(task);
        pendingLater.add(handle);
        return handle;
    }

    @Override
    public @NotNull PlatformTask runOnEntity(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired) {
        runOnEntityCount++;
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runOnEntityLater(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runOnEntityAtFixedRate(@NotNull Entity entity, @NotNull Runnable task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runAtRegion(@NotNull Location location, @NotNull Runnable task) {
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runAtRegionLater(@NotNull Location location, @NotNull Runnable task, long delayTicks) {
        return later(task);
    }

    @Override
    public @NotNull PlatformTask runGlobal(@NotNull Runnable task) {
        task.run();
        return new FakeTask(null);
    }

    @Override
    public @NotNull PlatformTask runGlobalLater(@NotNull Runnable task, long delayTicks) {
        return later(task);
    }

    @Override
    public boolean ownsRegion(@NotNull Entity entity) {
        return true;
    }

    @Override
    public boolean ownsRegion(@NotNull Location location) {
        return true;
    }

    /** A cancellable captured task. */
    public static final class FakeTask implements PlatformTask {
        private final Runnable task;
        private boolean cancelled = false;

        FakeTask(Runnable task) {
            this.task = task;
        }

        void run() {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
