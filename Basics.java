package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/** spawn, homes, warps, tpa and rtp. */
final class Basics {
    private record Req(UUID from, long expires) {}

    private static final Map<UUID, Req> tpa = new HashMap<>();
    private static final Random RNG = new Random();

    private Basics() {}

    static boolean validName(Player p, String name) {
        if (!name.matches("[A-Za-z0-9_\\-]{1,24}")) {
            Msg.send(p, "§cNames may only use letters, numbers, _ and - (max 24).");
            return false;
        }
        return true;
    }

    // ---- spawn
    static void spawn(Player p) {
        if (Store.spawn == null || Store.spawn.getWorld() == null) {
            Msg.send(p, "§cSpawn has not been set yet. An admin must run /setspawn.");
            return;
        }
        if (!Teleports.cooldownOk(p, "spawn")) return;
        Teleports.setCooldown(p, "spawn", Cfg.spawnCd);
        Teleports.request(p, Store.spawn, "spawn");
    }

    static void setSpawn(Player p) {
        Store.spawn = p.getLocation();
        Store.markDirty();
        Msg.send(p, "Spawn set to your location.");
    }

    // ---- homes
    static void setHome(Player p, String name) {
        if (!validName(p, name)) return;
        Map<String, Location> homes = Store.homes(p.getUniqueId());
        if (!homes.containsKey(name) && homes.size() >= Cfg.maxHomes) {
            Msg.send(p, "§cYou can only have §e" + Cfg.maxHomes + "§c homes. Use /delhome first.");
            return;
        }
        homes.put(name, p.getLocation());
        Store.markDirty();
        Msg.send(p, "Home §e" + name + "§f set.");
    }

    static void home(Player p, String name) {
        Map<String, Location> homes = Store.homes(p.getUniqueId());
        Location l = homes.get(name);
        if (l == null) {
            Msg.send(p, "§cNo home named §e" + name + "§c. Your homes: §f" + String.join(", ", homes.keySet()));
            return;
        }
        if (!Teleports.cooldownOk(p, "home")) return;
        Teleports.setCooldown(p, "home", Cfg.homeCd);
        Teleports.request(p, l, "home " + name);
    }

    static void delHome(Player p, String name) {
        if (Store.homes(p.getUniqueId()).remove(name) == null) {
            Msg.send(p, "§cNo home named §e" + name + "§c.");
            return;
        }
        Store.markDirty();
        Msg.send(p, "Home §e" + name + "§f deleted.");
    }

    static void listHomes(Player p) {
        Set<String> names = Store.homes(p.getUniqueId()).keySet();
        Msg.send(p, "Homes (" + names.size() + "/" + Cfg.maxHomes + "): §e" + (names.isEmpty() ? "none" : String.join(", ", names)));
    }

    // ---- warps
    static void warp(Player p, String name) {
        Location l = Store.warps.get(name);
        if (l == null) {
            Msg.send(p, "§cNo warp named §e" + name + "§c.");
            return;
        }
        if (!Teleports.cooldownOk(p, "warp")) return;
        Teleports.setCooldown(p, "warp", Cfg.warpCd);
        Teleports.request(p, l, "warp " + name);
    }

    static void listWarps(Player p) {
        Msg.send(p, "Warps: §e" + (Store.warps.isEmpty() ? "none" : String.join(", ", Store.warps.keySet())));
    }

    static void setWarp(Player p, String name) {
        if (!validName(p, name)) return;
        Store.warps.put(name, p.getLocation());
        Store.markDirty();
        Msg.send(p, "Warp §e" + name + "§f set.");
    }

    static void delWarp(Player p, String name) {
        if (Store.warps.remove(name) == null) {
            Msg.send(p, "§cNo warp named §e" + name + "§c.");
            return;
        }
        Store.markDirty();
        Msg.send(p, "Warp §e" + name + "§f deleted.");
    }

    // ---- tpa
    static void tpaSend(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            Msg.send(from, "§cYou can't teleport to yourself.");
            return;
        }
        if (!Teleports.cooldownOk(from, "tpa")) return;
        Teleports.setCooldown(from, "tpa", Cfg.tpaCd);
        tpa.put(to.getUniqueId(), new Req(from.getUniqueId(), System.currentTimeMillis() + Cfg.tpaTimeout * 1000L));
        Msg.send(from, "Request sent to §e" + to.getName() + "§f.");
        Msg.send(to, "§e" + from.getName() + "§f wants to teleport to you. §a/tpaccept §for §c/tpdeny §f(" + Cfg.tpaTimeout + "s)");
    }

    private static Req take(Player target) {
        Req r = tpa.remove(target.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) return null;
        return r;
    }

    static void tpaAccept(Player target) {
        Req r = take(target);
        if (r == null) {
            Msg.send(target, "§cNo pending teleport request.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            Msg.send(target, "§cThat player is no longer online.");
            return;
        }
        Msg.send(target, "Request accepted.");
        Teleports.request(from, target.getLocation(), target.getName());
    }

    static void tpaDeny(Player target) {
        Req r = take(target);
        if (r == null) {
            Msg.send(target, "§cNo pending teleport request.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        Msg.send(target, "Request denied.");
        if (from != null) Msg.send(from, "§cYour teleport request was denied.");
    }

    // ---- rtp
    static void rtp(Player p) {
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            Msg.send(p, "§c/rtp only works in the Overworld.");
            return;
        }
        if (!Teleports.cooldownOk(p, "rtp")) return;
        Location dest = findRtp(w);
        if (dest == null) {
            Msg.send(p, "§cCouldn't find a safe spot. Try again.");
            return;
        }
        Teleports.setCooldown(p, "rtp", Cfg.rtpCd);
        Teleports.request(p, dest, "a random location");
    }

    private static Location findRtp(World w) {
        for (int i = 0; i < Cfg.rtpAttempts; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = Cfg.rtpMin + RNG.nextDouble() * Math.max(1, Cfg.rtpMax - Cfg.rtpMin);
            int x = Cfg.rtpCenterX + (int) (Math.cos(ang) * dist);
            int z = Cfg.rtpCenterZ + (int) (Math.sin(ang) * dist);
            Block b = w.getHighestBlockAt(x, z); // loads the chunk
            Material m = b.getType();
            if (b.isLiquid() || m.isAir() || Tag.LEAVES.isTagged(m) || m == Material.MAGMA_BLOCK || m == Material.CACTUS
                    || m == Material.FIRE || m == Material.CAMPFIRE || m == Material.SWEET_BERRY_BUSH) continue;
            Location dest = b.getLocation().add(0.5, 1, 0.5);
            if (!w.getWorldBorder().isInside(dest)) continue;
            dest.setYaw(RNG.nextFloat() * 360f);
            return dest;
        }
        return null;
    }
}
