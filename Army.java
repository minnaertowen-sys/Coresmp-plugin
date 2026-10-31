package net.coresmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Player armies. Soldiers are humanoid husks (they don't burn in daylight) named after you,
 * wearing a copy of your armor, weapon and offhand. They never attack players, follow you,
 * teleport to you if left behind, and fight hostile mobs near you.
 * Copied gear never drops, so nothing can be duplicated.
 */
final class Army {
    static final String TAG = "coresmp_army";
    private static final Map<UUID, Integer> misses = new HashMap<>();
    /** owner -> hostile mob they last fought (and when) */
    private static final Map<UUID, UUID> foe = new HashMap<>();
    private static final Map<UUID, Long> foeTime = new HashMap<>();

    private Army() {}

    static boolean isSoldier(Entity e) {
        return e.getScoreboardTags().contains(TAG);
    }

    static void noteFight(Player owner, Entity enemy) {
        foe.put(owner.getUniqueId(), enemy.getUniqueId());
        foeTime.put(owner.getUniqueId(), System.currentTimeMillis());
    }

    private static ItemStack c(ItemStack s) {
        return s == null ? null : s.clone();
    }

    private static void equip(Husk h, Player p) {
        PlayerInventory pi = p.getInventory();
        EntityEquipment eq = h.getEquipment();
        eq.setHelmet(c(pi.getHelmet()));
        eq.setChestplate(c(pi.getChestplate()));
        eq.setLeggings(c(pi.getLeggings()));
        eq.setBoots(c(pi.getBoots()));
        eq.setItemInMainHand(c(pi.getItemInMainHand()));
        eq.setItemInOffHand(c(pi.getItemInOffHand()));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    static void summon(Player p) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        if (d.army.size() >= d.soldiers) {
            Msg.send(p, "§cNo free soldier slots (§e" + d.army.size() + "/" + d.soldiers
                    + "§c). Finish quests with §f/core quest§c to earn more.");
            return;
        }
        NamespacedKey ownerKey = new NamespacedKey(CorePlugin.get(), "army_owner");
        Husk h = p.getWorld().spawn(p.getLocation(), Husk.class, hs -> {
            hs.setBaby(false);
            hs.setCanPickupItems(false);
            hs.setRemoveWhenFarAway(false);
            hs.setPersistent(true);
            hs.customName(Component.text("[Army] " + p.getName()));
            hs.setCustomNameVisible(true);
            hs.addScoreboardTag(TAG);
            hs.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, p.getUniqueId().toString());
            equip(hs, p);
        });
        d.army.add(h.getUniqueId().toString());
        Store.markDirty();
        Msg.send(p, "A soldier joins you wearing your gear. Army: §e" + d.army.size() + "/" + d.soldiers);
    }

    /** Copies your current armor/weapon onto every soldier. */
    static void gear(Player p) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        int n = 0;
        for (String id : d.army) {
            Entity e = Bukkit.getEntity(UUID.fromString(id));
            if (e instanceof Husk h) {
                equip(h, p);
                n++;
            }
        }
        Msg.send(p, "Updated the gear of §e" + n + "§f soldier(s) to match yours.");
    }

    static void dismiss(Player p) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        int n = 0;
        for (String id : d.army) {
            Entity e = Bukkit.getEntity(UUID.fromString(id));
            if (e != null) {
                e.remove();
                n++;
            }
        }
        d.army.clear();
        Store.markDirty();
        Msg.send(p, "Army dismissed (§e" + n + "§f soldiers removed).");
    }

    static void status(Player p) {
        Store.PlayerData d = Store.pd(p.getUniqueId());
        Msg.send(p, "Army: §e" + d.army.size() + "§f active / §e" + d.soldiers + "§f earned. §7/core army summon | dismiss | gear");
    }

    static void give(Player target, int amount) {
        Store.PlayerData d = Store.pd(target.getUniqueId());
        d.soldiers = Math.min(Cfg.maxArmy, d.soldiers + amount);
        Store.markDirty();
        Msg.send(target, "You were granted §e" + amount + "§f soldier slot(s). Total: §e" + d.soldiers);
    }

    private static LivingEntity targetFor(Player p) {
        Long t = foeTime.get(p.getUniqueId());
        if (t != null && System.currentTimeMillis() - t < 10_000) {
            Entity e = Bukkit.getEntity(foe.get(p.getUniqueId()));
            if (e instanceof LivingEntity le && le.isValid() && le instanceof Enemy && !isSoldier(le)) return le;
        }
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(10, 5, 10)) {
            if (e instanceof LivingEntity le && e instanceof Enemy && !isSoldier(e) && le.isValid()) {
                double dd = le.getLocation().distanceSquared(p.getLocation());
                if (dd < bestD) {
                    bestD = dd;
                    best = le;
                }
            }
        }
        return best;
    }

    /** Called every 10 ticks. */
    static void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Store.PlayerData d = Store.peek(p.getUniqueId());
            if (d == null || d.army.isEmpty()) continue;
            LivingEntity target = null;
            boolean targetLooked = false;
            Iterator<String> it = d.army.iterator();
            while (it.hasNext()) {
                UUID id = UUID.fromString(it.next());
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof Mob m)) {
                    // may just be an unloaded chunk or still loading after a restart: give it ~15s
                    if (misses.merge(id, 1, Integer::sum) >= 30) {
                        it.remove();
                        misses.remove(id);
                        Store.markDirty();
                    }
                    continue;
                }
                misses.remove(id);
                if (m.isDead() || !m.isValid()) {
                    it.remove();
                    Store.markDirty();
                    continue;
                }
                Location pl = p.getLocation();
                if (m.getWorld() != p.getWorld() || m.getLocation().distanceSquared(pl) > 24 * 24) {
                    m.teleport(pl);
                    continue;
                }
                if (!targetLooked) {
                    target = targetFor(p);
                    targetLooked = true;
                }
                if (target != null) {
                    m.setTarget(target);
                } else if (m.getTarget() == null && m.getLocation().distanceSquared(pl) > 36) {
                    m.getPathfinder().moveTo(pl, 1.15);
                }
            }
        }
    }
}
