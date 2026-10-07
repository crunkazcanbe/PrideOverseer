package com.dogpound.prideoverseer.server;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.util.Locale;

/** Which creatures the overseer may command, and the card each one shows (name, kind, mod, health, order). */
public final class Units {
    private Units() {}

    /** what kind of unit it is: picks its icon and ring colour on the client */
    public enum Kind { VILLAGER, GUARD, GOLEM, PET, MOUNT, SOLDIER, WORKER, NPC, ANIMAL, HOSTILE }

    public static String mod(Entity e) {
        EntityEntry en = EntityRegistry.getEntry(e.getClass());
        return en == null || en.getRegistryName() == null ? "minecraft" : en.getRegistryName().getResourceDomain();
    }

    public static Kind kind(Entity e) {
        String cls = e.getClass().getName().toLowerCase(Locale.ROOT), mod = mod(e);
        if (e instanceof IMob) return Kind.HOSTILE;
        if (e instanceof EntityGolem) return Kind.GOLEM;
        if (cls.contains("guard") || cls.contains("knight") || cls.contains("sentry")) return Kind.GUARD;
        if (mod.equals("ancientwarfare") || mod.startsWith("ancientwarfare")) {
            if (cls.contains("soldier") || cls.contains("combat") || cls.contains("archer") || cls.contains("leader")) return Kind.SOLDIER;
            return Kind.WORKER;
        }
        if (cls.contains("soldier") || cls.contains("warrior") || cls.contains("archer")) return Kind.SOLDIER;
        if (e instanceof EntityVillager) return Kind.VILLAGER;
        if (e instanceof AbstractHorse) return Kind.MOUNT;
        if (e instanceof EntityTameable) return Kind.PET;
        if (mod.equals("mca") || mod.equals("millenaire") || mod.equals("customnpcs") || mod.equals("toroquest") || cls.contains("villager") || cls.contains("npc"))
            return Kind.NPC;
        return Kind.ANIMAL;
    }

    /** may this player command this creature? */
    public static boolean canCommand(EntityPlayer p, Entity e) {
        if (!(e instanceof EntityLiving) || !e.isEntityAlive() || e instanceof EntityPlayer) return false;
        if (e.dimension != p.dimension || e.getDistanceSq(p) > (double) ServerConfig.range * ServerConfig.range) return false;
        if (e instanceof IMob && !ServerConfig.hostiles && !p.isCreative()) return false;
        if (e instanceof IEntityOwnable) {
            Object owner = ((IEntityOwnable) e).getOwnerId();
            if (owner != null && !owner.equals(p.getUniqueID()) && !ServerConfig.othersPets && !p.isCreative()) return false;
        }
        return true;
    }

    /** a creature is worth listing in the view (walks, isn't a fish or a bat...) */
    public static boolean listed(EntityPlayer p, Entity e) {
        if (!canCommand(p, e)) return false;
        Kind k = kind(e);
        if (k == Kind.HOSTILE && !p.isCreative() && !ServerConfig.hostiles) return false;
        if (k == Kind.ANIMAL) {                                        // farm animals only when tamed/owned or named
            if (e instanceof IEntityOwnable && ((IEntityOwnable) e).getOwnerId() != null) return true;
            return e.hasCustomName();
        }
        return e instanceof EntityCreature || k != Kind.ANIMAL;
    }

    public static String name(Entity e) {
        String n = e.getDisplayName().getUnformattedText();
        return n == null || n.isEmpty() ? e.getName() : n;
    }

    public static NBTTagCompound card(Entity e) {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", e.getEntityId());
        t.setString("name", name(e));
        t.setString("kind", kind(e).name());
        t.setString("mod", mod(e));
        t.setString("cls", e.getClass().getSimpleName());
        t.setDouble("x", e.posX); t.setDouble("y", e.posY); t.setDouble("z", e.posZ);
        if (e instanceof EntityLivingBase) {
            t.setFloat("hp", ((EntityLivingBase) e).getHealth());
            t.setFloat("max", ((EntityLivingBase) e).getMaxHealth());
        }
        if (e instanceof EntityVillager) {
            EntityVillager v = (EntityVillager) e;
            try { t.setString("job", v.getProfessionForge().getRegistryName().getResourcePath()); } catch (Throwable ignored) { }
            t.setBoolean("child", v.isChild());
            t.setBoolean("trading", v.isTrading());
        }
        if (e instanceof IEntityOwnable && ((IEntityOwnable) e).getOwnerId() != null) t.setString("owner", ((IEntityOwnable) e).getOwnerId().toString());
        Orders.Order o = Orders.get(e);
        if (o != null) t.setString("order", o.type.name());
        if (o != null && o.pos != null) t.setIntArray("opos", new int[]{ o.pos.getX(), o.pos.getY(), o.pos.getZ() });
        if (o != null && o.pos2 != null) t.setIntArray("opos2", new int[]{ o.pos2.getX(), o.pos2.getY(), o.pos2.getZ() });
        return t;
    }
}
