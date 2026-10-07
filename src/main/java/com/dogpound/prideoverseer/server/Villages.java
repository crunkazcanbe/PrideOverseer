package com.dogpound.prideoverseer.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every settlement the overseer can see: Minecraft's own villages (doors, villagers, golems, your reputation there) and
 * groups of mod NPCs living together (MCA families, Millénaire, ToroQuest, Ancient Warfare camps, Custom NPCs towns),
 * found by where they stand. Each gets a stable, pretty name made from its position.
 */
public final class Villages {
    private Villages() {}

    private static final String[] A = { "Amber", "Bright", "Clover", "Dawn", "Elder", "Fern", "Gold", "Hollow", "Ivy", "Juniper", "Kestrel", "Lark",
            "Maple", "North", "Oak", "Pebble", "Quill", "Rose", "Sage", "Thistle", "Umber", "Violet", "Willow", "Yarrow", "Starling", "Honey", "Misty", "River" };
    private static final String[] B = { "brook", "ford", "vale", "wick", "haven", "stead", "field", "hollow", "moor", "cross", "dale", "mere", "shire",
            "glen", "bury", "ridge", "wood", "gate", "fall", "crest", "meadow", "well" };

    public static String name(BlockPos c) {
        long h = (c.getX() >> 4) * 341873128712L + (c.getZ() >> 4) * 132897987541L;
        h ^= (h >>> 17);
        return A[(int) Math.floorMod(h, (long) A.length)] + B[(int) Math.floorMod(h >>> 8, (long) B.length)];
    }

    public static NBTTagCompound list(EntityPlayerMP p) {
        WorldServer w = p.getServerWorld();
        NBTTagList out = new NBTTagList();
        List<BlockPos> vanillaCentres = new ArrayList<BlockPos>();
        double r2 = (double) ServerConfig.range * ServerConfig.range * 4;
        for (Village v : w.getVillageCollection().getVillageList()) {
            BlockPos c = v.getCenter();
            if (c.distanceSq(p.getPosition()) > r2) continue;
            vanillaCentres.add(c);
            NBTTagCompound t = new NBTTagCompound();
            t.setString("name", VillageLife.name(w, c));
            t.setString("kind", "village");
            t.setLong("pos", c.toLong());
            t.setInteger("radius", v.getVillageRadius());
            t.setInteger("doors", v.getNumVillageDoors());
            t.setInteger("villagers", v.getNumVillagers());
            t.setInteger("golems", w.getEntitiesWithinAABB(EntityIronGolem.class, new AxisAlignedBB(c).grow(v.getVillageRadius())).size());
            t.setInteger("rep", v.getPlayerReputation(p.getName()));
            t.setBoolean("annihilated", v.isAnnihilated());
            out.appendTag(t);
        }
        // groups of mod NPCs: 48-block cells, at least 2 NPCs, not inside a vanilla village
        Map<Long, List<Entity>> cells = new HashMap<Long, List<Entity>>();
        for (Entity e : w.loadedEntityList) {
            if (!Units.listed(p, e)) continue;
            String mod = Units.mod(e);
            if (mod.equals("minecraft")) continue;
            Units.Kind k = Units.kind(e);
            if (k == Units.Kind.ANIMAL || k == Units.Kind.PET || k == Units.Kind.MOUNT || k == Units.Kind.HOSTILE) continue;
            boolean inVillage = false;
            for (BlockPos c : vanillaCentres) if (c.distanceSq(e.getPosition()) < 64 * 64) { inVillage = true; break; }
            if (inVillage) continue;
            long key = ((long) Math.floorDiv((int) e.posX, 48) << 32) ^ (Math.floorDiv((int) e.posZ, 48) & 0xFFFFFFFFL);
            cells.computeIfAbsent(key, x -> new ArrayList<Entity>()).add(e);
        }
        for (List<Entity> l : cells.values()) {
            if (l.size() < 2) continue;
            double x = 0, y = 0, z = 0;
            Map<String, Integer> mods = new HashMap<String, Integer>();
            for (Entity e : l) { x += e.posX; y += e.posY; z += e.posZ; mods.merge(Units.mod(e), 1, Integer::sum); }
            String main = mods.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
            BlockPos c = new BlockPos(x / l.size(), y / l.size(), z / l.size());
            NBTTagCompound t = new NBTTagCompound();
            t.setString("name", name(c));
            t.setString("kind", main);
            t.setLong("pos", c.toLong());
            t.setInteger("radius", 24);
            t.setInteger("villagers", l.size());
            out.appendTag(t);
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("list", out);
        NBTTagList flags = new NBTTagList();
        for (BlockPos f : RallyFlags.get(w).flags) if (f.distanceSq(p.getPosition()) < r2) flags.appendTag(new net.minecraft.nbt.NBTTagLong(f.toLong()));
        t.setTag("flags", flags);
        return t;
    }
}
