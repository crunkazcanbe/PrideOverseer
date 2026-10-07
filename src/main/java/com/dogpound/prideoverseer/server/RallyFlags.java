package com.dogpound.prideoverseer.server;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashSet;
import java.util.Set;

/** Where the rally flags stand in this dimension (saved with the world). */
public class RallyFlags extends WorldSavedData {
    private static final String ID = "prideoverseer_rally";
    public final Set<BlockPos> flags = new LinkedHashSet<BlockPos>();

    public RallyFlags() { super(ID); }
    public RallyFlags(String n) { super(n); }

    public static RallyFlags get(World w) {
        RallyFlags d = (RallyFlags) w.getPerWorldStorage().getOrLoadData(RallyFlags.class, ID);
        if (d == null) { d = new RallyFlags(); w.getPerWorldStorage().setData(ID, d); }
        return d;
    }

    public void add(BlockPos p) { if (flags.add(p)) markDirty(); }
    public void remove(BlockPos p) { if (flags.remove(p)) markDirty(); }

    public BlockPos nearest(BlockPos from) {
        BlockPos best = null; double bd = Double.MAX_VALUE;
        for (BlockPos p : flags) { double d = p.distanceSq(from); if (d < bd) { bd = d; best = p; } }
        return best;
    }

    @Override public void readFromNBT(NBTTagCompound t) {
        flags.clear();
        NBTTagList l = t.getTagList("flags", 4);
        for (int i = 0; i < l.tagCount(); i++) flags.add(BlockPos.fromLong(((NBTTagLong) l.get(i)).getLong()));
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (BlockPos p : flags) l.appendTag(new NBTTagLong(p.toLong()));
        t.setTag("flags", l);
        return t;
    }
}
