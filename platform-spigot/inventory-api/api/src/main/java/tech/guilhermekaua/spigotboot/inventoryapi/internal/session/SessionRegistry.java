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
package tech.guilhermekaua.spigotboot.inventoryapi.internal.session;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.core.context.annotations.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the live view session of every player, keyed by player UUID (one open view per
 * player); the v3 counterpart of the 2.x {@code ViewerRegistry} as a DI-managed bean.
 */
@Component
@ApiStatus.Internal
public final class SessionRegistry {

    private final Map<UUID, ViewSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a session under its player's UUID, replacing any previous mapping.
     *
     * @param session the session to register
     */
    public void register(@NotNull ViewSession session) {
        Objects.requireNonNull(session, "session");
        sessions.put(session.player().getUniqueId(), session);
    }

    /**
     * Removes a session's mapping.
     *
     * @param session the session to unregister
     */
    public void unregister(@NotNull ViewSession session) {
        Objects.requireNonNull(session, "session");
        // identity-guarded so unregistering an already replaced session never drops its successor
        sessions.remove(session.player().getUniqueId(), session);
    }

    /**
     * Looks up the session of a player.
     *
     * @param playerId the player's UUID
     * @return the player's session, or empty when none is registered
     */
    public @NotNull Optional<ViewSession> find(@NotNull UUID playerId) {
        return Optional.ofNullable(sessions.get(Objects.requireNonNull(playerId, "playerId")));
    }

    /**
     * Returns every registered session.
     *
     * @return an unmodifiable live view of all sessions
     */
    public @NotNull Collection<ViewSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}
