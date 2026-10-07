package com.dogpound.prideoverseer.server;

import com.dogpound.prideoverseer.net.OverseerNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.village.Village;
import net.minecraft.village.VillageDoorInfo;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Village life for the overseer: whole-village commands (gather, curfew, alarm, festival, back to normal), names you
 * give villages, the village detail card (people by job, houses, golems, reputation), what a villager sells, and the
 * news feed (births, deaths, monsters at the gates) for everyone watching from above.
 */
public final class VillageLife {
    private VillageLife() {}

    // ------------------------------------------------------------------ names you gave villages (saved with the world)

    public static class Names extends WorldSavedData {
        static final String ID = "prideoverseer_names";
        final Map<Long, String> names = new HashMap<Long, String>();
        public Names() { super(ID); }
        public Names(String n) { super(n); }
        static Names get(net.minecraft.world.World w) {
            Names d = (Names) w.getPerWorldStorage().getOrLoadData(Names.class, ID);
            if (d == null) { d = new Names(); w.getPerWorldStorage().setData(ID, d); }
            return d;
        }
        @Override public void readFromNBT(NBTTagCompound t) { names.clear(); for (String k : t.getKeySet()) names.put(Long.parseLong(k), t.getString(k)); }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { for (Map.Entry<Long, String> e : names.entrySet()) t.setString(String.valueOf(e.getKey()), e.getValue()); return t; }
    }

    /** a village keeps its name while its centre drifts a little: names are kept per 32-block cell */
    static long key(BlockPos c) { return ((long) (c.getX() >> 5) << 32) ^ ((c.getZ() >> 5) & 0xFFFFFFFFL); }

    public static String name(net.minecraft.world.World w, BlockPos c) {
        String n = Names.get(w).names.get(key(c));
        return n != null ? n : Villages.name(c);
    }

    // ------------------------------------------------------------------ who lives where

    static Village village(WorldServer w, BlockPos near) { return w.getVillageCollection().getNearestVillage(near, 32); }

    static List<EntityLiving> people(WorldServer w, BlockPos c, int radius) {
        List<EntityLiving> l = new ArrayList<EntityLiving>();
        for (Entity e : w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(c).grow(radius + 8, 24, radius + 8))) {
            Units.Kind k = Units.kind(e);
            if (k == Units.Kind.VILLAGER || k == Units.Kind.NPC || k == Units.Kind.GUARD || k == Units.Kind.WORKER) l.add((EntityLiving) e);
        }
        return l;
    }

    static List<EntityIronGolem> golems(WorldServer w, BlockPos c, int radius) {
        return w.getEntitiesWithinAABB(EntityIronGolem.class, new AxisAlignedBB(c).grow(radius + 8, 24, radius + 8));
    }

    /** the village card: people by job, children, houses, golems, your reputation, a mood score */
    static NBTTagCompound detail(EntityPlayerMP p, BlockPos at) {
        WorldServer w = p.getServerWorld();
        Village v = village(w, at);
        BlockPos c = v != null ? v.getCenter() : at;
        int radius = v != null ? v.getVillageRadius() : 24;
        NBTTagCompound t = new NBTTagCompound();
        t.setLong("pos", c.toLong());
        t.setString("name", name(w, c));
        t.setInteger("radius", radius);
        Map<String, Integer> jobs = new HashMap<String, Integer>();
        int kids = 0;
        List<EntityLiving> people = people(w, c, radius);
        for (EntityLiving e : people) {
            String job = Units.kind(e).name().toLowerCase();
            if (e instanceof EntityVillager) {
                try { job = ((EntityVillager) e).getProfessionForge().getRegistryName().getResourcePath(); } catch (Throwable ignored) { }
                if (((EntityVillager) e).isChild()) { kids++; job = "child"; }
            }
            jobs.merge(job, 1, Integer::sum);
        }
        NBTTagCompound j = new NBTTagCompound();
        for (Map.Entry<String, Integer> e : jobs.entrySet()) j.setInteger(e.getKey(), e.getValue());
        t.setTag("jobs", j);
        t.setInteger("people", people.size());
        t.setInteger("kids", kids);
        int golems = golems(w, c, radius).size();
        t.setInteger("golems", golems);
        int doors = v != null ? v.getNumVillageDoors() : 0, rep = v != null ? v.getPlayerReputation(p.getName()) : 0;
        t.setInteger("doors", doors);
        t.setInteger("rep", rep);
        int monsters = w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(c).grow(radius + 16, 16, radius + 16), x -> x instanceof IMob).size();
        t.setInteger("monsters", monsters);
        // mood 0..100: room to live, safety, how much they like you
        float room = people.isEmpty() ? 1 : Math.min(1F, doors / (float) Math.max(1, people.size()) * 1.2F);
        float safety = Math.min(1F, (golems + 1) / (people.size() / 10F + 1F)) * (monsters > 0 ? 0.5F : 1F);
        float like = Math.max(0, Math.min(1F, (rep + 15) / 30F));
        t.setInteger("mood", Math.round((room * 0.35F + safety * 0.35F + like * 0.3F) * 100));
        return t;
    }

    /** what a villager sells: up to 10 "give -> get" lines */
    static NBTTagCompound trades(EntityPlayerMP p, int id) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id);
        Entity e = p.getServerWorld().getEntityByID(id);
        NBTTagList l = new NBTTagList();
        if (e instanceof EntityVillager) {
            MerchantRecipeList r = ((EntityVillager) e).getRecipes(p);
            if (r != null) for (int i = 0; i < Math.min(10, r.size()); i++) {
                MerchantRecipe m = r.get(i);
                String give = stack(m.getItemToBuy()) + (m.hasSecondItemToBuy() ? " + " + stack(m.getSecondItemToBuy()) : "");
                NBTTagCompound o = new NBTTagCompound();
                o.setString("give", give);
                o.setString("get", stack(m.getItemToSell()));
                o.setBoolean("locked", m.isRecipeDisabled());
                l.appendTag(o);
            }
        }
        t.setTag("trades", l);
        return t;
    }

    private static String stack(ItemStack s) { return s.isEmpty() ? "" : (s.getCount() > 1 ? s.getCount() + " " : "") + s.getDisplayName(); }

    // ------------------------------------------------------------------ whole-village commands

    static final Map<Long, Integer> FESTIVALS = new HashMap<Long, Integer>();      // village key -> ticks left

    static void command(EntityPlayerMP p, BlockPos at, String what, String arg) {
        WorldServer w = p.getServerWorld();
        Village v = village(w, at);
        BlockPos c = v != null ? v.getCenter() : at;
        int radius = v != null ? v.getVillageRadius() : 24;
        String vname = name(w, c);
        List<EntityLiving> people = people(w, c, radius);
        int n = 0;
        switch (what) {
            case "gather": case "festival": {
                for (int i = 0; i < people.size(); i++) {
                    Orders.Order o = new Orders.Order(Orders.Type.MOVE);
                    o.player = p.getUniqueID(); o.given = w.getTotalWorldTime();
                    o.pos = Orders.slot(c, i + 1, people.size() + 1, "circle", 0);
                    Orders.set(people.get(i), o); n++;
                }
                if (what.equals("festival")) {
                    FESTIVALS.put(key(c), 20 * 40);
                    news(w, c, "§d🎉 §fA festival begins in §d" + vname + "§f! Everyone gathers in the square.");
                } else news(w, c, "§e🔔 §fThe people of §d" + vname + " §fgather in the square.");
                break;
            }
            case "curfew": case "alarm": {
                List<VillageDoorInfo> doors = v != null ? v.getVillageDoorInfoList() : new ArrayList<VillageDoorInfo>();
                for (int i = 0; i < people.size(); i++) {
                    Orders.Order o = new Orders.Order(Orders.Type.MOVE);
                    o.player = p.getUniqueID(); o.given = w.getTotalWorldTime();
                    o.pos = doors.isEmpty() ? c : doors.get(i % doors.size()).getInsideBlockPos();
                    Orders.set(people.get(i), o); n++;
                }
                if (what.equals("alarm")) {
                    for (EntityIronGolem g : golems(w, c, radius)) {
                        Orders.Order o = new Orders.Order(Orders.Type.GUARD);
                        o.player = p.getUniqueID(); o.pos = c; o.radius = Math.max(12, radius);
                        Orders.set(g, o);
                    }
                    w.playSound(null, c, SoundEvents.BLOCK_NOTE_BELL, SoundCategory.BLOCKS, 3F, 0.6F);
                    news(w, c, "§c⚠ §fThe alarm rings in §d" + vname + "§f! Villagers hide, golems stand guard.");
                } else news(w, c, "§9☾ §fCurfew in §d" + vname + "§f: everyone heads indoors.");
                break;
            }
            case "free": {
                for (EntityLiving e : people) { Orders.set(e, null); n++; }
                for (EntityIronGolem g : golems(w, c, radius)) Orders.set(g, null);
                FESTIVALS.remove(key(c));
                news(w, c, "§a✿ §fLife in §d" + vname + " §fgoes back to normal.");
                break;
            }
            case "rename": {
                String nm = arg == null ? "" : arg.trim();
                if (nm.length() > 24) nm = nm.substring(0, 24);
                Names d = Names.get(w);
                if (nm.isEmpty()) d.names.remove(key(c)); else d.names.put(key(c), nm);
                d.markDirty();
                news(w, c, "§d⚑ §fThe village is now called §d" + name(w, c) + "§f.");
                break;
            }
            default: return;
        }
        OverseerNet.send(p, "village", detail(p, c));
        OverseerNet.send(p, "villages", Villages.list(p));
    }

    // ------------------------------------------------------------------ news for everyone watching from above

    static void news(WorldServer w, BlockPos c, String text) {
        for (EntityPlayerMP p : w.getMinecraftServer().getPlayerList().getPlayers()) {
            if (!Orders.overseeing(p) || p.world != w || p.getDistanceSq(c) > (double) ServerConfig.range * ServerConfig.range * 4) continue;
            NBTTagCompound t = new NBTTagCompound();
            t.setString("text", text);
            t.setLong("pos", c.toLong());
            OverseerNet.send(p, "news", t);
        }
    }

    public static final class Events {
        private final Random rnd = new Random();
        private long lastSiegeCheck;

        @SubscribeEvent
        public void born(EntityJoinWorldEvent e) {
            if (e.getWorld().isRemote || !(e.getEntity() instanceof EntityVillager) || !((EntityVillager) e.getEntity()).isChild()) return;
            if (e.getEntity().ticksExisted > 5 || e.getEntity().getEntityData().getBoolean("prideoverseer_seen")) return;
            e.getEntity().getEntityData().setBoolean("prideoverseer_seen", true);
            WorldServer w = (WorldServer) e.getWorld();
            Village v = village(w, e.getEntity().getPosition());
            if (v != null) news(w, v.getCenter(), "§b👶 §fA baby was born in §d" + name(w, v.getCenter()) + "§f!");
        }

        @SubscribeEvent
        public void died(LivingDeathEvent e) {
            if (e.getEntity().world.isRemote) return;
            Entity d = e.getEntity();
            if (!(d instanceof EntityVillager) && !(d instanceof EntityIronGolem) && Units.kind(d) != Units.Kind.NPC) return;
            WorldServer w = (WorldServer) d.world;
            Village v = village(w, d.getPosition());
            BlockPos c = v != null ? v.getCenter() : d.getPosition();
            String where = v != null ? " in §d" + name(w, c) : "";
            String who = Units.name(d);
            String cause = e.getSource().getTrueSource() != null ? " §7(by " + e.getSource().getTrueSource().getName() + ")" : "";
            news(w, c, (d instanceof EntityIronGolem ? "§7⬛ §f" : "§c✝ §f") + who + " died" + where + cause);
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
            WorldServer w = (WorldServer) e.world;
            long now = w.getTotalWorldTime();
            // festivals: fireworks, hearts and music in the square
            if (!FESTIVALS.isEmpty() && now % 10 == 0) {
                for (Map.Entry<Long, Integer> f : new ArrayList<Map.Entry<Long, Integer>>(FESTIVALS.entrySet())) {
                    int left = f.getValue() - 10;
                    if (left <= 0) { FESTIVALS.remove(f.getKey()); continue; }
                    FESTIVALS.put(f.getKey(), left);
                    for (Village v : w.getVillageCollection().getVillageList()) {
                        if (key(v.getCenter()) != f.getKey()) continue;
                        BlockPos c = v.getCenter();
                        BlockPos top = w.getHeight(c);
                        w.spawnParticle(EnumParticleTypes.HEART, c.getX() + 0.5, top.getY() + 1.5, c.getZ() + 0.5, 6, 3, 1, 3, 0.02);
                        w.spawnParticle(EnumParticleTypes.NOTE, c.getX() + 0.5, top.getY() + 2.5, c.getZ() + 0.5, 3, 2.0, 1.0, 2.0, 1.0);
                        if (now % 20 == 0) w.playSound(null, c, SoundEvents.BLOCK_NOTE_HARP, SoundCategory.RECORDS, 1.5F, 0.5F + rnd.nextInt(12) / 8F);
                        if (now % 40 == 0) firework(w, top.add(rnd.nextInt(9) - 4, 1, rnd.nextInt(9) - 4));
                        for (EntityLiving p : people(w, c, v.getVillageRadius())) if (rnd.nextInt(20) == 0 && p.onGround) p.getJumpHelper().setJumping();
                    }
                }
            }
            // monsters at the gates (checked every 15 s)
            if (now - lastSiegeCheck > 300) {
                lastSiegeCheck = now;
                for (Village v : w.getVillageCollection().getVillageList()) {
                    BlockPos c = v.getCenter();
                    int m = w.getEntitiesWithinAABB(EntityLiving.class, new AxisAlignedBB(c).grow(v.getVillageRadius() + 8, 16, v.getVillageRadius() + 8), x -> x instanceof IMob).size();
                    if (m >= 3) news(w, c, "§c☠ §f" + m + " monsters are at the gates of §d" + name(w, c) + "§f!");
                }
            }
        }

        private void firework(WorldServer w, BlockPos at) {
            ItemStack s = new ItemStack(Items.FIREWORKS);
            NBTTagCompound ex = new NBTTagCompound();
            int[][] pride = { { 0xE40303, 0xFF8C00, 0xFFED00 }, { 0x008026, 0x24408E, 0x732982 }, { 0x5BCEFA, 0xF5A9B8, 0xFFFFFF } };
            ex.setIntArray("Colors", pride[rnd.nextInt(pride.length)]);
            ex.setByte("Type", (byte) rnd.nextInt(5));
            ex.setBoolean("Flicker", rnd.nextBoolean());
            ex.setBoolean("Trail", true);
            NBTTagList list = new NBTTagList(); list.appendTag(ex);
            NBTTagCompound fw = new NBTTagCompound(); fw.setTag("Explosions", list); fw.setByte("Flight", (byte) 1);
            NBTTagCompound tag = new NBTTagCompound(); tag.setTag("Fireworks", fw);
            s.setTagCompound(tag);
            w.spawnEntity(new EntityFireworkRocket(w, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, s));
        }
    }
}
