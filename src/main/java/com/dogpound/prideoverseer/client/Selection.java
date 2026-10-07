package com.dogpound.prideoverseer.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The units you have selected, plus control groups 1-9 (Ctrl+number saves, number recalls, like strategy games). */
public final class Selection {
    private Selection() {}

    public static final Set<Integer> IDS = new LinkedHashSet<Integer>();
    @SuppressWarnings("unchecked")
    public static final Set<Integer>[] GROUPS = new Set[10];
    static { for (int i = 0; i < 10; i++) GROUPS[i] = new LinkedHashSet<Integer>(); }

    public static boolean has(int id) { return IDS.contains(id); }
    public static void clear() { IDS.clear(); }
    public static void set(int id) { IDS.clear(); IDS.add(id); }
    public static void toggle(int id) { if (!IDS.remove(id)) IDS.add(id); }
    public static void add(int id) { IDS.add(id); }

    public static int[] array() {
        int[] a = new int[IDS.size()]; int i = 0;
        for (int id : IDS) a[i++] = id;
        return a;
    }

    public static List<ClientData.Unit> units() {
        List<ClientData.Unit> l = new ArrayList<ClientData.Unit>();
        for (int id : IDS) { ClientData.Unit u = ClientData.UNITS.get(id); if (u != null) l.add(u); }
        return l;
    }

    /** units that died or left drop out of the selection and groups */
    static void prune() {
        IDS.removeIf(id -> !ClientData.UNITS.containsKey(id) && net.minecraft.client.Minecraft.getMinecraft().world != null
                && net.minecraft.client.Minecraft.getMinecraft().world.getEntityByID(id) == null);
    }

    public static void saveGroup(int n) { GROUPS[n].clear(); GROUPS[n].addAll(IDS); }
    public static void loadGroup(int n, boolean add) { if (!add) IDS.clear(); IDS.addAll(GROUPS[n]); }
}
