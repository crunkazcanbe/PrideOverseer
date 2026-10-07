package com.dogpound.prideoverseer.content;

import com.dogpound.prideoverseer.server.Orders;
import com.dogpound.prideoverseer.server.Units;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Command Baton: right-click to rise into the overseer view; sneak + right-click a creature to make it follow
 * you (again to let it go). Ancient Warfare players will know the feeling.
 */
public class ItemCommandBaton extends Item {
    ItemCommandBaton() {
        OverseerContent.named(this, "command_baton");
        setUnlocalizedName("prideoverseer.command_baton");
        setCreativeTab(OverseerContent.TAB);
        setMaxStackSize(1);
    }

    @Override public EnumRarity getRarity(ItemStack s) { return EnumRarity.EPIC; }
    @Override public boolean hasEffect(ItemStack s) { return true; }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer p, EnumHand hand) {
        if (p.isSneaking()) return new ActionResult<ItemStack>(EnumActionResult.PASS, p.getHeldItem(hand));
        if (w.isRemote) com.dogpound.prideoverseer.client.ClientSetup.openOverseer(null);
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, p.getHeldItem(hand));
    }

    @Override
    public boolean itemInteractionForEntity(ItemStack s, EntityPlayer p, EntityLivingBase target, EnumHand hand) {
        if (!p.isSneaking() || !(target instanceof EntityLiving)) return false;
        if (p.world.isRemote) return true;
        if (!Units.canCommand(p, target)) { Orders.say(p, "§7" + Units.name(target) + " won't take orders from you."); return true; }
        Orders.Order o = Orders.get(target);
        if (o != null && o.type == Orders.Type.FOLLOW) { Orders.set(target, null); Orders.say(p, "§d✦ §f" + Units.name(target) + " §7goes back to its life."); }
        else {
            Orders.handle((net.minecraft.entity.player.EntityPlayerMP) p, "order", follow(target.getEntityId()));
            Orders.say(p, "§d✦ §f" + Units.name(target) + " §7follows you.");
        }
        return true;
    }

    private static net.minecraft.nbt.NBTTagCompound follow(int id) {
        net.minecraft.nbt.NBTTagCompound t = new net.minecraft.nbt.NBTTagCompound();
        t.setString("type", "FOLLOW");
        t.setIntArray("ids", new int[]{ id });
        return t;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§dRight-click: §frise into the overseer view");
        tip.add("§dSneak + right-click a creature: §fit follows you");
        tip.add("§7Key: §fO §7(change in Controls)");
    }
}
