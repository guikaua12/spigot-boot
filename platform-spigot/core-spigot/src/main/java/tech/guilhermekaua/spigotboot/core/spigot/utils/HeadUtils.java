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
package tech.guilhermekaua.spigotboot.core.spigot.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HeadUtils {

    /**
     * Lazily-resolved handles for the modern Bukkit profile API (introduced in 1.18.1 and stable
     * through the latest release). {@code null} until first resolved; the inner {@link Optional} is
     * empty on servers that predate the API (1.8-1.17.x), which then use the legacy field path.
     */
    private static volatile Optional<ProfileApi> profileApi;

    /**
     * Per-meta-class cache of the legacy CraftBukkit {@code CraftMetaSkull#profile} field, so the
     * reflective field lookup runs once per skull-meta implementation rather than per head.
     */
    private static final ConcurrentMap<Class<?>, Optional<Field>> PROFILE_FIELD_CACHE = new ConcurrentHashMap<>();

    private HeadUtils() {
    }

    public static ItemStack getHeadByName(String name) {
        final ItemStack head = createPlayerHead();
        if (name == null || name.isEmpty()) {
            return head;
        }
        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwner(name);

        head.setItemMeta(headMeta);
        return head;
    }

    public static ItemStack getHeadByUuid(UUID uuid) {
        final ItemStack head = createPlayerHead();
        if (uuid == null) {
            return head;
        }
        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        final OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (!trySetOwningPlayer(headMeta, player)) {
            // 1.8.8: setOwningPlayer (1.12.1+) is absent; fall back to the name-based owner.
            // the name can be null for an uncached uuid-only player on 1.8.8 - skip in that
            // case (there is no uuid-based skull api there, so the head stays owner-less)
            final String name = player.getName();
            if (name != null) {
                headMeta.setOwner(name);
            }
        }

        head.setItemMeta(headMeta);
        return head;
    }

    /**
     * Builds a player head skinned from a Mojang texture URL (e.g.
     * {@code http://textures.minecraft.net/texture/<hash>}), replacing the removed
     * {@code NBTEditor.getHead(url)} dependency.
     *
     * <p>The texture is applied version-proof from 1.8.8 to the latest release via two tiers, both
     * invoked reflectively so this stays compilable under the 1.8.8 signature check and safe at
     * runtime on servers where a given API is absent:
     * <ol>
     *     <li><b>1.18.1+</b>: the public Bukkit {@code PlayerProfile} API
     *         ({@code Bukkit.createPlayerProfile} → {@code PlayerTextures.setSkin(URL)} →
     *         {@code SkullMeta.setOwnerProfile}). This survives the 1.20.5 internal
     *         {@code GameProfile → ResolvableProfile} refactor because it never touches internals.</li>
     *     <li><b>1.8-1.17.x</b>: reflection into the skull meta's private {@code profile} field with a
     *         {@code com.mojang.authlib.GameProfile} carrying a base64 textures property.</li>
     * </ol>
     *
     * <p>A null, blank, or malformed URL yields a bare (owner-less) player head rather than throwing,
     * so a GUI never fails to render.
     *
     * @param url the Mojang texture URL, or null/blank for a bare head
     * @return a player-head {@link ItemStack}
     */
    public static ItemStack getHeadByUrl(String url) {
        final ItemStack head = createPlayerHead();
        if (url == null || url.trim().isEmpty()) {
            return head;
        }
        final SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta == null) {
            return head;
        }
        applySkinUrl(headMeta, url.trim());
        head.setItemMeta(headMeta);
        return head;
    }

    /**
     * Creates a bare player-head {@link ItemStack}, resolving the material by name so it works both
     * pre-flattening ({@code SKULL_ITEM} with data {@code 3}) and post-flattening
     * ({@code PLAYER_HEAD}, where the legacy data value is ignored).
     */
    private static ItemStack createPlayerHead() {
        Material material = Material.getMaterial("SKULL_ITEM");
        if (material == null) {
            // Most likely 1.13 materials
            material = Material.getMaterial("PLAYER_HEAD");
        }
        return new ItemStack(material, 1, (short) 3);
    }

    /**
     * Applies a skin URL to the given skull meta, preferring the modern profile API and falling back
     * to the legacy {@code GameProfile} field. Never throws.
     *
     * @return {@code true} if either tier applied the texture
     */
    static boolean applySkinUrl(SkullMeta meta, String url) {
        if (applyViaProfileApi(meta, url)) {
            return true;
        }
        return applyViaGameProfileField(meta, url);
    }

    /**
     * Tier 1 (1.18.1+): applies the skin through the public Bukkit {@code PlayerProfile} API. Returns
     * {@code false} when the API is absent or any reflective step fails, so the caller can fall back.
     */
    static boolean applyViaProfileApi(SkullMeta meta, String url) {
        final ProfileApi api = profileApi().orElse(null);
        if (api == null) {
            return false;
        }
        try {
            final URL skinUrl = new URL(url);
            final Object profile = api.createPlayerProfile.invoke(null, UUID.randomUUID());
            if (profile == null) {
                return false;
            }
            final Object textures = api.getTextures.invoke(profile);
            if (textures == null) {
                return false;
            }
            api.setSkin.invoke(textures, skinUrl);
            api.setOwnerProfile.invoke(meta, profile);
            return true;
        } catch (MalformedURLException | ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Tier 2 (1.8-1.17.x): applies the skin by writing a {@code GameProfile} with a base64 textures
     * property into the skull meta's private {@code profile} field.
     */
    static boolean applyViaGameProfileField(SkullMeta meta, String url) {
        final Object gameProfile = createGameProfile(url);
        if (gameProfile == null) {
            return false;
        }
        return writeProfileField(meta, gameProfile);
    }

    /**
     * Builds a {@code com.mojang.authlib.GameProfile} (reflectively, to avoid a compile-time authlib
     * reference) with a random UUID and a {@code textures} property carrying the base64-encoded skin.
     *
     * @return the profile, or {@code null} if authlib is unavailable or construction fails
     */
    static Object createGameProfile(String url) {
        try {
            final Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            final Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");

            final Object gameProfile = gameProfileClass
                    .getConstructor(UUID.class, String.class)
                    .newInstance(UUID.randomUUID(), "");
            final Object property = propertyClass
                    .getConstructor(String.class, String.class)
                    .newInstance("textures", encodeSkinTexture(url));

            final Object properties = gameProfileClass.getMethod("getProperties").invoke(gameProfile);
            properties.getClass()
                    .getMethod("put", Object.class, Object.class)
                    .invoke(properties, "textures", property);
            return gameProfile;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Writes {@code gameProfile} into the {@code profile} field declared by {@code meta}'s class (or a
     * superclass), caching the resolved field per meta class. Returns {@code false} when the field is
     * absent (non-CraftBukkit meta) or the value is not assignable (e.g. a 1.20.5+ server whose field
     * type changed, though that path is never reached because Tier 1 handles those versions).
     */
    static boolean writeProfileField(Object meta, Object gameProfile) {
        final Field field = profileField(meta.getClass());
        if (field == null) {
            return false;
        }
        try {
            field.set(meta, gameProfile);
            return true;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return false;
        }
    }

    /**
     * Base64-encodes the minimal Mojang skin-texture JSON for the given URL. The URL is escaped for
     * the two JSON metacharacters it could theoretically contain, so the payload is always valid JSON.
     */
    static String encodeSkinTexture(String url) {
        final String escaped = url.replace("\\", "\\\\").replace("\"", "\\\"");
        final String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + escaped + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static Field profileField(Class<?> metaClass) {
        return PROFILE_FIELD_CACHE.computeIfAbsent(metaClass, HeadUtils::findProfileField).orElse(null);
    }

    private static Optional<Field> findProfileField(Class<?> metaClass) {
        Class<?> current = metaClass;
        while (current != null && current != Object.class) {
            try {
                final Field field = current.getDeclaredField("profile");
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<ProfileApi> profileApi() {
        Optional<ProfileApi> resolved = profileApi;
        if (resolved == null) {
            synchronized (HeadUtils.class) {
                resolved = profileApi;
                if (resolved == null) {
                    resolved = resolveProfileApi();
                    profileApi = resolved;
                }
            }
        }
        return resolved;
    }

    static Optional<ProfileApi> resolveProfileApi() {
        try {
            final Class<?> profileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            final Class<?> texturesClass = Class.forName("org.bukkit.profile.PlayerTextures");
            final Method createPlayerProfile = Bukkit.class.getMethod("createPlayerProfile", UUID.class);
            final Method getTextures = profileClass.getMethod("getTextures");
            final Method setSkin = texturesClass.getMethod("setSkin", URL.class);
            final Method setOwnerProfile = SkullMeta.class.getMethod("setOwnerProfile", profileClass);
            return Optional.of(new ProfileApi(createPlayerProfile, getTextures, setSkin, setOwnerProfile));
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Sets the skull owner via {@code SkullMeta#setOwningPlayer(OfflinePlayer)} (1.12.1+),
     * invoked reflectively on purpose: keeping a direct bytecode reference out of this module
     * lets the animal-sniffer 1.8.8 check keep covering the rest of the {@link SkullMeta} API
     * surface (a class-level {@code <ignore>} would blind all of it). Returns {@code false} on
     * 1.8.8, where the method is absent, so the caller can fall back to {@code setOwner}.
     *
     * @param meta   the skull meta to mutate
     * @param player the owning player
     * @return {@code true} if the owning player was set, {@code false} if the API is unavailable
     */
    private static boolean trySetOwningPlayer(SkullMeta meta, OfflinePlayer player) {
        try {
            SkullMeta.class.getMethod("setOwningPlayer", OfflinePlayer.class).invoke(meta, player);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Cached reflective handles for the modern Bukkit profile API. */
    static final class ProfileApi {
        final Method createPlayerProfile;
        final Method getTextures;
        final Method setSkin;
        final Method setOwnerProfile;

        private ProfileApi(Method createPlayerProfile, Method getTextures, Method setSkin, Method setOwnerProfile) {
            this.createPlayerProfile = createPlayerProfile;
            this.getTextures = getTextures;
            this.setSkin = setSkin;
            this.setOwnerProfile = setOwnerProfile;
        }
    }
}
