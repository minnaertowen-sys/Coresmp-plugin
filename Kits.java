package net.coresmp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Kits are saved in data.yml and built in game from an admin's own inventory. */
final class Kits {
    private Kits() {}

    static void seedDefaults() {
        Store.Kit starter = new Store.Kit(-1);
        starter.items.put(0, new ItemStack(Material.STONE_SWORD));
        starter.items.put(1, new ItemStack(Material.STONE_PICKAXE));
        starter.items.put(2, new ItemStack(Material.COOKED_BEEF, 16));
        Store.kits.put("starter", starter);

        Store.Kit warrior = new Store.Kit(3600);
        warrior.items.put(0, new ItemStack(Material.IRON_SWORD));
        warrior.items.put(1, new ItemStack(Material.GOLDEN_APPLE, 2));
        warrior.items.put(38, new ItemStack(Material.IRON_CHESTPLATE));
        warrior.items.put(40, new ItemStack(Material.SHIELD));
        Store.kits.put("warrior", warrior);
    }

    /** Saves your whole inventory (hotbar, armor, offhand) as the kit. Running it again edits the kit. */
    static void create(Player p, String name, Integer cooldown) {
        if (!Basics.validName(p, name)) return;
        Store.Kit k = Store.kits.computeIfAbsent(name, n -> new Store.Kit(3600));
        if (cooldown != null) k.cooldown = cooldown;
        k.items.clear();
        ItemStack[] contents = p.getInventory().getContents();
        int saved = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack st = contents[i];
            if (st == null || st.getType().isAir()) continue;
            k.items.put(i, st.clone());
            saved++;
        }
        Store.markDirty();
        Msg.send(p, "Kit §e" + name + "§f saved with §e" + saved + "§f item stacks (cooldown "
                + (k.cooldown < 0 ? "one-time" : fmt(k.cooldown)) + ").");
    }

    static void delete(Player p, String name) {
        if (Store.kits.remove(name) == null) Msg.send(p, "§cNo kit named §e" + name + "§c.");
        else {
            Store.markDirty();
            Msg.send(p, "Kit §e" + name + "§f deleted.");
        }
    }

    static void setCooldown(Player p, String name, int seconds) {
        Store.Kit k = Store.kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c.");
            return;
        }
        k.cooldown = seconds;
        Store.markDirty();
        Msg.send(p, "Kit §e" + name + "§f cooldown: " + (seconds < 0 ? "one-time" : fmt(seconds)));
    }

    static void addCommand(Player p, String name, String command) {
        Store.Kit k = Store.kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c.");
            return;
        }
        k.commands.add(command);
        Store.markDirty();
        Msg.send(p, "Added command to kit §e" + name + "§f. (%player% = the player)");
    }

    static void clearCommands(Player p, String name) {
        Store.Kit k = Store.kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c.");
            return;
        }
        k.commands.clear();
        Store.markDirty();
        Msg.send(p, "Cleared commands of kit §e" + name + "§f.");
    }

    static void info(Player p, String name) {
        Store.Kit k = Store.kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c.");
            return;
        }
        Msg.send(p, "Kit §e" + name + "§f | cooldown " + (k.cooldown < 0 ? "one-time" : fmt(k.cooldown))
                + " | " + k.items.size() + " stacks, " + k.commands.size() + " commands");
        int shown = 0;
        for (ItemStack it : k.items.values()) {
            if (shown++ >= 12) {
                p.sendMessage("§7...and more");
                break;
            }
            p.sendMessage("§7- §f" + it.getAmount() + "x " + it.getType().name().toLowerCase().replace('_', ' '));
        }
    }

    static void give(Player p, String name) {
        Store.Kit k = Store.kits.get(name);
        if (k == null) {
            Msg.send(p, "§cNo kit named §e" + name + "§c. Kits: §f" + String.join(", ", Store.kits.keySet()));
            return;
        }
        Store.PlayerData d = Store.pd(p.getUniqueId());
        long now = System.currentTimeMillis();
        Long last = d.kitUsed.get(name);
        if (last != null) {
            if (k.cooldown < 0) {
                Msg.send(p, "§cYou already claimed the §e" + name + "§c kit.");
                return;
            }
            long left = (last + k.cooldown * 1000L - now + 999) / 1000;
            if (left > 0) {
                Msg.send(p, "§cKit §e" + name + "§c is on cooldown for §e" + fmt(left) + "§c.");
                return;
            }
        }
        PlayerInventory inv = p.getInventory();
        for (Map.Entry<Integer, ItemStack> e : k.items.entrySet()) {
            ItemStack st = e.getValue().clone();
            int slot = e.getKey();
            ItemStack cur = slot < 41 ? inv.getItem(slot) : null;
            if (slot < 41 && (cur == null || cur.getType().isAir())) {
                inv.setItem(slot, st);
            } else {
                Map<Integer, ItemStack> left = inv.addItem(st);
                Location l = p.getLocation();
                for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(l, rest);
            }
        }
        Run.forPlayer(p, k.commands);
        d.kitUsed.put(name, now);
        Store.markDirty();
        Msg.send(p, "You received the §e" + name + "§f kit.");
    }

    static String fmt(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
