package com.dogpound.prideoverseer.server;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Carries out a unit's order. Added to every creature as its MOST important task (priority -1), so while it has an
 * order its own wandering/working doesn't pull it away. Fighters (golems, pets, soldiers) let their own attack AI take
 * over while an enemy is near their guard spot, then come back.
 */
public class OrderTask extends EntityAIBase {
    private final EntityLiving unit;
    private int repath, idle;
    private long yieldUntil;
    private BlockPos wanderTo;

    public OrderTask(EntityLiving unit) {
        this.unit = unit;
        setMutexBits(3);                                          // movement + looking
    }

    private Orders.Order order() { return Orders.get(unit); }

    private boolean fighter() {
        return unit instanceof EntityGolem || unit instanceof EntityTameable || Units.kind(unit) == Units.Kind.SOLDIER
                || Units.kind(unit) == Units.Kind.GUARD || unit instanceof IMob;
    }

    /** guard duty: an enemy near the spot -> step aside for the unit's own attack AI */
    private boolean enemyNear(Orders.Order o) {
        if (o.type != Orders.Type.GUARD || o.pos == null || !fighter()) return false;
        if (unit.getAttackTarget() != null && unit.getAttackTarget().isEntityAlive()
                && unit.getAttackTarget().getDistanceSq(o.pos) < (o.radius + 8) * (o.radius + 8)) return true;
        List<EntityLivingBase> l = unit.world.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(o.pos).grow(o.radius),
                x -> x instanceof IMob && x.isEntityAlive() && !(unit instanceof IMob));
        if (l.isEmpty()) return false;
        unit.setAttackTarget(l.get(0));
        return true;
    }

    @Override
    public boolean shouldExecute() {
        Orders.Order o = order();
        if (o == null) return false;
        if (unit.world.getTotalWorldTime() < yieldUntil) return false;
        if (enemyNear(o)) { yieldUntil = unit.world.getTotalWorldTime() + 60; return false; }
        return true;
    }

    @Override public boolean shouldContinueExecuting() { return shouldExecute(); }

    @Override public void startExecuting() { repath = 0; idle = 0; }

    @Override public void resetTask() { unit.getNavigator().clearPath(); }

    private double speed() { return unit instanceof EntityCreature ? 0.6D : 1.0D; }

    private boolean goTo(BlockPos p, double near) {
        double d = unit.getDistanceSqToCenter(p);
        if (d <= near * near) { unit.getNavigator().clearPath(); return true; }
        if (--repath <= 0 || unit.getNavigator().noPath()) {
            repath = 20;
            boolean ok = unit.getNavigator().tryMoveToXYZ(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed());
            if (!ok && d > 32 * 32) {                              // too far for one path: walk toward it in steps
                Vec3d dir = new Vec3d(p.getX() - unit.posX, 0, p.getZ() - unit.posZ).normalize().scale(24);
                unit.getNavigator().tryMoveToXYZ(unit.posX + dir.x, unit.posY, unit.posZ + dir.z, speed());
            }
        }
        return false;
    }

    @Override
    public void updateTask() {
        Orders.Order o = order();
        if (o == null) return;
        switch (o.type) {
            case MOVE: case HOLD: case COME:
                if (o.pos != null && goTo(o.pos, o.type == Orders.Type.HOLD ? 1.2 : 1.6)) idle++;
                if (idle > 0 && unit.ticksExisted % 40 == 0) unit.getLookHelper().setLookPosition(unit.posX + unit.getRNG().nextGaussian() * 4, unit.posY + 1, unit.posZ + unit.getRNG().nextGaussian() * 4, 10, 10);
                break;
            case GUARD:
                if (o.pos == null) break;
                if (unit.getDistanceSqToCenter(o.pos) > o.radius * o.radius || unit.getNavigator().noPath() && unit.getRNG().nextInt(120) == 0) {
                    BlockPos t = unit.getDistanceSqToCenter(o.pos) > o.radius * o.radius ? o.pos
                            : o.pos.add(unit.getRNG().nextInt(o.radius * 2 + 1) - o.radius, 0, unit.getRNG().nextInt(o.radius * 2 + 1) - o.radius);
                    unit.getNavigator().tryMoveToXYZ(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, speed() * 0.8);
                }
                break;
            case PATROL: {
                BlockPos t = o.leg ? o.pos2 : o.pos;
                if (t != null && goTo(t, 1.6)) { o.leg = !o.leg; Orders.save(unit, o); }
                break;
            }
            case WANDER:
                if (o.pos == null) break;
                if (wanderTo == null || unit.getNavigator().noPath() && unit.getRNG().nextInt(60) == 0 || unit.getDistanceSqToCenter(o.pos) > (o.radius + 4) * (o.radius + 4)) {
                    wanderTo = o.pos.add(unit.getRNG().nextInt(o.radius * 2 + 1) - o.radius, 0, unit.getRNG().nextInt(o.radius * 2 + 1) - o.radius);
                    if (unit instanceof EntityCreature) {
                        Vec3d v = RandomPositionGenerator.findRandomTargetBlockTowards((EntityCreature) unit, 6, 3, new Vec3d(wanderTo));
                        if (v != null) wanderTo = new BlockPos(v);
                    }
                    unit.getNavigator().tryMoveToXYZ(wanderTo.getX() + 0.5, wanderTo.getY(), wanderTo.getZ() + 0.5, speed() * 0.7);
                }
                break;
            case HOME:
                if (o.pos == null || goTo(o.pos, 3)) Orders.set(unit, null);      // home: back to its normal life
                break;
            case FOLLOW: {
                EntityPlayer p = o.player == null ? null : unit.world.getPlayerEntityByUUID(o.player);
                if (p == null) break;
                double d = unit.getDistanceSq(p);
                if (d > 48 * 48 && ServerConfig.followTeleport && !p.isSpectator() && p.onGround) {
                    unit.setPositionAndUpdate(p.posX + unit.getRNG().nextGaussian() * 1.5, p.posY, p.posZ + unit.getRNG().nextGaussian() * 1.5);
                    unit.getNavigator().clearPath();
                } else if (d > 9) {
                    if (--repath <= 0) { repath = 10; unit.getNavigator().tryMoveToEntityLiving(p, speed() * 1.15); }
                } else unit.getNavigator().clearPath();
                unit.getLookHelper().setLookPositionWithEntity(p, 10, unit.getVerticalFaceSpeed());
                break;
            }
            default: break;
        }
    }
}
