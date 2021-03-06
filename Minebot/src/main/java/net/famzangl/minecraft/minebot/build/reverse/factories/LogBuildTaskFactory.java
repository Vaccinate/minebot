package net.famzangl.minecraft.minebot.build.reverse.factories;

import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.build.blockbuild.BuildTask;
import net.famzangl.minecraft.minebot.build.blockbuild.LogBuildTask;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public class LogBuildTaskFactory extends AbstractBuildTaskFactory {

	@Override
	protected BuildTask getTaskImpl(BlockPos position,
			BlockState block) {
		return new LogBuildTask(position, block);
	}

	@Override
	public BlockSet getSupportedBlocks() {
		return LogBuildTask.NORMAL_LOGS;
	}

}
