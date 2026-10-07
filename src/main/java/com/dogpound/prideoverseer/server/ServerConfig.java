package com.dogpound.prideoverseer.server;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** What the overseer may do on this world/server: config/prideoverseer-server.cfg */
public final class ServerConfig {
    private ServerConfig() {}

    public static boolean survival = true;              // use it outside creative
    public static boolean opOnly = false;                // only operators (servers)
    public static int range = 256;                       // how far from your body you can see and command
    public static int maxUnits = 64;                     // most units in one order
    public static boolean hostiles = false;              // command hostile mobs too
    public static boolean othersPets = false;            // command other players' pets
    public static boolean remoteTalk = true;             // trade/talk with NPCs far from your body
    public static boolean creativeTools = true;          // heal / teleport / glow units in creative
    public static boolean followTeleport = true;         // followers teleport to you when far behind
    public static int syncTicks = 10;                    // how often the view gets fresh unit positions

    public static void load(File dir) {
        Configuration c = new Configuration(new File(dir, "prideoverseer-server.cfg"));
        survival = c.getBoolean("survival", "rules", survival, "Use the overseer view outside creative");
        opOnly = c.getBoolean("opOnly", "rules", opOnly, "Only server operators may use it");
        range = c.getInt("range", "rules", range, 32, 2048, "How far from your body (blocks) you can see and command");
        maxUnits = c.getInt("maxUnits", "rules", maxUnits, 1, 1024, "Most units in one order");
        hostiles = c.getBoolean("hostiles", "rules", hostiles, "Command hostile mobs too");
        othersPets = c.getBoolean("othersPets", "rules", othersPets, "Command other players' pets");
        remoteTalk = c.getBoolean("remoteTalk", "rules", remoteTalk, "Trade and talk with NPCs far from your body");
        creativeTools = c.getBoolean("creativeTools", "rules", creativeTools, "Heal, teleport and glow units in creative");
        followTeleport = c.getBoolean("followTeleport", "orders", followTeleport, "Followers teleport to you when far behind");
        syncTicks = c.getInt("syncTicks", "performance", syncTicks, 2, 100, "Ticks between unit updates for the view");
        if (c.hasChanged()) c.save();
    }
}
