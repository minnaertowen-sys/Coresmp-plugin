package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Conservative server-side checks (speed, hover-fly, reach, auto-click). Violations decay over time,
 * players are set back, and only repeated flags lead to a kick. It never bans.
 */
final class AntiCheat {
    private static final class St {
        boolean init;
        double x, y, z;
        Location lastGround;
        double dist;
        int ticks, hover, grace, vl, decay;
        long atkWindow;
        int atkCount;
    }

    private static final Map<UUID, St> states = new HashMap<>();
    private static final Set<UUID> alerts = new HashSet<>();

    private AntiCheat() {}

    static void remove(UUID id) {
        states.remove(id);
    }

    static void grace(UUID id) {
        St st = states.get(id);
        if (st != null) st.grace = 20;
    }

    static boolean toggleAlerts(UUID id) {
        if (!alerts.remove(id)) {
            alerts.add(id);
            return true;
        }
        return false;
    }

    static int violations(UUID id) {
        St s = states.get(id);
        return s == null ? 0 : s.vl;
    }

    private static boolean slowing(Material m) {
        return m == Material.COBWEB || m == Material.SWEET_BERRY_BUSH || m == Material.POWDER_SNOW
                || m == Material.HONEY_BLOCK || m == Material.SCAFFOLDING || m == Material.VINE;
    }

    static void tick() {
        if (!Cfg.ac) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            St st = states.computeIfAbsent(p.getUniqueId(), k -> new St());
            Location l = p.getLocation();
            double x = l.getX(), y = l.getY(), z = l.getZ();
            if (st.vl > 0 && ++st.decay >= 600) {
                st.vl--;
                st.decay = 0;
            }
            if (!st.init) {
                st.init = true;
                st.x = x;
                st.y = y;
                st.z = z;
                continue;
            }
            double dx = x - st.x, dy = y - st.y, dz = z - st.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            st.x = x;
            st.y = y;
            st.z = z;
            if (st.grace > 0) st.grace--;

            GameMode gm = p.getGameMode();
            boolean exempt = st.grace > 0 || h > 10 || Math.abs(dy) > 10
                    || (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE)
                    || p.getAllowFlight() || p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isRiptiding()
                    || p.isSwimming() || p.isInWater() || p.isClimbing()
                    || l.getBlock().getType() == Material.LAVA;
            boolean onGround = p.isOnGround();
            if (onGround && !exempt) st.lastGround = l.clone();
            if (exempt) {
                st.dist = 0;
                st.ticks = 0;
                st.hover = 0;
                continue;
            }

            // ---- speed: average horizontal speed over 20 ticks ----
            st.dist += h;
            if (++st.ticks >= 20) {
                double avg = st.dist / 20.0;
                st.dist = 0;
                st.ticks = 0;
                double limit = Cfg.maxSpeed;
                PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
                if (sp != null) limit *= 1.0 + 0.25 * (sp.getAmplifier() + 1);
                if (avg > limit) flag(p, st, "speed", String.format(Locale.ROOT, "%.2f b/t", avg));
            }

            // ---- fly: hovering over empty air ----
            boolean airBelow = l.getBlock().getRelative(BlockFace.DOWN).getType().isAir()
                    && l.getBlock().getRelative(BlockFace.DOWN, 2).getType().isAir();
            if (!onGround && airBelow && Math.abs(dy) < 0.005 && !slowing(l.getBlock().getType())
                    && !p.hasPotionEffect(PotionEffectType.LEVITATION) && !p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                if (++st.hover > Cfg.hoverTicks) {
                    st.hover = 0;
                    flag(p, st, "fly", "hovering");
                }
            } else {
                st.hover = 0;
            }
        }
    }

    /** @return true if the attack should be cancelled. */
    static boolean checkAttack(Player p, Entity target) {
        if (!Cfg.ac || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;
        if (p.hasPermission("coresmp.ac.bypass")) return false;
        St st = states.computeIfAbsent(p.getUniqueId(), k -> new St());
        if (p.getWorld() == target.getWorld()) {
            double d = p.getLocation().distance(target.getLocation());
            if (d > Cfg.maxReach) {
                flag(p, st, "reach", String.format(Locale.ROOT, "%.1f blocks", d));
                return true;
            }
        }
        long now = System.currentTimeMillis();
        if (now - st.atkWindow > 1000) {
            st.atkWindow = now;
            st.atkCount = 0;
        }
        if (++st.atkCount > Cfg.maxCps) {
            flag(p, st, "autoclick", st.atkCount + " hits/s");
            return true;
        }
        return false;
    }

    private static void flag(Player p, St st, String type, String detail) {
        if (p.hasPermission("coresmp.ac.bypass")) return;
        st.vl++;
        CorePlugin.get().getLogger().warning("[AntiCheat] " + p.getName() + " flagged for " + type + " (" + detail + ") VL=" + st.vl);
        for (UUID id : alerts) {
            Player a = Bukkit.getPlayer(id);
            if (a != null) Msg.send(a, "§c[AC] §e" + p.getName() + "§f " + type + " (" + detail + ") VL " + st.vl);
        }
        if (st.vl >= Cfg.kickVl) {
            st.vl = 0;
            p.kickPlayer("§cKicked by CoreSmp anti-cheat (" + type + "). If this was a mistake, rejoin.");
            return;
        }
        if ((type.equals("speed") || type.equals("fly")) && st.lastGround != null) {
            p.teleport(st.lastGround);
            st.grace = 20;
        }
    }
}
