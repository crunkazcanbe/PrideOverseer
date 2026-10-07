package com.dogpound.prideoverseer.content;

import com.dogpound.prideoverseer.PrideOverseer;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Pride Overseer's things: the Command Baton, the War Table and the Rally Flag (Blockbench models). */
public class OverseerContent {
    public static final CreativeTabs TAB = new CreativeTabs("prideoverseer") {
        @Override public ItemStack getTabIconItem() { return new ItemStack(BATON); }
    };

    public static Item BATON;
    public static Block WAR_TABLE, RALLY_FLAG;

    @SubscribeEvent
    public void blocks(RegistryEvent.Register<Block> e) {
        WAR_TABLE = new BlockWarTable();
        RALLY_FLAG = new BlockRallyFlag();
        e.getRegistry().registerAll(WAR_TABLE, RALLY_FLAG);
    }

    @SubscribeEvent
    public void items(RegistryEvent.Register<Item> e) {
        BATON = new ItemCommandBaton();
        e.getRegistry().register(BATON);
        e.getRegistry().register(new ItemBlock(WAR_TABLE).setRegistryName(WAR_TABLE.getRegistryName()));
        e.getRegistry().register(new ItemBlock(RALLY_FLAG).setRegistryName(RALLY_FLAG.getRegistryName()));
    }

    static <T extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<?>> T named(T t, String id) {
        t.setRegistryName(PrideOverseer.MODID, id);
        return t;
    }
}
