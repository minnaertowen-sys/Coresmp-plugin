package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.*;

/**
 * /core duel <player>: a safe 1v1. Nobody really dies (no deathban, no item loss), both fighters
 * are healed and sent back afterwards, and a crowd of NPC spectators watches from a ring around the arena.
 */
final class Duel {
    private static final String CROWD_TAG = "coresmp_crowd";
    private static final String NEW_TAG = "coresmp_new_crowd";

    private record Req(UUID from, long expires) {}

    private static final Map<UUID, Req> requests = new HashMap<>();
    private static final List<Duel> active = new ArrayList<>();
    private static int crowdMode = 0; // 0 = mannequin with skin, 1 = plain mannequin, 2 = villager (fallbacks)
    private static final Random RNG = new Random();

    private final UUID a, b;
    private final String an, bn;
    private final Location aHome, bHome, aStart, bStart;
    private final World world;
    private final double cx, cy, cz;
    private final List<UUID> crowd = new ArrayList<>();
    private int countdown;
    private int fightTicks;
    private boolean fighting;
    private boolean done;

    private Duel(Player pa, Player pb, double cx, double cz) {
        this.a = pa.getUniqueId();
        this.b = pb.getUniqueId();
        this.an = pa.getName();
        this.bn = pb.getName();
        this.aHome = pa.getLocation();
        this.bHome = pb.getLocation();
        this.world = pb.getWorld();
        this.cx = cx;
        this.cz = cz;
        double ax = cx - 4, bx = cx + 4;
        double ay = groundY(world, ax, cz), by = groundY(world, bx, cz);
        this.cy = Math.max(ay, by);
        this.aStart = new Location(world, ax, ay, cz, -90f, 0f);
        this.bStart = new Location(world, bx, by, cz, 90f, 0f);
        this.countdown = Math.max(1, Cfg.duelCountdown) * 20;
    }

    // ------------------------------------------------------------ requests
    static void challenge(Player from, Player to) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            Msg.send(from, "§cYou can't duel yourself.");
            return;
        }
        if (of(from.getUniqueId()) != null || of(to.getUniqueId()) != null) {
            Msg.send(from, "§cOne of you is already in a duel.");
            return;
        }
        if (from.getWorld() != to.getWorld()) {
            Msg.send(from, "§cYou must be in the same world.");
            return;
        }
        requests.put(to.getUniqueId(), new Req(from.getUniqueId(), System.currentTimeMillis() + Cfg.duelTimeout * 1000L));
        Msg.send(from, "Duel challenge sent to §e" + to.getName() + "§f.");
        Msg.send(to, "§c⚔ §e" + from.getName() + "§f challenged you to a duel! §a/core duel accept §for §c/core duel deny §f("
                + Cfg.duelTimeout + "s)");
        Msg.send(to, "§7Stand somewhere open and outdoors: the arena forms around where you stand when you accept.");
    }

    static void deny(Player target) {
        Req r = requests.remove(target.getUniqueId());
        if (r == null) {
            Msg.send(target, "§cNo duel challenge pending.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        Msg.send(target, "Challenge denied.");
        if (from != null) Msg.send(from, "§c" + target.getName() + " declined your duel.");
    }

    static void accept(Player target) {
        Req r = requests.remove(target.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            Msg.send(target, "§cNo duel challenge pending.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            Msg.send(target, "§cThat player is no longer online.");
            return;
        }
        if (of(from.getUniqueId()) != null || of(target.getUniqueId()) != null) {
            Msg.send(target, "§cOne of you is already in a duel.");
            return;
        }
        if (from.getWorld() != target.getWorld()) {
            Msg.send(target, "§cYou must be in the same world.");
            return;
        }
        Location tl = target.getLocation();
        if (target.getWorld().getHighestBlockYAt(tl) > tl.getBlockY()) {
            Msg.send(target, "§cGo somewhere open and outdoors (sky above you), then accept again.");
            requests.put(target.getUniqueId(), r);
            return;
        }
        Duel d = new Duel(from, target, tl.getX(), tl.getZ());
        active.add(d);
        d.begin(from, target);
    }

    static void forfeit(Player p) {
        Duel d = of(p.getUniqueId());
        if (d == null) {
            Msg.send(p, "§cYou aren't in a duel.");
            return;
        }
        UUID winner = p.getUniqueId().equals(d.a) ? d.b : d.a;
        d.finish(winner, " (forfeit)");
        active.remove(d);
    }

    // ------------------------------------------------------------ lookup / events
    static Duel of(UUID id) {
        for (Duel d : active) if (d.a.equals(id) || d.b.equals(id)) return d;
        return null;
    }

    boolean isFighting() {
        return fighting;
    }

    /** Called when a duelist takes lethal damage; the damage is cancelled by the caller. */
    static void onLethal(Player p) {
        Duel d = of(p.getUniqueId());
        if (d == null) return;
        UUID winner = p.getUniqueId().equals(d.a) ? d.b : d.a;
        d.finish(winner, "");
        active.remove(d);
    }

    static void tick() {
        if (active.isEmpty()) return;
        for (Duel d : new ArrayList<>(active)) {
            if (d.step()) active.remove(d);
        }
    }

    static void shutdown() {
        for (Duel d : new ArrayList<>(active)) d.finish(null, " (server stopping)");
        active.clear();
    }

    // ------------------------------------------------------------ lifecycle
    private void begin(Player pa, Player pb) {
        Teleports.cancel(a);
        Teleports.cancel(b);
        for (Player p : List.of(pa, pb)) {
            p.setHealth(p.getMaxHealth());
            p.setFoodLevel(20);
            p.setFireTicks(0);
        }
        pa.teleport(aStart);
        pb.teleport(bStart);
        spawnCrowd();
        Bukkit.broadcastMessage("§c⚔ §6DUEL! §f" + an + " §7vs §f" + bn + " §7- come watch!");
        pa.sendTitle("§6DUEL", "§7" + bn + " awaits...", 2, 16, 2);
        pb.sendTitle("§6DUEL", "§7" + an + " awaits...", 2, 16, 2);
    }

    private boolean step() {
        Player pa = Bukkit.getPlayer(a);
        Player pb = Bukkit.getPlayer(b);
        if (pa == null || pb == null) {
            finish(pa == null ? b : a, " (opponent left)");
            return true;
        }
        if (pa.getWorld() != world || pb.getWorld() != world) {
            finish(null, " (cancelled: left the arena world)");
            return true;
        }
        if (!fighting) {
            hold(pa, aStart);
            hold(pb, bStart);
            if (countdown > 0 && countdown % 20 == 0) {
                int sec = countdown / 20;
                for (Player p : List.of(pa, pb)) {
                    p.sendTitle("§c" + sec, "", 0, 20, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
            }
            if (--countdown <= 0) {
                fighting = true;
                for (Player p : List.of(pa, pb)) {
                    p.sendTitle("§4§lFIGHT!", "", 2, 20, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                }
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, cx, cy + 1, cz, 120, 1.5, 1, 1.5, 0.4);
            }
            return false;
        }
        fightTicks++;
        if (fightTicks > Cfg.duelMax * 20) {
            finish(null, " (time limit - draw)");
            return true;
        }
        if (outside(pa)) {
            finish(b, " (ran away)");
            return true;
        }
        if (outside(pb)) {
            finish(a, " (ran away)");
            return true;
        }
        return false;
    }

    private boolean outside(Player p) {
        double dx = p.getLocation().getX() - cx, dz = p.getLocation().getZ() - cz;
        return dx * dx + dz * dz > 30 * 30;
    }

    private void hold(Player p, Location l) {
        if (p.getLocation().distanceSquared(l) > 0.8) p.teleport(l);
    }

    /** Ends the duel. winner == null means a draw/cancel. */
    private void finish(UUID winner, String note) {
        if (done) return;
        done = true;
        if (winner != null) {
            for (int i = 0; i < 3; i++) {
                Location fl = new Location(world, cx + RNG.nextInt(7) - 3, cy + 1, cz + RNG.nextInt(7) - 3);
                try {
                    world.spawn(fl, Firework.class, fw -> {
                        FireworkMeta m = fw.getFireworkMeta();
                        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                                .withColor(Color.RED).withFade(Color.YELLOW).build());
                        fw.setFireworkMeta(m);
                    }).detonate();
                } catch (Exception ignored) {
                }
            }
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, cx, cy + 1, cz, 150, 3, 2, 3, 0.3);
        }
        for (UUID id : crowd) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        crowd.clear();
        for (UUID id : List.of(a, b)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.setHealth(p.getMaxHealth());
            p.setFireTicks(0);
            p.setFoodLevel(20);
            p.teleport(id.equals(a) ? aHome : bHome);
        }
        if (winner != null) {
            UUID loser = winner.equals(a) ? b : a;
            Store.pd(winner).duelWins++;
            Store.pd(loser).duelLosses++;
            Store.markDirty();
            String wn = winner.equals(a) ? an : bn;
            String ln = winner.equals(a) ? bn : an;
            Bukkit.broadcastMessage("§c⚔ §6" + wn + " §fdefeated §7" + ln + "§f in a duel!" + note);
        } else {
            Bukkit.broadcastMessage("§c⚔ §fThe duel between §e" + an + "§f and §e" + bn + "§f ended" + note + ".");
        }
    }

    // ------------------------------------------------------------ crowd
    private static double groundY(World w, double x, double z) {
        return w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
    }

    private void spawnCrowd() {
        int n = Math.min(60, Cfg.crowdSize);
        if (n <= 0) return;
        double r = Math.max(7, Cfg.crowdRadius);
        List<String> names = Cfg.crowdNames;
        for (int i = 0; i < n; i++) {
            double ring = r + (i % 2) * 2;
            double ang = Math.PI * 2 * i / n;
            double x = cx + Math.cos(ang) * ring;
            double z = cz + Math.sin(ang) * ring;
            double y = groundY(world, x, z);
            float yaw = (float) Math.toDegrees(Math.atan2(-(cx - x), cz - z)); // face the arena
            String name = names.isEmpty() ? "Steve" : names.get(RNG.nextInt(names.size()));
            UUID id = spawnOne(x, y, z, yaw, name);
            if (id != null) crowd.add(id);
        }
        if (crowd.isEmpty()) {
            Player pa = Bukkit.getPlayer(a);
            if (pa != null) Msg.send(pa, "§7(The crowd couldn't spawn on this server version.)");
        }
    }

    private UUID spawnOne(double x, double y, double z, float yaw, String name) {
        String pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
        String rot = String.format(Locale.ROOT, "Rotation:[%.1ff,0f]", yaw);
        String in = "execute in " + world.getKey().asString() + " run ";
        for (int m = crowdMode; m < 3; m++) {
            String cmd;
            if (m == 0) {
                cmd = in + "summon minecraft:mannequin " + pos + " {Tags:[\"" + NEW_TAG + "\"]," + rot
                        + ",Invulnerable:1b,Silent:1b,profile:{name:\"" + name + "\"}}";
            } else if (m == 1) {
                cmd = in + "summon minecraft:mannequin " + pos + " {Tags:[\"" + NEW_TAG + "\"]," + rot
                        + ",Invulnerable:1b,Silent:1b}";
            } else {
                cmd = in + "summon minecraft:villager " + pos + " {Tags:[\"" + NEW_TAG + "\"]," + rot
                        + ",NoAI:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b}";
            }
            Run.cmd(cmd);
            Collection<Entity> found = world.getNearbyEntities(new Location(world, x, y, z), 1.5, 3, 1.5,
                    e -> e.getScoreboardTags().contains(NEW_TAG));
            if (!found.isEmpty()) {
                crowdMode = m; // remember what works on this server
                Entity e = found.iterator().next();
                e.removeScoreboardTag(NEW_TAG);
                e.addScoreboardTag(CROWD_TAG);
                return e.getUniqueId();
            }
        }
        return null;
    }
}
