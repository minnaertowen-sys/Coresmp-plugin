package net.coresmp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

/** Handles /core and the simple top-level commands (/spawn, /home, /tpa ...). */
final class Cmds implements CommandExecutor, TabCompleter {

    // ------------------------------------------------------------ helpers
    private static Player asPlayer(CommandSender s) {
        if (s instanceof Player p) return p;
        Msg.send(s, "§cThis command must be used by a player.");
        return null;
    }

    private static boolean admin(CommandSender s) {
        if (s.hasPermission("coresmp.admin")) return true;
        Msg.send(s, "§cYou don't have permission for that.");
        return false;
    }

    /** Player by name, or by selector (@p = nearest OTHER player). */
    private static Player findPlayer(CommandSender s, String arg) {
        try {
            if (arg.equalsIgnoreCase("@p") && s instanceof Player me) {
                Player best = null;
                double bd = Double.MAX_VALUE;
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o.equals(me) || o.getWorld() != me.getWorld()) continue;
                    double dd = o.getLocation().distanceSquared(me.getLocation());
                    if (dd < bd) {
                        bd = dd;
                        best = o;
                    }
                }
                if (best == null) Msg.send(s, "§cNo other player nearby.");
                return best;
            }
            if (arg.startsWith("@")) {
                for (Entity e : Bukkit.selectEntities(s, arg)) if (e instanceof Player p) return p;
                Msg.send(s, "§cNo player matched " + arg);
                return null;
            }
        } catch (IllegalArgumentException ex) {
            Msg.send(s, "§cInvalid selector.");
            return null;
        }
        Player p = Bukkit.getPlayerExact(arg);
        if (p == null) Msg.send(s, "§c" + arg + " is not online.");
        return p;
    }

    private static Integer parseInt(CommandSender s, String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            Msg.send(s, "§c'" + v + "' is not a number.");
            return null;
        }
    }

    // ------------------------------------------------------------ dispatch
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n = c.getName().toLowerCase();
        if (n.equals("core")) {
            core(s, a);
            return true;
        }
        Player p = asPlayer(s);
        if (p == null) return true;
        switch (n) {
            case "spawn" -> Basics.spawn(p);
            case "setspawn" -> {
                if (admin(s)) Basics.setSpawn(p);
            }
            case "sethome" -> Basics.setHome(p, a.length > 0 ? a[0] : "home");
            case "home" -> Basics.home(p, a.length > 0 ? a[0] : "home");
            case "delhome" -> {
                if (a.length < 1) Msg.send(p, "§cUsage: /delhome <name>");
                else Basics.delHome(p, a[0]);
            }
            case "homes" -> Basics.listHomes(p);
            case "warp" -> {
                if (a.length < 1) Msg.send(p, "§cUsage: /warp <name>");
                else Basics.warp(p, a[0]);
            }
            case "warps" -> Basics.listWarps(p);
            case "setwarp" -> {
                if (!admin(s)) break;
                if (a.length < 1) Msg.send(p, "§cUsage: /setwarp <name>");
                else Basics.setWarp(p, a[0]);
            }
            case "delwarp" -> {
                if (!admin(s)) break;
                if (a.length < 1) Msg.send(p, "§cUsage: /delwarp <name>");
                else Basics.delWarp(p, a[0]);
            }
            case "tpa" -> {
                if (a.length < 1) {
                    Msg.send(p, "§cUsage: /tpa <player>");
                    break;
                }
                Player t = findPlayer(p, a[0]);
                if (t != null) Basics.tpaSend(p, t);
            }
            case "tpaccept" -> Basics.tpaAccept(p);
            case "tpdeny" -> Basics.tpaDeny(p);
            case "rtp" -> Basics.rtp(p);
            default -> {
            }
        }
        return true;
    }

    // ------------------------------------------------------------ /core
    private void core(CommandSender s, String[] a) {
        if (a.length == 0) {
            help(s);
            return;
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "help" -> help(s);
            case "info" -> info(s);
            case "reload" -> {
                if (!admin(s)) return;
                Cfg.load(CorePlugin.get());
                Msg.send(s, "Config reloaded.");
            }
            case "kits" -> Msg.send(s, "Kits: §e" + (Store.kits.isEmpty() ? "none" : String.join(", ", Store.kits.keySet())));
            case "kit" -> kit(s, a);
            case "quest" -> {
                Player p = asPlayer(s);
                if (p != null) Quests.status(p);
            }
            case "army" -> army(s, a);
            case "stats" -> {
                if (a.length > 1) {
                    Player t = findPlayer(s, a[1]);
                    if (t != null) Features.stats(s, t);
                } else {
                    Player p = asPlayer(s);
                    if (p != null) Features.stats(s, p);
                }
            }
            case "top" -> {
                if (a.length < 2) Msg.send(s, "§cUsage: /core top <kills|pvp|deaths|mined|time|duels>");
                else Features.top(s, a[1]);
            }
            case "daily" -> {
                Player p = asPlayer(s);
                if (p != null) Features.daily(p);
            }
            case "back" -> {
                Player p = asPlayer(s);
                if (p != null) Features.back(p);
            }
            case "revive" -> revive(s, a);
            case "duel" -> duel(s, a);
            case "chat" -> {
                if (!admin(s)) return;
                if (a.length < 2) {
                    Msg.send(s, "§cUsage: /core chat <on|off>");
                    return;
                }
                boolean off = a[1].equalsIgnoreCase("off");
                Store.chatDisabled = off;
                Store.markDirty();
                Msg.send(s, off ? "Chat §cdisabled§f (admins and exempt names can still talk)." : "Chat §aenabled§f.");
            }
            case "deathban" -> {
                if (!admin(s)) return;
                if (a.length < 3 || !a[1].equalsIgnoreCase("pardon")) {
                    Msg.send(s, "§cUsage: /core deathban pardon <name>");
                    return;
                }
                if (Deathban.pardon(a[2])) Msg.send(s, "Pardoned §e" + a[2] + "§f.");
                else Msg.send(s, "§cNo active deathban for " + a[2]);
            }
            case "ac" -> ac(s, a);
            case "extras" -> extras(s, a);
            default -> help(s);
        }
    }

    private void kit(CommandSender s, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "§cUsage: /core kit <name> | create|delete|setcooldown|addcmd|clearcmds|info");
            return;
        }
        String sub = a[1].toLowerCase();
        boolean editor = sub.equals("create") || sub.equals("delete") || sub.equals("setcooldown")
                || sub.equals("addcmd") || sub.equals("clearcmds") || sub.equals("info");
        Player p = asPlayer(s);
        if (p == null) return;
        if (!editor) {
            Kits.give(p, a[1]);
            return;
        }
        if (!sub.equals("info") && !admin(s)) return;
        if (a.length < 3) {
            Msg.send(s, "§cUsage: /core kit " + sub + " <name>" + (sub.equals("setcooldown") ? " <seconds>" : ""));
            return;
        }
        String name = a[2];
        switch (sub) {
            case "create" -> {
                Integer cd = null;
                if (a.length > 3) {
                    cd = parseInt(s, a[3]);
                    if (cd == null) return;
                }
                Kits.create(p, name, cd);
            }
            case "delete" -> Kits.delete(p, name);
            case "setcooldown" -> {
                if (a.length < 4) {
                    Msg.send(s, "§cUsage: /core kit setcooldown <name> <seconds> (-1 = one-time)");
                    return;
                }
                Integer cd = parseInt(s, a[3]);
                if (cd != null) Kits.setCooldown(p, name, cd);
            }
            case "addcmd" -> {
                if (a.length < 4) {
                    Msg.send(s, "§cUsage: /core kit addcmd <name> <command...>");
                    return;
                }
                Kits.addCommand(p, name, String.join(" ", Arrays.copyOfRange(a, 3, a.length)));
            }
            case "clearcmds" -> Kits.clearCommands(p, name);
            case "info" -> Kits.info(p, name);
            default -> {
            }
        }
    }

    private void army(CommandSender s, String[] a) {
        if (a.length >= 2 && a[1].equalsIgnoreCase("give")) {
            if (!admin(s)) return;
            if (a.length < 4) {
                Msg.send(s, "§cUsage: /core army give <player> <amount>");
                return;
            }
            Player t = findPlayer(s, a[2]);
            Integer n = parseInt(s, a[3]);
            if (t != null && n != null) {
                Army.give(t, n);
                Msg.send(s, "Soldier slots granted.");
            }
            return;
        }
        Player p = asPlayer(s);
        if (p == null) return;
        if (a.length < 2) Army.status(p);
        else switch (a[1].toLowerCase()) {
            case "summon" -> Army.summon(p);
            case "dismiss" -> Army.dismiss(p);
            case "gear" -> Army.gear(p);
            default -> Army.status(p);
        }
    }

    private void revive(CommandSender s, String[] a) {
        if (a.length >= 2 && a[1].equalsIgnoreCase("give")) {
            if (!admin(s)) return;
            if (a.length < 4) {
                Msg.send(s, "§cUsage: /core revive give <player> <amount>");
                return;
            }
            Player t = findPlayer(s, a[2]);
            Integer n = parseInt(s, a[3]);
            if (t != null && n != null) {
                Features.giveRevives(t, n);
                Msg.send(s, "Revive tokens granted.");
            }
            return;
        }
        Player p = asPlayer(s);
        if (p == null) return;
        if (a.length < 2) Msg.send(p, "§cUsage: /core revive <name>   (you have §e" + Store.pd(p.getUniqueId()).revives + "§c tokens)");
        else Features.revive(p, a[1]);
    }

    private void duel(CommandSender s, String[] a) {
        Player p = asPlayer(s);
        if (p == null) return;
        if (a.length < 2) {
            Msg.send(p, "§cUsage: /core duel <player|@p>   then: accept | deny | quit");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "accept" -> Duel.accept(p);
            case "deny" -> Duel.deny(p);
            case "quit" -> Duel.forfeit(p);
            default -> {
                Player t = findPlayer(p, a[1]);
                if (t != null) Duel.challenge(p, t);
            }
        }
    }

    private void ac(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            Msg.send(s, "§cUsage: /core ac alerts | status <player>");
            return;
        }
        if (a[1].equalsIgnoreCase("alerts")) {
            Player p = asPlayer(s);
            if (p == null) return;
            Msg.send(p, "Anti-cheat alerts " + (AntiCheat.toggleAlerts(p.getUniqueId()) ? "§aON" : "§cOFF"));
        } else if (a[1].equalsIgnoreCase("status") && a.length > 2) {
            Player t = findPlayer(s, a[2]);
            if (t != null) Msg.send(s, t.getName() + " violations: §e" + AntiCheat.violations(t.getUniqueId()));
        }
    }

    private void extras(CommandSender s, String[] a) {
        if (!admin(s)) return;
        if (a.length < 2) {
            Msg.send(s, "§cUsage: /core extras toggle | trigger | strike <player>");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "toggle" -> {
                Store.unstableOn = !Store.unstableOn;
                Store.markDirty();
                Msg.send(s, "Unstable events " + (Store.unstableOn ? "§aON" : "§cOFF"));
            }
            case "trigger" -> {
                if (!Extras.trigger()) Msg.send(s, "§cNo players online.");
            }
            case "strike" -> {
                if (a.length < 3) {
                    Msg.send(s, "§cUsage: /core extras strike <player>");
                    return;
                }
                Player t = findPlayer(s, a[2]);
                if (t != null) Extras.strike(t);
            }
            default -> {
            }
        }
    }

    private void help(CommandSender s) {
        Msg.send(s, "§e/core kit <name> §7| §e/core kits §7- claim kits");
        s.sendMessage("§e/core quest §7- quests   §e/core army [summon|dismiss|gear] §7- your soldiers");
        s.sendMessage("§e/core duel <player> §7- challenge someone (then /core duel accept|deny|quit)");
        s.sendMessage("§e/core daily §7- daily reward   §e/core back §7- previous location / death spot");
        s.sendMessage("§e/core stats [player] §7| §e/core top <kills|pvp|deaths|mined|time|duels>");
        s.sendMessage("§e/core revive <name> §7- use a revive token on a deathbanned player");
        s.sendMessage("§e/core info §7- TPS & server stats");
        s.sendMessage("§7Also: /spawn /home /sethome /homes /delhome /warp /warps /tpa /tpaccept /tpdeny /rtp");
        s.sendMessage("§cAdmin: §7kit create|delete|setcooldown|addcmd|clearcmds|info, reload, chat, deathban pardon, ac, extras, army give, revive give");
    }

    private void info(CommandSender s) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1048576;
        long max = rt.maxMemory() / 1048576;
        double[] tps = Bukkit.getTPS();
        Msg.send(s, String.format(Locale.ROOT, "TPS §e%.1f§f | MSPT §e%.1f§f | Players §e%d§f | RAM §e%d/%d MB",
                Math.min(20.0, tps[0]), Bukkit.getAverageTickTime(), Bukkit.getOnlinePlayers().size(), used, max));
    }

    // ------------------------------------------------------------ tab completion
    private static List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase().startsWith(prefix.toLowerCase())) out.add(o);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        String n = c.getName().toLowerCase();
        if (n.equals("home") || n.equals("delhome")) {
            return s instanceof Player p && a.length == 1 ? filter(Store.homes(p.getUniqueId()).keySet(), a[0]) : List.of();
        }
        if (n.equals("warp") || n.equals("delwarp")) return a.length == 1 ? filter(Store.warps.keySet(), a[0]) : List.of();
        if (!n.equals("core")) return null; // player names for /tpa etc.
        if (a.length == 1) {
            return filter(List.of("help", "info", "kit", "kits", "quest", "army", "duel", "stats", "top", "daily", "back",
                    "revive", "chat", "deathban", "ac", "extras", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase();
        if (a.length == 2) {
            switch (sub) {
                case "kit": {
                    List<String> opts = new ArrayList<>(Store.kits.keySet());
                    opts.addAll(List.of("create", "delete", "setcooldown", "addcmd", "clearcmds", "info"));
                    return filter(opts, a[1]);
                }
                case "army": return filter(List.of("summon", "dismiss", "gear", "give"), a[1]);
                case "duel": {
                    List<String> opts = new ArrayList<>(List.of("accept", "deny", "quit", "@p"));
                    for (Player p : Bukkit.getOnlinePlayers()) opts.add(p.getName());
                    return filter(opts, a[1]);
                }
                case "top": return filter(List.of("kills", "pvp", "deaths", "mined", "time", "duels"), a[1]);
                case "chat": return filter(List.of("on", "off"), a[1]);
                case "deathban": return filter(List.of("pardon"), a[1]);
                case "ac": return filter(List.of("alerts", "status"), a[1]);
                case "extras": return filter(List.of("toggle", "trigger", "strike"), a[1]);
                case "revive": {
                    List<String> opts = new ArrayList<>(Deathban.names());
                    opts.add("give");
                    return filter(opts, a[1]);
                }
                case "stats": return null;
                default: return List.of();
            }
        }
        if (a.length == 3) {
            if (sub.equals("kit") && List.of("delete", "setcooldown", "addcmd", "clearcmds", "info").contains(a[1].toLowerCase()))
                return filter(Store.kits.keySet(), a[2]);
            if (sub.equals("deathban")) return filter(Deathban.names(), a[2]);
            if ((sub.equals("army") || sub.equals("revive")) && a[1].equalsIgnoreCase("give")) return null;
            if (sub.equals("ac") && a[1].equalsIgnoreCase("status")) return null;
            if (sub.equals("extras") && a[1].equalsIgnoreCase("strike")) return null;
        }
        return List.of();
    }
}
