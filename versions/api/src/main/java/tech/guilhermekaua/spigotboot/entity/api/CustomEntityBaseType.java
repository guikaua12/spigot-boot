/*
 * The MIT License
 * Copyright (c) 2025 Guilherme Kaua da Silva
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
package tech.guilhermekaua.spigotboot.entity.api;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the stable logical vanilla entity type that a custom entity is built on top of.
 *
 * <p>The enum is the union of Bukkit entity kinds across the supported server versions.
 *
 * @since 2.0.2
 */
public enum CustomEntityBaseType {
    ITEM("item", "ITEM", "org.bukkit.entity.Item", "DROPPED_ITEM"),
    EXPERIENCE_ORB("experience_orb", "EXPERIENCE_ORB", "org.bukkit.entity.ExperienceOrb"),
    AREA_EFFECT_CLOUD("area_effect_cloud", "AREA_EFFECT_CLOUD", "org.bukkit.entity.AreaEffectCloud"),
    ELDER_GUARDIAN("elder_guardian", "ELDER_GUARDIAN", "org.bukkit.entity.ElderGuardian"),
    WITHER_SKELETON("wither_skeleton", "WITHER_SKELETON", "org.bukkit.entity.WitherSkeleton"),
    STRAY("stray", "STRAY", "org.bukkit.entity.Stray"),
    EGG("egg", "EGG", "org.bukkit.entity.Egg"),
    LEASH_KNOT("leash_knot", "LEASH_KNOT", "org.bukkit.entity.LeashHitch", "LEASH_HITCH"),
    PAINTING("painting", "PAINTING", "org.bukkit.entity.Painting"),
    ARROW("arrow", "ARROW", "org.bukkit.entity.Arrow"),
    SNOWBALL("snowball", "SNOWBALL", "org.bukkit.entity.Snowball"),
    FIREBALL("fireball", "FIREBALL", "org.bukkit.entity.LargeFireball"),
    SMALL_FIREBALL("small_fireball", "SMALL_FIREBALL", "org.bukkit.entity.SmallFireball"),
    ENDER_PEARL("ender_pearl", "ENDER_PEARL", "org.bukkit.entity.EnderPearl"),
    EYE_OF_ENDER("eye_of_ender", "EYE_OF_ENDER", "org.bukkit.entity.EnderSignal", "ENDER_SIGNAL"),
    POTION("potion", "POTION", "org.bukkit.entity.ThrownPotion", "SPLASH_POTION"),
    EXPERIENCE_BOTTLE(
            "experience_bottle",
            "EXPERIENCE_BOTTLE",
            "org.bukkit.entity.ThrownExpBottle",
            "THROWN_EXP_BOTTLE"
    ),
    ITEM_FRAME("item_frame", "ITEM_FRAME", "org.bukkit.entity.ItemFrame"),
    WITHER_SKULL("wither_skull", "WITHER_SKULL", "org.bukkit.entity.WitherSkull"),
    TNT("tnt", "TNT", "org.bukkit.entity.TNTPrimed", "PRIMED_TNT"),
    FALLING_BLOCK("falling_block", "FALLING_BLOCK", "org.bukkit.entity.FallingBlock"),
    FIREWORK_ROCKET("firework_rocket", "FIREWORK_ROCKET", "org.bukkit.entity.Firework", "FIREWORK"),
    HUSK("husk", "HUSK", "org.bukkit.entity.Husk"),
    SPECTRAL_ARROW("spectral_arrow", "SPECTRAL_ARROW", "org.bukkit.entity.SpectralArrow"),
    SHULKER_BULLET("shulker_bullet", "SHULKER_BULLET", "org.bukkit.entity.ShulkerBullet"),
    DRAGON_FIREBALL("dragon_fireball", "DRAGON_FIREBALL", "org.bukkit.entity.DragonFireball"),
    ZOMBIE_VILLAGER("zombie_villager", "ZOMBIE_VILLAGER", "org.bukkit.entity.ZombieVillager"),
    SKELETON_HORSE("skeleton_horse", "SKELETON_HORSE", "org.bukkit.entity.SkeletonHorse"),
    ZOMBIE_HORSE("zombie_horse", "ZOMBIE_HORSE", "org.bukkit.entity.ZombieHorse"),
    ARMOR_STAND("armor_stand", "ARMOR_STAND", "org.bukkit.entity.ArmorStand"),
    DONKEY("donkey", "DONKEY", "org.bukkit.entity.Donkey"),
    MULE("mule", "MULE", "org.bukkit.entity.Mule"),
    EVOKER_FANGS("evoker_fangs", "EVOKER_FANGS", "org.bukkit.entity.EvokerFangs"),
    EVOKER("evoker", "EVOKER", "org.bukkit.entity.Evoker"),
    VEX("vex", "VEX", "org.bukkit.entity.Vex"),
    VINDICATOR("vindicator", "VINDICATOR", "org.bukkit.entity.Vindicator"),
    ILLUSIONER("illusioner", "ILLUSIONER", "org.bukkit.entity.Illusioner"),
    COMMAND_BLOCK_MINECART(
            "command_block_minecart",
            "COMMAND_BLOCK_MINECART",
            "org.bukkit.entity.minecart.CommandMinecart",
            "MINECART_COMMAND"
    ),
    MINECART("minecart", "MINECART", "org.bukkit.entity.minecart.RideableMinecart"),
    CHEST_MINECART(
            "chest_minecart",
            "CHEST_MINECART",
            "org.bukkit.entity.minecart.StorageMinecart",
            "MINECART_CHEST"
    ),
    FURNACE_MINECART(
            "furnace_minecart",
            "FURNACE_MINECART",
            "org.bukkit.entity.minecart.PoweredMinecart",
            "MINECART_FURNACE"
    ),
    TNT_MINECART(
            "tnt_minecart",
            "TNT_MINECART",
            "org.bukkit.entity.minecart.ExplosiveMinecart",
            "MINECART_TNT"
    ),
    HOPPER_MINECART(
            "hopper_minecart",
            "HOPPER_MINECART",
            "org.bukkit.entity.minecart.HopperMinecart",
            "MINECART_HOPPER"
    ),
    SPAWNER_MINECART(
            "spawner_minecart",
            "SPAWNER_MINECART",
            "org.bukkit.entity.minecart.SpawnerMinecart",
            "MINECART_MOB_SPAWNER"
    ),
    CREEPER("creeper", "CREEPER", "org.bukkit.entity.Creeper"),
    SKELETON("skeleton", "SKELETON", "org.bukkit.entity.Skeleton"),
    SPIDER("spider", "SPIDER", "org.bukkit.entity.Spider"),
    GIANT("giant", "GIANT", "org.bukkit.entity.Giant"),
    ZOMBIE("zombie", "ZOMBIE", "org.bukkit.entity.Zombie"),
    SLIME("slime", "SLIME", "org.bukkit.entity.Slime"),
    GHAST("ghast", "GHAST", "org.bukkit.entity.Ghast"),
    ZOMBIFIED_PIGLIN("zombified_piglin", "ZOMBIFIED_PIGLIN", "org.bukkit.entity.PigZombie", "PIG_ZOMBIE"),
    ENDERMAN("enderman", "ENDERMAN", "org.bukkit.entity.Enderman"),
    CAVE_SPIDER("cave_spider", "CAVE_SPIDER", "org.bukkit.entity.CaveSpider"),
    SILVERFISH("silverfish", "SILVERFISH", "org.bukkit.entity.Silverfish"),
    BLAZE("blaze", "BLAZE", "org.bukkit.entity.Blaze"),
    MAGMA_CUBE("magma_cube", "MAGMA_CUBE", "org.bukkit.entity.MagmaCube"),
    ENDER_DRAGON("ender_dragon", "ENDER_DRAGON", "org.bukkit.entity.EnderDragon"),
    WITHER("wither", "WITHER", "org.bukkit.entity.Wither"),
    BAT("bat", "BAT", "org.bukkit.entity.Bat"),
    WITCH("witch", "WITCH", "org.bukkit.entity.Witch"),
    ENDERMITE("endermite", "ENDERMITE", "org.bukkit.entity.Endermite"),
    GUARDIAN("guardian", "GUARDIAN", "org.bukkit.entity.Guardian"),
    SHULKER("shulker", "SHULKER", "org.bukkit.entity.Shulker"),
    PIG("pig", "PIG", "org.bukkit.entity.Pig"),
    SHEEP("sheep", "SHEEP", "org.bukkit.entity.Sheep"),
    COW("cow", "COW", "org.bukkit.entity.Cow"),
    CHICKEN("chicken", "CHICKEN", "org.bukkit.entity.Chicken"),
    SQUID("squid", "SQUID", "org.bukkit.entity.Squid"),
    WOLF("wolf", "WOLF", "org.bukkit.entity.Wolf"),
    MOOSHROOM("mooshroom", "MOOSHROOM", "org.bukkit.entity.MushroomCow", "MUSHROOM_COW"),
    SNOW_GOLEM("snow_golem", "SNOW_GOLEM", "org.bukkit.entity.Snowman", "SNOWMAN"),
    OCELOT("ocelot", "OCELOT", "org.bukkit.entity.Ocelot"),
    IRON_GOLEM("iron_golem", "IRON_GOLEM", "org.bukkit.entity.IronGolem"),
    HORSE("horse", "HORSE", "org.bukkit.entity.Horse"),
    RABBIT("rabbit", "RABBIT", "org.bukkit.entity.Rabbit"),
    POLAR_BEAR("polar_bear", "POLAR_BEAR", "org.bukkit.entity.PolarBear"),
    LLAMA("llama", "LLAMA", "org.bukkit.entity.Llama"),
    LLAMA_SPIT("llama_spit", "LLAMA_SPIT", "org.bukkit.entity.LlamaSpit"),
    PARROT("parrot", "PARROT", "org.bukkit.entity.Parrot"),
    VILLAGER("villager", "VILLAGER", "org.bukkit.entity.Villager"),
    END_CRYSTAL("end_crystal", "END_CRYSTAL", "org.bukkit.entity.EnderCrystal", "ENDER_CRYSTAL"),
    TURTLE("turtle", "TURTLE", "org.bukkit.entity.Turtle"),
    PHANTOM("phantom", "PHANTOM", "org.bukkit.entity.Phantom"),
    TRIDENT("trident", "TRIDENT", "org.bukkit.entity.Trident"),
    COD("cod", "COD", "org.bukkit.entity.Cod"),
    SALMON("salmon", "SALMON", "org.bukkit.entity.Salmon"),
    PUFFERFISH("pufferfish", "PUFFERFISH", "org.bukkit.entity.PufferFish"),
    TROPICAL_FISH("tropical_fish", "TROPICAL_FISH", "org.bukkit.entity.TropicalFish"),
    DROWNED("drowned", "DROWNED", "org.bukkit.entity.Drowned"),
    DOLPHIN("dolphin", "DOLPHIN", "org.bukkit.entity.Dolphin"),
    CAT("cat", "CAT", "org.bukkit.entity.Cat"),
    PANDA("panda", "PANDA", "org.bukkit.entity.Panda"),
    PILLAGER("pillager", "PILLAGER", "org.bukkit.entity.Pillager"),
    RAVAGER("ravager", "RAVAGER", "org.bukkit.entity.Ravager"),
    TRADER_LLAMA("trader_llama", "TRADER_LLAMA", "org.bukkit.entity.TraderLlama"),
    WANDERING_TRADER("wandering_trader", "WANDERING_TRADER", "org.bukkit.entity.WanderingTrader"),
    FOX("fox", "FOX", "org.bukkit.entity.Fox"),
    BEE("bee", "BEE", "org.bukkit.entity.Bee"),
    HOGLIN("hoglin", "HOGLIN", "org.bukkit.entity.Hoglin"),
    PIGLIN("piglin", "PIGLIN", "org.bukkit.entity.Piglin"),
    STRIDER("strider", "STRIDER", "org.bukkit.entity.Strider"),
    ZOGLIN("zoglin", "ZOGLIN", "org.bukkit.entity.Zoglin"),
    PIGLIN_BRUTE("piglin_brute", "PIGLIN_BRUTE", "org.bukkit.entity.PiglinBrute"),
    AXOLOTL("axolotl", "AXOLOTL", "org.bukkit.entity.Axolotl"),
    GLOW_ITEM_FRAME("glow_item_frame", "GLOW_ITEM_FRAME", "org.bukkit.entity.GlowItemFrame"),
    GLOW_SQUID("glow_squid", "GLOW_SQUID", "org.bukkit.entity.GlowSquid"),
    GOAT("goat", "GOAT", "org.bukkit.entity.Goat"),
    MARKER("marker", "MARKER", "org.bukkit.entity.Marker"),
    ALLAY("allay", "ALLAY", "org.bukkit.entity.Allay"),
    FROG("frog", "FROG", "org.bukkit.entity.Frog"),
    TADPOLE("tadpole", "TADPOLE", "org.bukkit.entity.Tadpole"),
    WARDEN("warden", "WARDEN", "org.bukkit.entity.Warden"),
    CAMEL("camel", "CAMEL", "org.bukkit.entity.Camel"),
    BLOCK_DISPLAY("block_display", "BLOCK_DISPLAY", "org.bukkit.entity.BlockDisplay"),
    INTERACTION("interaction", "INTERACTION", "org.bukkit.entity.Interaction"),
    ITEM_DISPLAY("item_display", "ITEM_DISPLAY", "org.bukkit.entity.ItemDisplay"),
    SNIFFER("sniffer", "SNIFFER", "org.bukkit.entity.Sniffer"),
    TEXT_DISPLAY("text_display", "TEXT_DISPLAY", "org.bukkit.entity.TextDisplay"),
    BREEZE("breeze", "BREEZE", "org.bukkit.entity.Breeze"),
    WIND_CHARGE("wind_charge", "WIND_CHARGE", "org.bukkit.entity.WindCharge"),
    BREEZE_WIND_CHARGE(
            "breeze_wind_charge",
            "BREEZE_WIND_CHARGE",
            "org.bukkit.entity.BreezeWindCharge"
    ),
    ARMADILLO("armadillo", "ARMADILLO", "org.bukkit.entity.Armadillo"),
    BOGGED("bogged", "BOGGED", "org.bukkit.entity.Bogged"),
    OMINOUS_ITEM_SPAWNER(
            "ominous_item_spawner",
            "OMINOUS_ITEM_SPAWNER",
            "org.bukkit.entity.OminousItemSpawner"
    ),
    ACACIA_BOAT("acacia_boat", "ACACIA_BOAT", "org.bukkit.entity.boat.AcaciaBoat"),
    ACACIA_CHEST_BOAT(
            "acacia_chest_boat",
            "ACACIA_CHEST_BOAT",
            "org.bukkit.entity.boat.AcaciaChestBoat"
    ),
    BAMBOO_RAFT("bamboo_raft", "BAMBOO_RAFT", "org.bukkit.entity.boat.BambooRaft"),
    BAMBOO_CHEST_RAFT(
            "bamboo_chest_raft",
            "BAMBOO_CHEST_RAFT",
            "org.bukkit.entity.boat.BambooChestRaft"
    ),
    BIRCH_BOAT("birch_boat", "BIRCH_BOAT", "org.bukkit.entity.boat.BirchBoat"),
    BIRCH_CHEST_BOAT(
            "birch_chest_boat",
            "BIRCH_CHEST_BOAT",
            "org.bukkit.entity.boat.BirchChestBoat"
    ),
    CHERRY_BOAT("cherry_boat", "CHERRY_BOAT", "org.bukkit.entity.boat.CherryBoat"),
    CHERRY_CHEST_BOAT(
            "cherry_chest_boat",
            "CHERRY_CHEST_BOAT",
            "org.bukkit.entity.boat.CherryChestBoat"
    ),
    DARK_OAK_BOAT("dark_oak_boat", "DARK_OAK_BOAT", "org.bukkit.entity.boat.DarkOakBoat"),
    DARK_OAK_CHEST_BOAT(
            "dark_oak_chest_boat",
            "DARK_OAK_CHEST_BOAT",
            "org.bukkit.entity.boat.DarkOakChestBoat"
    ),
    JUNGLE_BOAT("jungle_boat", "JUNGLE_BOAT", "org.bukkit.entity.boat.JungleBoat"),
    JUNGLE_CHEST_BOAT(
            "jungle_chest_boat",
            "JUNGLE_CHEST_BOAT",
            "org.bukkit.entity.boat.JungleChestBoat"
    ),
    MANGROVE_BOAT("mangrove_boat", "MANGROVE_BOAT", "org.bukkit.entity.boat.MangroveBoat"),
    MANGROVE_CHEST_BOAT(
            "mangrove_chest_boat",
            "MANGROVE_CHEST_BOAT",
            "org.bukkit.entity.boat.MangroveChestBoat"
    ),
    OAK_BOAT("oak_boat", "OAK_BOAT", "org.bukkit.entity.boat.OakBoat"),
    OAK_CHEST_BOAT(
            "oak_chest_boat",
            "OAK_CHEST_BOAT",
            "org.bukkit.entity.boat.OakChestBoat"
    ),
    PALE_OAK_BOAT("pale_oak_boat", "PALE_OAK_BOAT", "org.bukkit.entity.boat.PaleOakBoat"),
    PALE_OAK_CHEST_BOAT(
            "pale_oak_chest_boat",
            "PALE_OAK_CHEST_BOAT",
            "org.bukkit.entity.boat.PaleOakChestBoat"
    ),
    SPRUCE_BOAT("spruce_boat", "SPRUCE_BOAT", "org.bukkit.entity.boat.SpruceBoat"),
    SPRUCE_CHEST_BOAT(
            "spruce_chest_boat",
            "SPRUCE_CHEST_BOAT",
            "org.bukkit.entity.boat.SpruceChestBoat"
    ),
    CREAKING("creaking", "CREAKING", "org.bukkit.entity.Creaking"),
    FISHING_BOBBER("fishing_bobber", "FISHING_BOBBER", "org.bukkit.entity.FishHook", "FISHING_HOOK"),
    LIGHTNING_BOLT("lightning_bolt", "LIGHTNING_BOLT", "org.bukkit.entity.LightningStrike", "LIGHTNING"),
    PLAYER("player", "PLAYER", "org.bukkit.entity.Player"),
    BOAT("boat", "BOAT", "org.bukkit.entity.Boat"),
    WEATHER("weather", "WEATHER", "org.bukkit.entity.Weather"),
    COMPLEX_PART("complex_part", "COMPLEX_PART", "org.bukkit.entity.ComplexEntityPart"),
    UNKNOWN("unknown", "UNKNOWN", null);

    private static final Map<String, CustomEntityBaseType> ENTITY_TYPE_NAMES = new LinkedHashMap<>();

    static {
        for (CustomEntityBaseType value : values()) {
            registerEntityTypeName(value.entityTypeName, value);
            for (String alias : value.entityTypeAliases) {
                registerEntityTypeName(alias, value);
            }
        }
    }

    private final String logicalId;
    private final String entityTypeName;
    private final String bukkitWrapperClassName;
    private final String[] entityTypeAliases;

    CustomEntityBaseType(
            @NotNull String logicalId,
            @NotNull String entityTypeName,
            @Nullable String bukkitWrapperClassName,
            @NotNull String... entityTypeAliases
    ) {
        this.logicalId = Objects.requireNonNull(logicalId, "logicalId cannot be null");
        this.entityTypeName = Objects.requireNonNull(entityTypeName, "entityTypeName cannot be null");
        this.bukkitWrapperClassName = bukkitWrapperClassName;
        this.entityTypeAliases = Objects.requireNonNull(entityTypeAliases, "entityTypeAliases cannot be null");
    }

    /**
     * Returns the stable cross-version logical id for this base type.
     *
     * @return the stable logical id
     */
    public @NotNull String logicalId() {
        return logicalId;
    }

    /**
     * Returns the primary Bukkit {@link EntityType#name()} used for this logical type.
     *
     * @return the primary Bukkit entity type name
     */
    public @NotNull String entityTypeName() {
        return entityTypeName;
    }

    /**
     * Returns the preferred Bukkit wrapper class name for this logical type.
     *
     * @return the preferred Bukkit wrapper class name, or {@code null}
     */
    public @Nullable String bukkitWrapperClassName() {
        return bukkitWrapperClassName;
    }

    /**
     * Resolves the active Bukkit {@link EntityType}, or {@code null} when the type does not exist
     * on the active server version.
     *
     * @return the active Bukkit entity type, or {@code null}
     */
    public @Nullable EntityType entityTypeOrNull() {
        EntityType resolved = resolveEntityType(entityTypeName);
        if (resolved != null) {
            return resolved;
        }
        for (String alias : entityTypeAliases) {
            resolved = resolveEntityType(alias);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * Resolves the active Bukkit wrapper class, or {@code null} when the type does not exist on
     * the active server version.
     *
     * @return the active Bukkit wrapper class, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable Class<? extends Entity> bukkitTypeOrNull() {
        EntityType entityType = entityTypeOrNull();
        if (entityType != null && entityType.getEntityClass() != null) {
            return entityType.getEntityClass();
        }
        if (bukkitWrapperClassName == null) {
            return null;
        }
        for (ClassLoader classLoader : candidateClassLoaders()) {
            try {
                Class<?> resolvedClass = Class.forName(bukkitWrapperClassName, false, classLoader);
                if (Entity.class.isAssignableFrom(resolvedClass)) {
                    return (Class<? extends Entity>) resolvedClass;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    /**
     * Resolves the logical base type for the supplied Bukkit {@link EntityType}.
     *
     * @param entityType the Bukkit entity type
     * @return the resolved logical base type, or {@code null}
     */
    public static @Nullable CustomEntityBaseType fromEntityType(@NotNull EntityType entityType) {
        Objects.requireNonNull(entityType, "entityType cannot be null");
        return fromEntityTypeName(entityType.name());
    }

    /**
     * Resolves the logical base type for the supplied Bukkit entity type name.
     *
     * @param entityTypeName the Bukkit entity type name
     * @return the resolved logical base type, or {@code null}
     */
    public static @Nullable CustomEntityBaseType fromEntityTypeName(@NotNull String entityTypeName) {
        Objects.requireNonNull(entityTypeName, "entityTypeName cannot be null");
        return ENTITY_TYPE_NAMES.get(entityTypeName.toUpperCase(Locale.ROOT));
    }

    private static void registerEntityTypeName(@NotNull String entityTypeName, @NotNull CustomEntityBaseType baseType) {
        ENTITY_TYPE_NAMES.put(entityTypeName.toUpperCase(Locale.ROOT), baseType);
    }

    private static @Nullable EntityType resolveEntityType(@NotNull String entityTypeName) {
        try {
            return EntityType.valueOf(entityTypeName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @NotNull ClassLoader[] candidateClassLoaders() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader enumClassLoader = CustomEntityBaseType.class.getClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (contextClassLoader == null) {
            return new ClassLoader[]{enumClassLoader, systemClassLoader};
        }
        if (contextClassLoader == enumClassLoader) {
            return new ClassLoader[]{contextClassLoader, systemClassLoader};
        }
        return new ClassLoader[]{contextClassLoader, enumClassLoader, systemClassLoader};
    }
}
