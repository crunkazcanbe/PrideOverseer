package com.dogpound.prideoverseer.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the server told the overseer view: units (even ones too far for the client to see), villages, flags, messages. */
public final class ClientData {
    private ClientData() {}

    public static final class Unit {
        public int id; public String name = "", kind = "ANIMAL", mod = "", cls = "", job = "", order = "", owner = "";
        public double x, y, z; public float hp, max; public boolean child, trading;
        public BlockPos opos, opos2;
    }
    public static final class Village {
        public String name, kind; public BlockPos pos; public int radius, doors, villagers, golems, rep;
    }

    public static final Map<Integer, Unit> UNITS = new LinkedHashMap<Integer, Unit>();
    public static final List<Village> VILLAGES = new ArrayList<Village>();
    public static final List<BlockPos> FLAGS = new ArrayList<BlockPos>();
    public static final List<String[]> LOG = new ArrayList<String[]>();   // {text, time}
    public static int range = 256, maxUnits = 64;
    /** the village card that's open (null = none) */
    public static NBTTagCompound village;
    /** news from the villages: {text, time} newest last */
    public static final List<String[]> NEWS = new ArrayList<String[]>();
    /** what villagers sell, by entity id: {give, get, locked} */
    public static final Map<Integer, List<String[]>> TRADES = new java.util.HashMap<Integer, List<String[]>>();
    /** little ripples where orders were given: {x, y, z, startMillis} */
    public static final List<double[]> PINGS = new ArrayList<double[]>();

    public static void ping(BlockPos p) { PINGS.add(new double[]{ p.getX() + 0.5, p.getY(), p.getZ() + 0.5, System.currentTimeMillis() }); while (PINGS.size() > 20) PINGS.remove(0); }
    public static boolean creative, remoteTalk = true;
    public static long lastUnits;

    public static void receive(String kind, NBTTagCompound d) {
        switch (kind) {
            case "hello":
                range = d.getInteger("range"); maxUnits = d.getInteger("maxUnits"); creative = d.getBoolean("creative"); remoteTalk = d.getBoolean("remoteTalk");
                break;
            case "units": {
                UNITS.clear();
                NBTTagList l = d.getTagList("units", 10);
                for (int i = 0; i < l.tagCount(); i++) {
                    NBTTagCompound t = l.getCompoundTagAt(i);
                    Unit u = new Unit();
                    u.id = t.getInteger("id"); u.name = t.getString("name"); u.kind = t.getString("kind"); u.mod = t.getString("mod"); u.cls = t.getString("cls");
                    u.x = t.getDouble("x"); u.y = t.getDouble("y"); u.z = t.getDouble("z"); u.hp = t.getFloat("hp"); u.max = t.getFloat("max");
                    u.job = t.getString("job"); u.order = t.getString("order"); u.owner = t.getString("owner"); u.child = t.getBoolean("child"); u.trading = t.getBoolean("trading");
                    if (t.hasKey("opos")) { int[] a = t.getIntArray("opos"); u.opos = new BlockPos(a[0], a[1], a[2]); }
                    if (t.hasKey("opos2")) { int[] a = t.getIntArray("opos2"); u.opos2 = new BlockPos(a[0], a[1], a[2]); }
                    UNITS.put(u.id, u);
                }
                lastUnits = System.currentTimeMillis();
                Selection.prune();
                break;
            }
            case "villages": {
                VILLAGES.clear();
                NBTTagList l = d.getTagList("list", 10);
                for (int i = 0; i < l.tagCount(); i++) {
                    NBTTagCompound t = l.getCompoundTagAt(i);
                    Village v = new Village();
                    v.name = t.getString("name"); v.kind = t.getString("kind"); v.pos = BlockPos.fromLong(t.getLong("pos")); v.radius = t.getInteger("radius");
                    v.doors = t.getInteger("doors"); v.villagers = t.getInteger("villagers"); v.golems = t.getInteger("golems"); v.rep = t.getInteger("rep");
                    VILLAGES.add(v);
                }
                FLAGS.clear();
                NBTTagList f = d.getTagList("flags", 4);
                for (int i = 0; i < f.tagCount(); i++) FLAGS.add(BlockPos.fromLong(((NBTTagLong) f.get(i)).getLong()));
                break;
            }
            case "msg":
                log(d.getString("text"));
                break;
            case "village": village = d; break;
            case "news":
                NEWS.add(new String[]{ d.getString("text"), String.valueOf(System.currentTimeMillis()), String.valueOf(d.getLong("pos")) });
                while (NEWS.size() > 80) NEWS.remove(0);
                log(d.getString("text"));
                Sounds.play(net.minecraft.init.SoundEvents.BLOCK_NOTE_XYLOPHONE, 1.4F);
                break;
            case "trades": {
                List<String[]> l = new ArrayList<String[]>();
                NBTTagList t = d.getTagList("trades", 10);
                for (int i = 0; i < t.tagCount(); i++) { NBTTagCompound o = t.getCompoundTagAt(i); l.add(new String[]{ o.getString("give"), o.getString("get"), String.valueOf(o.getBoolean("locked")) }); }
                TRADES.put(d.getInteger("id"), l);
                break;
            }
            default: break;
        }
    }

    public static void log(String s) {
        LOG.add(new String[]{ s, String.valueOf(System.currentTimeMillis()) });
        while (LOG.size() > 40) LOG.remove(0);
    }
}
