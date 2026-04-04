/*
 * The MIT License
 * Copyright Â© 2025 Guilherme KauÃ£ da Silva
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
package tech.guilhermekaua.spigotboot.testPlugin.services;

import com.destroystokyo.paper.entity.ai.GoalKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tech.guilhermekaua.spigotboot.core.context.annotations.Service;
import tech.guilhermekaua.spigotboot.entity.api.CustomEntity;
import tech.guilhermekaua.spigotboot.entity.api.goal.EntityGoalKeys;
import tech.guilhermekaua.spigotboot.entity.api.goal.SimpleGoalDefinition;
import tech.guilhermekaua.spigotboot.entity.api.type.EntityTypeKeys;
import tech.guilhermekaua.spigotboot.entity.api.zombie.CustomZombie;
import tech.guilhermekaua.spigotboot.entity.runtime.VersionedEntityPlatform;
import tech.guilhermekaua.spigotboot.entity.runtime.bootstrap.SpigotEntityBootstrap;
import tech.guilhermekaua.spigotboot.testPlugin.entity.goal.OrbitingZombieGoal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class VersionedZombieService {
    private final JavaPlugin plugin;
    private final Map<UUID, SpawnedZombie> activeZombies = new HashMap<UUID, SpawnedZombie>();

    private VersionedEntityPlatform entityPlatform;

    public VersionedZombieService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
    }

    public Zombie spawnDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        clearDemoZombie(owner);

        VersionedEntityPlatform platform = resolvePlatform();
        Location spawnLocation = resolveSpawnLocation(owner);

        CustomEntity entity = platform.createEntity(EntityTypeKeys.ZOMBIE, spawnLocation);
        if (!(entity instanceof CustomZombie)) {
            throw new IllegalStateException("The versioned platform did not create a zombie wrapper.");
        }

        CustomZombie customZombie = (CustomZombie) entity;
        Zombie zombie = (Zombie) customZombie.unwrap();
        configureZombie(owner, customZombie, zombie);

        OrbitingZombieGoal orbitingGoal = new OrbitingZombieGoal(plugin, zombie, owner.getUniqueId());
        Bukkit.getMobGoals().addGoal(zombie, 1, orbitingGoal);
        activeZombies.put(owner.getUniqueId(), new SpawnedZombie(customZombie, zombie, orbitingGoal.getKey()));
        return zombie;
    }

    public boolean clearDemoZombie(Player owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        return clearDemoZombie(owner.getUniqueId());
    }

    public void clearAllDemoZombies() {
        for (UUID ownerId : new ArrayList<UUID>(activeZombies.keySet())) {
            clearDemoZombie(ownerId);
        }
    }

    private void configureZombie(Player owner, CustomZombie customZombie, Zombie zombie) {
        Bukkit.getMobGoals().removeAllGoals(zombie);
        customZombie.addGoal(SimpleGoalDefinition.of(
                EntityGoalKeys.FLOAT,
                0,
                Collections.<String, Object>emptyMap()
        ));

        customZombie.setBaby(false);
        customZombie.setName("Orbit Zombie");
        customZombie.setNameVisible(true);
        customZombie.setMaxHealth(40.0D);
        customZombie.setHealth(customZombie.getMaxHealth());

        zombie.setGlowing(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setCanPickupItems(false);

        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        }
    }

    private boolean clearDemoZombie(UUID ownerId) {
        SpawnedZombie spawnedZombie = activeZombies.remove(ownerId);
        if (spawnedZombie == null) {
            return false;
        }

        Zombie zombie = spawnedZombie.bukkitZombie;
        if (zombie.isValid() && !zombie.isDead()) {
            Bukkit.getMobGoals().removeGoal(zombie, spawnedZombie.goalKey);
        }
        spawnedZombie.customZombie.despawn();
        return true;
    }

    private VersionedEntityPlatform resolvePlatform() {
        if (entityPlatform != null) {
            return entityPlatform;
        }

        try {
            entityPlatform = SpigotEntityBootstrap.boot();
            return entityPlatform;
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "The versions demo only runs on a supported Paper server. Current server: "
                    + Bukkit.getBukkitVersion(),
                    ex
            );
        }
    }

    private Location resolveSpawnLocation(Player owner) {
        return owner.getLocation().clone()
                .add(owner.getLocation().getDirection().normalize().multiply(4.0D))
                .add(0.0D, 0.5D, 0.0D);
    }

    private static final class SpawnedZombie {
        private final CustomZombie customZombie;
        private final Zombie bukkitZombie;
        private final GoalKey<Zombie> goalKey;

        private SpawnedZombie(CustomZombie customZombie, Zombie bukkitZombie, GoalKey<Zombie> goalKey) {
            this.customZombie = customZombie;
            this.bukkitZombie = bukkitZombie;
            this.goalKey = goalKey;
        }
    }
}
