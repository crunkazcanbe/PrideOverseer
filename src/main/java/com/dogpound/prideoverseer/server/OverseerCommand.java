package com.dogpound.prideoverseer.server;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/** /overseer clearall — every unit near you goes back to its normal life;  /overseer list — how many have orders */
public class OverseerCommand extends CommandBase {
    @Override public String getName() { return "overseer"; }
    @Override public String getUsage(ICommandSender s) { return "/overseer <list|clearall>"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
        EntityPlayerMP p = getCommandSenderAsPlayer(sender);
        String what = a.length > 0 ? a[0] : "list";
        int n = 0;
        for (Entity e : p.getServerWorld().loadedEntityList) {
            if (!(e instanceof EntityLiving) || Orders.get(e) == null || !Units.canCommand(p, e)) continue;
            if (what.equals("clearall")) Orders.set(e, null);
            n++;
        }
        p.sendMessage(new TextComponentString(what.equals("clearall") ? "§d✦ §f" + n + " §7units are back to their normal lives." : "§d✦ §f" + n + " §7units near you are carrying out orders."));
    }
}
