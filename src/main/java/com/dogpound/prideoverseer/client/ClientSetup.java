package com.dogpound.prideoverseer.client;

import com.dogpound.prideoverseer.content.OverseerContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Client side: the O key, the item/block models, and the overseer camera + drawing. */
public final class ClientSetup {
    private ClientSetup() {}

    public static final KeyBinding KEY_OPEN = new KeyBinding("Pride Overseer: rise above / come back", Keyboard.KEY_O, "Pride Overseer");

    public static void preInit() {
        MinecraftForge.EVENT_BUS.register(new ClientSetup.Models());
    }

    public static void init() {
        ClientRegistry.registerKeyBinding(KEY_OPEN);
        MinecraftForge.EVENT_BUS.register(new OverseerMode.Events());
        MinecraftForge.EVENT_BUS.register(new OverseerRender());
        MinecraftForge.EVENT_BUS.register(new ClientSetup.Keys());
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(new OverseeCommand());
    }

    /** the baton, the war table and the key all come here */
    public static void openOverseer(BlockPos at) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> { if (!OverseerMode.active()) OverseerMode.enter(at); });
    }

    public static final class Keys {
        @SubscribeEvent
        public void key(InputEvent.KeyInputEvent e) {
            if (KEY_OPEN.isPressed() && Minecraft.getMinecraft().currentScreen == null) {
                if (OverseerMode.active()) OverseerMode.exit(); else OverseerMode.enter(null);
            }
        }
    }

    public static final class Models {
        @SubscribeEvent
        public void models(ModelRegistryEvent e) {
            ModelLoader.setCustomModelResourceLocation(OverseerContent.BATON, 0, new ModelResourceLocation(OverseerContent.BATON.getRegistryName(), "inventory"));
            for (net.minecraft.block.Block b : new net.minecraft.block.Block[]{ OverseerContent.WAR_TABLE, OverseerContent.RALLY_FLAG })
                ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0, new ModelResourceLocation(b.getRegistryName(), "inventory"));
        }
    }
}
