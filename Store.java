package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** All saved data lives in plugins/CoreSmp/data.yml. */
final class Store {
    static class PlayerData {
        String name;
        int questIndex, progress, soldiers;
        List<String> army = new ArrayList<>();
        Map<String, Long> kitUsed = new HashMap<>();
        int mobKills, playerKills, deaths, mined, minutes, duelWins, duelLosses, revives, streak;
        long lastDaily;
    }

    static class Ban {
        String name;
        long until;

        Ban(String name, long until) {
            this.name = name;
            this.until = until;
        }
    }

    static class Kit {
        int cooldown;
        List<String> commands = new ArrayList<>();
        Map<Integer, ItemStack> items = new TreeMap<>();

        Kit(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    static final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    static Location spawn;
    static final Map<String, Location> warps = new TreeMap<>();
    static final Map<UUID, PlayerData> players = new HashMap<>();
    static final Map<UUID, Ban> bans = new ConcurrentHashMap<>();
    static final Map<String, Kit> kits = new LinkedHashMap<>();
    static boolean chatDisabled;
    static boolean unstableOn = true;
    private static boolean kitsSeeded;
    private static volatile boolean dirty;
    private static File file;

    private Store() {}

    static PlayerData pd(UUID id) {
        return players.computeIfAbsent(id, k -> new PlayerData());
    }

    static PlayerData peek(UUID id) {
        return players.get(id);
    }

    static Map<String, Location> homes(UUID id) {
        return homes.computeIfAbsent(id, k -> new TreeMap<>());
    }

    static void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ load
    static void load(CorePlugin pl) {
        pl.getDataFolder().mkdirs();
        file = new File(pl.getDataFolder(), "data.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection hs = y.getConfigurationSection("homes");
        if (hs != null) {
            for (String u : hs.getKeys(false)) {
                ConfigurationSection s = hs.getConfigurationSection(u);
                if (s == null) continue;
                Map<String, Location> m = new TreeMap<>();
                for (String n : s.getKeys(false)) {
                    Location l = s.getLocation(n);
                    if (l != null) m.put(n, l);
                }
                homes.put(UUID.fromString(u), m);
            }
        }
        spawn = y.getLocation("spawn");
        ConfigurationSection ws = y.getConfigurationSection("warps");
        if (ws != null) for (String n : ws.getKeys(false)) {
            Location l = ws.getLocation(n);
            if (l != null) warps.put(n, l);
        }
        ConfigurationSection ps = y.getConfigurationSection("players");
        if (ps != null) {
            for (String u : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(u);
                if (s == null) continue;
                PlayerData d = new PlayerData();
                d.name = s.getString("name");
                d.questIndex = s.getInt("questIndex");
                d.progress = s.getInt("progress");
                d.soldiers = s.getInt("soldiers");
                d.army = new ArrayList<>(s.getStringList("army"));
                d.mobKills = s.getInt("mobKills");
                d.playerKills = s.getInt("playerKills");
                d.deaths = s.getInt("deaths");
                d.mined = s.getInt("mined");
                d.minutes = s.getInt("minutes");
                d.duelWins = s.getInt("duelWins");
                d.duelLosses = s.getInt("duelLosses");
                d.revives = s.getInt("revives");
                d.streak = s.getInt("streak");
                d.lastDaily = s.getLong("lastDaily");
                ConfigurationSection ku = s.getConfigurationSection("kitUsed");
                if (ku != null) for (String k : ku.getKeys(false)) d.kitUsed.put(k, ku.getLong(k));
                players.put(UUID.fromString(u), d);
            }
        }
        ConfigurationSection bs = y.getConfigurationSection("bans");
        if (bs != null) for (String u : bs.getKeys(false)) {
            bans.put(UUID.fromString(u), new Ban(bs.getString(u + ".name", "?"), bs.getLong(u + ".until")));
        }
        ConfigurationSection ks = y.getConfigurationSection("kits");
        if (ks != null) {
            for (String n : ks.getKeys(false)) {
                ConfigurationSection s = ks.getConfigurationSection(n);
                if (s == null) continue;
                Kit k = new Kit(s.getInt("cooldown", 3600));
                k.commands = new ArrayList<>(s.getStringList("commands"));
                ConfigurationSection is = s.getConfigurationSection("items");
                if (is != null) for (String slot : is.getKeys(false)) {
                    ItemStack it = is.getItemStack(slot);
                    if (it != null) k.items.put(Integer.parseInt(slot), it);
                }
                kits.put(n, k);
            }
        }
        chatDisabled = y.getBoolean("chatDisabled", false);
        unstableOn = y.getBoolean("unstableOn", true);
        kitsSeeded = y.getBoolean("kitsSeeded", false);
        if (!kitsSeeded) {
            Kits.seedDefaults();
            kitsSeeded = true;
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ save
    private static String toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Location>> e : homes.entrySet())
            for (Map.Entry<String, Location> h : e.getValue().entrySet())
                y.set("homes." + e.getKey() + "." + h.getKey(), h.getValue());
        y.set("spawn", spawn);
        for (Map.Entry<String, Location> w : warps.entrySet()) y.set("warps." + w.getKey(), w.getValue());
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            String b = "players." + e.getKey() + ".";
            PlayerData d = e.getValue();
            y.set(b + "name", d.name);
            y.set(b + "questIndex", d.questIndex);
            y.set(b + "progress", d.progress);
            y.set(b + "soldiers", d.soldiers);
            y.set(b + "army", d.army);
            y.set(b + "mobKills", d.mobKills);
            y.set(b + "playerKills", d.playerKills);
            y.set(b + "deaths", d.deaths);
            y.set(b + "mined", d.mined);
            y.set(b + "minutes", d.minutes);
            y.set(b + "duelWins", d.duelWins);
            y.set(b + "duelLosses", d.duelLosses);
            y.set(b + "revives", d.revives);
            y.set(b + "streak", d.streak);
            y.set(b + "lastDaily", d.lastDaily);
            for (Map.Entry<String, Long> k : d.kitUsed.entrySet()) y.set(b + "kitUsed." + k.getKey(), k.getValue());
        }
        for (Map.Entry<UUID, Ban> e : bans.entrySet()) {
            y.set("bans." + e.getKey() + ".name", e.getValue().name);
            y.set("bans." + e.getKey() + ".until", e.getValue().until);
        }
        for (Map.Entry<String, Kit> e : kits.entrySet()) {
            String b = "kits." + e.getKey() + ".";
            y.set(b + "cooldown", e.getValue().cooldown);
            y.set(b + "commands", e.getValue().commands);
            for (Map.Entry<Integer, ItemStack> it : e.getValue().items.entrySet())
                y.set(b + "items." + it.getKey(), it.getValue());
        }
        y.set("chatDisabled", chatDisabled);
        y.set("unstableOn", unstableOn);
        y.set("kitsSeeded", kitsSeeded);
        return y.saveToString();
    }

    static void saveAsyncIfDirty(CorePlugin pl) {
        if (!dirty) return;
        dirty = false;
        final String text = toYaml(); // snapshot on the main thread
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> write(text));
    }

    static void saveNow() {
        dirty = false;
        write(toYaml());
    }

    private static synchronized void write(String text) {
        try {
            File tmp = new File(file.getParentFile(), "data.yml.tmp");
            Files.writeString(tmp.toPath(), text);
            Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CorePlugin.get().getLogger().severe("Could not save data.yml: " + e);
        }
    }
}
