package net.famzangl.minecraft.minebot.ai.path;

import net.famzangl.minecraft.minebot.ai.BlockItemFilter;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSets;
import net.famzangl.minecraft.minebot.ai.task.place.DestroyBlockTask;
import net.famzangl.minecraft.minebot.ai.task.place.PlaceBlockAtFloorTask;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public class SugarCanePathFinder extends WalkingPathfinder {
	private static final BlockSet SUGAR_CANE_GROUND = BlockSet.builder().add(Blocks.SAND, Blocks.GRASS, Blocks.DIRT).build();
	private static final BlockSet SUGAR_CANE = BlockSet.builder().add(Blocks.SUGAR_CANE).build();

	@Override
	protected float rateDestination(int distance, int x, int y, int z) {
		if (SUGAR_CANE.isAt(world, x, y, z)
				&& SUGAR_CANE.isAt(world, x, y + 1, z)) {
			return super.rateDestination(distance, x, y, z);
		} else if (isSugarCanePlantPlace(x, y, z)) {
			return super.rateDestination(distance, x, y, z) + 2;
		} else {
			return -1;
		}
	}

	private boolean isSugarCanePlantPlace(int x, int y, int z) {
		if (!BlockSets.AIR.isAt(world, x, y, z)
				|| !BlockSets.AIR.isAt(world, x, y + 1, z)
				|| !SUGAR_CANE_GROUND.isAt(world, x, y - 1, z)) {
			// not on sand
			return false;
		}
		for (Direction d : Direction.values()) {
			if (d.getXOffset() == 0) {
				if (BlockSets.WATER.isAt(world, x + d.getXOffset(), y - 1,
						z + d.getZOffset())) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected void addTasksForTarget(BlockPos currentPos) {
		super.addTasksForTarget(currentPos);
		BlockPos top = currentPos.add(0, 1, 0);
		if (SUGAR_CANE.isAt(world, top)) {
			addTask(new DestroyBlockTask(top));
		} else if (BlockSets.AIR.isAt(world, currentPos)) {
			//TODO: Test if this is the correct filter
			addTask(new PlaceBlockAtFloorTask(currentPos, new BlockItemFilter(Blocks.SUGAR_CANE)));
		}
	}
}
