package net.coresmp;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class Msg {
    static final String PREFIX = "§6[Core] §f";

    private Msg() {}

    static void send(CommandSender s, String text) {
        s.sendMessage(PREFIX + text);
    }

    static void bar(Player p, String text) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }
}
