package com.dogpound.prideoverseer.client;

import com.dogpound.prideoverseer.net.OverseerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * /oversee — the overseer from the chat box (handy for keys/macros too):
 *  /oversee              open or close the view
 *  /oversee select all|none|<kind>   (kind: villager guard golem pet mount soldier worker npc animal)
 *  /oversee order <move|hold|follow|guard|patrol|wander|home|come> [x y z]
 *  /oversee act <stop|glow|rally|heal|tpme>
 *  /oversee group <1-9>   pick a control group
 */
public class OverseeCommand extends CommandBase {
    @Override public String getName() { return "oversee"; }
    @Override public String getUsage(ICommandSender s) { return "/oversee [select all|none|<kind>] [order <type> [x y z]] [act <action>] [group <n>]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

    private static void say(String s) { Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§d✦ §7" + s)); }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] a) {
        Minecraft mc = Minecraft.getMinecraft();
        if (a.length == 0) { mc.addScheduledTask(() -> { if (OverseerMode.active()) OverseerMode.exit(); else OverseerMode.enter(null); }); return; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "select": {
                String what = a.length > 1 ? a[1].toUpperCase(Locale.ROOT) : "ALL";
                if (what.equals("NONE")) Selection.clear();
                else for (ClientData.Unit u : ClientData.UNITS.values()) if (what.equals("ALL") || u.kind.equals(what)) Selection.add(u.id);
                say(Selection.IDS.size() + " selected");
                break;
            }
            case "order": {
                if (a.length < 2) { say("which order?"); return; }
                NBTTagCompound t = new NBTTagCompound();
                t.setString("type", a[1].toUpperCase(Locale.ROOT));
                t.setIntArray("ids", Selection.array());
                BlockPos at = a.length >= 5 ? new BlockPos(Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])) : mc.player.getPosition();
                t.setLong("pos", at.toLong());
                t.setLong("pos2", at.add(10, 0, 0).toLong());
                t.setString("formation", Settings.get().formation);
                send("order", t);
                break;
            }
            case "act": {
                if (a.length < 2) return;
                NBTTagCompound t = new NBTTagCompound();
                t.setString("action", a[1].toLowerCase(Locale.ROOT));
                t.setIntArray("ids", Selection.array());
                send("act", t);
                break;
            }
            case "group": {
                if (a.length > 1) { Selection.loadGroup(Math.max(1, Math.min(9, Integer.parseInt(a[1]))), false); say("group " + a[1] + ": " + Selection.IDS.size()); }
                break;
            }
            default: say(getUsage(sender));
        }
    }

    private static void send(String op, NBTTagCompound t) { OverseerNet.NET.sendToServer(new OverseerNet.Cmd(op, t)); }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
        if (a.length == 1) return getListOfStringsMatchingLastWord(a, "select", "order", "act", "group");
        if (a.length == 2 && a[0].equals("select")) return getListOfStringsMatchingLastWord(a, "all", "none", "villager", "guard", "golem", "pet", "mount", "soldier", "worker", "npc", "animal");
        if (a.length == 2 && a[0].equals("order")) return getListOfStringsMatchingLastWord(a, Arrays.asList("move", "hold", "follow", "guard", "patrol", "wander", "home", "come"));
        if (a.length == 2 && a[0].equals("act")) return getListOfStringsMatchingLastWord(a, "stop", "glow", "rally", "heal", "tpme");
        return Collections.emptyList();
    }
}
