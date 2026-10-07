package com.dogpound.prideoverseer.content;

import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** The War Table: a strategy table with a painted map, little figures and flags. Right-click to oversee from here. */
public class BlockWarTable extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB BOX = new AxisAlignedBB(0, 0, 0, 1, 15 / 16D, 1);

    BlockWarTable() {
        super(Material.WOOD);
        OverseerContent.named(this, "war_table");
        setUnlocalizedName("prideoverseer.war_table");
        setCreativeTab(OverseerContent.TAB);
        setHardness(2.5F);
        setSoundType(SoundType.WOOD);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int m) { return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(m & 3)); }
    @Override public int getMetaFromState(IBlockState s) { return s.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState getStateForPlacement(World w, BlockPos p, EnumFacing f, float x, float y, float z, int m, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing f) { return f == EnumFacing.DOWN ? BlockFaceShape.UNDEFINED : BlockFaceShape.UNDEFINED; }

    @Override
    public boolean onBlockActivated(World w, BlockPos pos, IBlockState s, EntityPlayer p, EnumHand hand, EnumFacing side, float hx, float hy, float hz) {
        if (w.isRemote) com.dogpound.prideoverseer.client.ClientSetup.openOverseer(pos);
        return true;
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§dRight-click: §foversee your lands from this table");
    }
}
