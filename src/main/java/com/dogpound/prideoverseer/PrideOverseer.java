package com.dogpound.prideoverseer;

import com.dogpound.prideoverseer.content.OverseerContent;
import com.dogpound.prideoverseer.net.OverseerNet;
import com.dogpound.prideoverseer.server.OverseerCommand;
import com.dogpound.prideoverseer.server.Orders;
import com.dogpound.prideoverseer.server.ServerConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Pride Overseer (her idea 2026-10-05): rise above the world like a strategy game. Zoom out over your villages, click
 * the little villagers and NPCs running around and command them, with the same interactions you get up close.
 * Works with any creature that walks (villagers, golems, pets) and NPC mods (Ancient Warfare 2, Custom NPCs, MCA,
 * Millénaire, ToroQuest). Server-authoritative: the client only asks, the server checks and carries out orders.
 */
@Mod(modid = PrideOverseer.MODID, name = "Pride Overseer", version = "0.1.0", acceptableRemoteVersions = "*")
public class PrideOverseer {
    public static final String MODID = "prideoverseer";
    public static final Logger LOG = LogManager.getLogger("Pride Overseer");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ServerConfig.load(e.getModConfigurationDirectory());
        OverseerNet.init();
        MinecraftForge.EVENT_BUS.register(new OverseerContent());
        MinecraftForge.EVENT_BUS.register(new Orders.Events());
        MinecraftForge.EVENT_BUS.register(new com.dogpound.prideoverseer.server.VillageLife.Events());
        if (FMLCommonHandler.instance().getSide().isClient()) com.dogpound.prideoverseer.client.ClientSetup.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        if (FMLCommonHandler.instance().getSide().isClient()) com.dogpound.prideoverseer.client.ClientSetup.init();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent e) {
        e.registerServerCommand(new OverseerCommand());
    }
}
