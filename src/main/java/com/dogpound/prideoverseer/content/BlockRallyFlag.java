package com.dogpound.prideoverseer.content;

import com.dogpound.prideoverseer.server.RallyFlags;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

/** A rally flag: plant it anywhere, then "Rally" in the overseer view sends your units to the nearest one. */
public class BlockRallyFlag extends Block {
    private static final AxisAlignedBB BOX = new AxisAlignedBB(6 / 16D, 0, 6 / 16D, 10 / 16D, 1, 10 / 16D);

    BlockRallyFlag() {
        super(Material.CLOTH);
        OverseerContent.named(this, "rally_flag");
        setUnlocalizedName("prideoverseer.rally_flag");
        setCreativeTab(OverseerContent.TAB);
        setHardness(0.8F);
        setSoundType(SoundType.CLOTH);
    }

    @Override public boolean isOpaqueCube(IBlockState s) { return false; }
    @Override public boolean isFullCube(IBlockState s) { return false; }
    @Override public BlockRenderLayer getBlockLayer() { return BlockRenderLayer.CUTOUT; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState s, IBlockAccess w, BlockPos p) { return BOX; }
    @Override public BlockFaceShape getBlockFaceShape(IBlockAccess w, IBlockState s, BlockPos p, EnumFacing f) { return BlockFaceShape.UNDEFINED; }
    @Override public boolean canPlaceBlockAt(World w, BlockPos p) { return super.canPlaceBlockAt(w, p) && w.getBlockState(p.down()).isSideSolid(w, p.down(), EnumFacing.UP); }

    @Override
    public void onBlockAdded(World w, BlockPos pos, IBlockState s) {
        if (!w.isRemote) RallyFlags.get(w).add(pos);
    }

    @Override
    public void breakBlock(World w, BlockPos pos, IBlockState s) {
        if (!w.isRemote) RallyFlags.get(w).remove(pos);
        super.breakBlock(w, pos, s);
    }

    @Override
    public void addInformation(ItemStack s, World w, List<String> tip, ITooltipFlag f) {
        tip.add("§dPlant it, then press §fRally §din the overseer view:");
        tip.add("§fyour units gather round the nearest flag");
    }
}
