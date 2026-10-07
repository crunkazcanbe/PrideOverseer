package com.dogpound.prideoverseer.server;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.ai.EntityAITradePlayer;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Trading from above: Minecraft ends a trade the moment you're more than 4 blocks from the villager (its trade task
 * gives up and closes the window). This swaps that task for one that keeps the trade open while you're in the overseer
 * view, and behaves exactly like Minecraft's otherwise.
 */
public final class RemoteTrade extends EntityAIBase {
    private final EntityVillager villager;

    private RemoteTrade(EntityVillager v) { villager = v; setMutexBits(5); }

    static void install(EntityVillager v) {
        if (!ServerConfig.remoteTalk) return;
        List<EntityAITasks.EntityAITaskEntry> found = new ArrayList<EntityAITasks.EntityAITaskEntry>();
        for (EntityAITasks.EntityAITaskEntry t : v.tasks.taskEntries) {
            if (t.action instanceof RemoteTrade) return;
            if (t.action instanceof EntityAITradePlayer) found.add(t);
        }
        for (EntityAITasks.EntityAITaskEntry t : found) {
            v.tasks.removeTask(t.action);
            v.tasks.addTask(t.priority, new RemoteTrade(v));
        }
    }

    @Override
    public boolean shouldExecute() {
        if (!villager.isEntityAlive() || villager.isInWater() || !villager.onGround || villager.velocityChanged) return false;
        EntityPlayer p = villager.getCustomer();
        if (p == null) return false;
        if (Orders.overseeing(p)) return p.openContainer != null;                 // far away but watching from above
        if (villager.getDistanceSq(p) > 16.0D) return false;
        return p.openContainer != null;
    }

    @Override public void startExecuting() { villager.getNavigator().clearPath(); }

    @Override public void resetTask() { villager.setCustomer(null); }
}
