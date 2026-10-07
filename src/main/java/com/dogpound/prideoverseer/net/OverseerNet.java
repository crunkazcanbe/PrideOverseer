package com.dogpound.prideoverseer.net;

import com.dogpound.prideoverseer.server.Orders;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Two messages carrying NBT: the client asks (Cmd), the server answers with what the overseer view shows (Sync). */
public final class OverseerNet {
    private OverseerNet() {}
    public static final SimpleNetworkWrapper NET = NetworkRegistry.INSTANCE.newSimpleChannel("prideoverseer");

    public static void init() {
        NET.registerMessage(CmdHandler.class, Cmd.class, 0, Side.SERVER);
        NET.registerMessage(SyncHandler.class, Sync.class, 1, Side.CLIENT);
    }

    public static final class Cmd implements IMessage {
        public String op = ""; public NBTTagCompound args = new NBTTagCompound();
        public Cmd() {}
        public Cmd(String op, NBTTagCompound args) { this.op = op; this.args = args == null ? new NBTTagCompound() : args; }
        @Override public void fromBytes(ByteBuf b) { op = ByteBufUtils.readUTF8String(b); args = ByteBufUtils.readTag(b); if (args == null) args = new NBTTagCompound(); }
        @Override public void toBytes(ByteBuf b) { ByteBufUtils.writeUTF8String(b, op); ByteBufUtils.writeTag(b, args); }
    }

    public static final class Sync implements IMessage {
        public String kind = ""; public NBTTagCompound data = new NBTTagCompound();
        public Sync() {}
        public Sync(String kind, NBTTagCompound data) { this.kind = kind; this.data = data; }
        @Override public void fromBytes(ByteBuf b) { kind = ByteBufUtils.readUTF8String(b); data = ByteBufUtils.readTag(b); if (data == null) data = new NBTTagCompound(); }
        @Override public void toBytes(ByteBuf b) { ByteBufUtils.writeUTF8String(b, kind); ByteBufUtils.writeTag(b, data); }
    }

    public static final class CmdHandler implements IMessageHandler<Cmd, IMessage> {
        @Override public IMessage onMessage(Cmd m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                try { Orders.handle(p, m.op, m.args); }
                catch (Throwable t) { com.dogpound.prideoverseer.PrideOverseer.LOG.warn("command {} failed", m.op, t); }
            });
            return null;
        }
    }

    public static final class SyncHandler implements IMessageHandler<Sync, IMessage> {
        @Override public IMessage onMessage(Sync m, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> com.dogpound.prideoverseer.client.ClientData.receive(m.kind, m.data));
            return null;
        }
    }

    public static void send(EntityPlayerMP p, String kind, NBTTagCompound data) { NET.sendTo(new Sync(kind, data), p); }
}
