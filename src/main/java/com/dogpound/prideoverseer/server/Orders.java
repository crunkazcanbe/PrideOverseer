package com.dogpound.prideoverseer.server;

import com.dogpound.prideoverseer.PrideOverseer;
import com.dogpound.prideoverseer.net.OverseerNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The orders a unit is carrying out. Stored in the creature's own saved data, so an order survives saving and
 * reloading, and carried out by {@link OrderTask}, which every creature gets as its most important AI task.
 */
public final class Orders {
    private Orders() {}

    public enum Type {
        MOVE,       // walk there and stay
        HOLD,       // stand still right here
        FOLLOW,     // follow a player
        GUARD,      // stay near a spot, fight anything hostile that comes close (if it can fight)
        PATROL,     // walk back and forth between two spots
        WANDER,     // roam freely around a spot
        HOME,       // go back to its village / home spot, then back to normal life
        COME        // walk to the player's body and wait there
    }

    public static final class Order {
        public Type type;
        public BlockPos pos, pos2;
        public UUID player;
        public int radius = 8;
        public long given;
        boolean leg;           // patrol: heading to pos2
        Order(Type t) { type = t; }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("type", type.name());
            if (pos != null) t.setLong("pos", pos.toLong());
            if (pos2 != null) t.setLong("pos2", pos2.toLong());
            if (player != null) t.setString("player", player.toString());
            t.setInteger("radius", radius);
            t.setLong("given", given);
            t.setBoolean("leg", leg);
            return t;
        }
        static Order read(NBTTagCompound t) {
            try {
                Order o = new Order(Type.valueOf(t.getString("type")));
                if (t.hasKey("pos")) o.pos = BlockPos.fromLong(t.getLong("pos"));
                if (t.hasKey("pos2")) o.pos2 = BlockPos.fromLong(t.getLong("pos2"));
                if (t.hasKey("player")) o.player = UUID.fromString(t.getString("player"));
                o.radius = t.getInteger("radius"); o.given = t.getLong("given"); o.leg = t.getBoolean("leg");
                return o;
            } catch (Throwable e) { return null; }
        }
    }

    private static final String KEY = "prideoverseer_order";
    private static final Map<Entity, Order> CACHE = new WeakHashMap<Entity, Order>();

    public static Order get(Entity e) {
        if (CACHE.containsKey(e)) return CACHE.get(e);
        NBTTagCompound d = e.getEntityData();
        Order o = d.hasKey(KEY) ? Order.read(d.getCompoundTag(KEY)) : null;
        CACHE.put(e, o);
        return o;
    }

    public static void set(Entity e, Order o) {
        CACHE.put(e, o);
        if (o == null) e.getEntityData().removeTag(KEY); else e.getEntityData().setTag(KEY, o.write());
        if (e instanceof EntityLiving && o != null) ((EntityLiving) e).getNavigator().clearPath();
    }

    static void save(Entity e, Order o) { if (o != null) e.getEntityData().setTag(KEY, o.write()); }

    // ------------------------------------------------------------------ who is watching from above

    static final class Viewer { double x, y, z; long lastSync; }
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<UUID, Viewer>();
    public static boolean overseeing(EntityPlayer p) { return p != null && VIEWERS.containsKey(p.getUniqueID()); }

    private static boolean allowed(EntityPlayerMP p) {
        if (ServerConfig.opOnly && !p.canUseCommand(2, "prideoverseer")) return false;
        return p.isCreative() || ServerConfig.survival;
    }

    private static void tell(EntityPlayerMP p, String s) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("text", s);
        OverseerNet.send(p, "msg", t);
    }

    // ------------------------------------------------------------------ commands from the client

    public static void handle(EntityPlayerMP p, String op, NBTTagCompound a) {
        if (op.equals("exit")) { VIEWERS.remove(p.getUniqueID()); return; }
        if (!allowed(p)) { tell(p, "§cThe overseer view isn't allowed for you here."); return; }
        WorldServer w = p.getServerWorld();
        switch (op) {
            case "enter": {
                VIEWERS.put(p.getUniqueID(), new Viewer());
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger("range", ServerConfig.range);
                t.setInteger("maxUnits", ServerConfig.maxUnits);
                t.setBoolean("creative", p.isCreative() && ServerConfig.creativeTools);
                t.setBoolean("remoteTalk", ServerConfig.remoteTalk);
                OverseerNet.send(p, "hello", t);
                OverseerNet.send(p, "villages", Villages.list(p));
                break;
            }
            case "view": {
                Viewer v = VIEWERS.computeIfAbsent(p.getUniqueID(), k -> new Viewer());
                v.x = a.getDouble("x"); v.y = a.getDouble("y"); v.z = a.getDouble("z");
                break;
            }
            case "villages": OverseerNet.send(p, "villages", Villages.list(p)); break;
            case "order": order(p, w, a); break;
            case "act": act(p, w, a); break;
            case "talk": talk(p, w, a.getInteger("id")); break;
            case "vdetail": OverseerNet.send(p, "village", VillageLife.detail(p, BlockPos.fromLong(a.getLong("pos")))); break;
            case "vcmd": VillageLife.command(p, BlockPos.fromLong(a.getLong("pos")), a.getString("what"), a.getString("arg")); break;
            case "trades": OverseerNet.send(p, "trades", VillageLife.trades(p, a.getInteger("id"))); break;
            default: PrideOverseer.LOG.debug("unknown op {}", op);
        }
    }

    private static List<EntityLiving> units(EntityPlayerMP p, WorldServer w, int[] ids) {
        List<EntityLiving> l = new ArrayList<EntityLiving>();
        for (int id : ids) {
            Entity e = w.getEntityByID(id);
            if (e instanceof EntityLiving && Units.canCommand(p, e)) l.add((EntityLiving) e);
            if (l.size() >= ServerConfig.maxUnits) break;
        }
        return l;
    }

    /** where unit i of n stands around the target, in the chosen formation */
    static BlockPos slot(BlockPos c, int i, int n, String formation, float facing) {
        if (n <= 1 || i == 0 && !"circle".equals(formation)) return c;
        double dx, dz;
        switch (formation) {
            case "line": { int k = i - n / 2; dx = k * 1.6; dz = 0; break; }
            case "column": { dx = 0; dz = i * 1.6; break; }
            case "circle": { double a = i * Math.PI * 2 / n, r = Math.max(2.0, n * 0.45); dx = Math.cos(a) * r; dz = Math.sin(a) * r; break; }
            case "wedge": { int row = (int) Math.ceil((Math.sqrt(8 * i + 1) - 1) / 2), k = i - row * (row - 1) / 2; dx = (k - (row - 1) / 2.0) * 1.6; dz = row * 1.6; break; }
            case "loose": { long h = c.toLong() * 31 + i * 7919L; dx = ((h >> 3) & 15) / 2.0 - 4; dz = ((h >> 9) & 15) / 2.0 - 4; break; }
            default: { int side = (int) Math.ceil(Math.sqrt(n)); dx = (i % side - (side - 1) / 2.0) * 1.6; dz = (i / side - (side - 1) / 2.0) * 1.6; }   // box
        }
        double r = Math.toRadians(facing), cos = Math.cos(r), sin = Math.sin(r);
        return c.add(Math.round(dx * cos - dz * sin), 0, Math.round(dx * sin + dz * cos));
    }

    private static void order(EntityPlayerMP p, WorldServer w, NBTTagCompound a) {
        Type type;
        try { type = Type.valueOf(a.getString("type")); } catch (Throwable t) { return; }
        List<EntityLiving> l = units(p, w, a.getIntArray("ids"));
        BlockPos pos = a.hasKey("pos") ? BlockPos.fromLong(a.getLong("pos")) : null, pos2 = a.hasKey("pos2") ? BlockPos.fromLong(a.getLong("pos2")) : null;
        String formation = a.hasKey("formation") ? a.getString("formation") : "box";
        float facing = a.getFloat("facing");
        int radius = a.hasKey("radius") ? MathHelper.clamp(a.getInteger("radius"), 2, 64) : 8;
        for (int i = 0; i < l.size(); i++) {
            EntityLiving e = l.get(i);
            Order o = new Order(type);
            o.player = p.getUniqueID();
            o.given = w.getTotalWorldTime();
            o.radius = radius;
            switch (type) {
                case MOVE: case GUARD: case WANDER: o.pos = pos == null ? null : slot(pos, i, l.size(), formation, facing); break;
                case PATROL: o.pos = pos == null ? e.getPosition() : slot(pos, i, l.size(), formation, facing); o.pos2 = pos2 == null ? e.getPosition() : slot(pos2, i, l.size(), formation, facing); break;
                case HOLD: o.pos = e.getPosition(); break;
                case HOME: o.pos = home(e); break;
                case COME: o.pos = slot(p.getPosition(), i + 1, l.size() + 1, "circle", 0); break;
                default: break;
            }
            if ((type == Type.MOVE || type == Type.GUARD || type == Type.WANDER) && o.pos == null) continue;
            set(e, o);
        }
        tell(p, "§d✦ §f" + l.size() + " §7unit" + (l.size() == 1 ? "" : "s") + " → §f" + label(type));
    }

    static String label(Type t) {
        switch (t) {
            case MOVE: return "move there"; case HOLD: return "hold position"; case FOLLOW: return "follow you"; case GUARD: return "guard the area";
            case PATROL: return "patrol"; case WANDER: return "wander there"; case HOME: return "go home"; case COME: return "come to you";
            default: return t.name().toLowerCase();
        }
    }

    /** its village centre (vanilla villagers), else where it was first seen, else where it is */
    static BlockPos home(EntityLiving e) {
        if (e instanceof EntityVillager) {
            net.minecraft.village.Village v = e.world.getVillageCollection().getNearestVillage(e.getPosition(), 128);
            if (v != null) return v.getCenter();
        }
        if (e instanceof net.minecraft.entity.EntityCreature && ((net.minecraft.entity.EntityCreature) e).hasHome()) return ((net.minecraft.entity.EntityCreature) e).getHomePosition();
        NBTTagCompound d = e.getEntityData();
        if (d.hasKey("prideoverseer_home")) return BlockPos.fromLong(d.getLong("prideoverseer_home"));
        return e.getPosition();
    }

    private static void act(EntityPlayerMP p, WorldServer w, NBTTagCompound a) {
        String what = a.getString("action");
        List<EntityLiving> l = units(p, w, a.getIntArray("ids"));
        boolean creative = p.isCreative() && ServerConfig.creativeTools;
        int done = 0;
        for (EntityLiving e : l) {
            switch (what) {
                case "stop": set(e, null); done++; break;                        // back to its normal life
                case "rally": {
                    BlockPos f = RallyFlags.get(w).nearest(e.getPosition());
                    if (f == null) { tell(p, "§7No rally flag yet: craft one and plant it."); return; }
                    Order o = new Order(Type.MOVE); o.player = p.getUniqueID(); o.given = w.getTotalWorldTime();
                    o.pos = slot(f, l.indexOf(e) + 1, l.size() + 1, "circle", 0);
                    set(e, o); done++; break;
                }
                case "glow": e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, 200, 0, false, false)); done++; break;
                case "rename": {
                    String n = a.getString("name").trim();
                    if (n.length() > 32) n = n.substring(0, 32);
                    if (n.isEmpty()) e.setCustomNameTag(""); else e.setCustomNameTag(n);
                    e.enablePersistence(); done++; break;
                }
                case "heal": if (creative) { e.setHealth(e.getMaxHealth()); e.extinguish(); done++; } break;
                case "tp": if (creative && a.hasKey("pos")) { BlockPos b = BlockPos.fromLong(a.getLong("pos")); e.setPositionAndUpdate(b.getX() + 0.5, b.getY(), b.getZ() + 0.5); done++; } break;
                case "tpme": if (creative) { e.setPositionAndUpdate(p.posX, p.posY, p.posZ); done++; } break;
                case "persist": e.enablePersistence(); done++; break;
                case "sethome": e.getEntityData().setLong("prideoverseer_home", a.hasKey("pos") ? a.getLong("pos") : e.getPosition().toLong()); done++; break;
                default: break;
            }
        }
        if (done > 0) tell(p, "§d✦ §7" + what + " §f×" + done);
        else if (!creative && (what.equals("heal") || what.startsWith("tp"))) tell(p, "§7That one needs creative mode.");
    }

    /** right-click a unit from above: same as walking up to it (trade, MCA menu, Custom NPCs dialog, AW2 orders...) */
    private static void talk(EntityPlayerMP p, WorldServer w, int id) {
        Entity e = w.getEntityByID(id);
        if (!(e instanceof EntityLiving) || !Units.canCommand(p, e)) return;
        if (!ServerConfig.remoteTalk && e.getDistanceSq(p) > 36) { tell(p, "§7Get closer to talk (remote talking is off)."); return; }
        EnumHand hand = EnumHand.MAIN_HAND;
        boolean ok = false;
        try {
            ok = e.processInitialInteract(p, hand);
            if (!ok) ok = net.minecraftforge.common.ForgeHooks.onInteractEntity(p, e, hand) != null;
        } catch (Throwable t) { PrideOverseer.LOG.warn("talking to {} failed", e, t); }
        if (!ok) tell(p, "§7" + Units.name(e) + " §7has nothing to say right now.");
    }

    // ------------------------------------------------------------------ hooks

    public static final class Events {
        /** every creature gets the order task (most important) and villagers the remote-trade fix */
        @SubscribeEvent
        public void join(EntityJoinWorldEvent e) {
            if (e.getWorld().isRemote || !(e.getEntity() instanceof EntityLiving)) return;
            EntityLiving l = (EntityLiving) e.getEntity();
            boolean has = false;
            for (EntityAITasks.EntityAITaskEntry t : l.tasks.taskEntries) if (t.action instanceof OrderTask) { has = true; break; }
            if (!has) l.tasks.addTask(-1, new OrderTask(l));
            if (l instanceof EntityVillager) RemoteTrade.install((EntityVillager) l);
            if (!l.getEntityData().hasKey("prideoverseer_home")) l.getEntityData().setLong("prideoverseer_home", l.getPosition().toLong());
        }

        @SubscribeEvent
        public void logout(PlayerEvent.PlayerLoggedOutEvent e) { VIEWERS.remove(e.player.getUniqueID()); }

        @SubscribeEvent
        public void changedDim(PlayerEvent.PlayerChangedDimensionEvent e) { VIEWERS.remove(e.player.getUniqueID()); }

        /** fresh unit positions for everyone watching from above */
        @SubscribeEvent
        public void tick(TickEvent.PlayerTickEvent e) {
            if (e.phase != TickEvent.Phase.END || e.player.world.isRemote) return;
            Viewer v = VIEWERS.get(e.player.getUniqueID());
            if (v == null) return;
            long now = e.player.world.getTotalWorldTime();
            if (now - v.lastSync < ServerConfig.syncTicks) return;
            v.lastSync = now;
            EntityPlayerMP p = (EntityPlayerMP) e.player;
            NBTTagList list = new NBTTagList();
            double r = ServerConfig.range;
            for (Entity en : p.getServerWorld().loadedEntityList) {
                if (!(en instanceof EntityLivingBase) || !Units.listed(p, en)) continue;
                if (en.getDistanceSq(p) > r * r) continue;
                list.appendTag(Units.card(en));
                if (list.tagCount() >= 600) break;
            }
            NBTTagCompound t = new NBTTagCompound();
            t.setTag("units", list);
            t.setLong("time", now);
            OverseerNet.send(p, "units", t);
            if (now % 200 == 0) OverseerNet.send(p, "villages", Villages.list(p));
        }
    }

    public static void say(EntityPlayer p, String s) { p.sendStatusMessage(new TextComponentString(s), true); }
    static int[] ints(NBTTagIntArray a) { return a.getIntArray(); }
}
