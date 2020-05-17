/*******************************************************************************
 * This file is part of Minebot.
 *
 * Minebot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Minebot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Minebot.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package net.famzangl.minecraft.minebot.ai.strategy;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.BlockItemFilter;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSets;
import net.famzangl.minecraft.minebot.ai.path.world.WorldData;
import net.famzangl.minecraft.minebot.settings.MinebotSettings;
import net.minecraft.block.Blocks;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Place a torch on the floor if the light level is below x.
 * @author Michael Zangl
 */
public class PlaceTorchStrategy extends AIStrategy {
	private static final Logger LOGGER = LogManager.getLogger(PlaceTorchStrategy.class);

	public static class PosAndDir {
		public final BlockPos place;
		public final Direction dir;

		public PosAndDir(BlockPos place, Direction dir) {
			super();
			this.place = place;
			this.dir = dir;
		}

		public BlockPos getPlaceOn() {
			return place.offset(dir);
		}

		@Override
		public String toString() {
			return "PosAndDir [place=" + place + ", dir=" + dir + "]";
		}
	}

	// TODO: Instead, use net.minecraft.block.Block.hasEnoughSolidSide valid block states.
	private static final BlockSet CAN_PLACE_ON = BlockSets.SIMPLE_CUBE
			// Cannot place a torch on leaves
			.minus(BlockSets.LEAVES);

	private static final BlockItemFilter TORCH_FILTER = new BlockItemFilter(
			Blocks.TORCH);

	private static final int MAX_TICKS_PER_PLACE = 10;

	private int torchLightLevel;
	private Queue<PosAndDir> attemptPositions = new ConcurrentLinkedQueue<PosAndDir>();

	private int inCurrentTick;

	private HashSet<BlockPos> done = new HashSet<BlockPos>();

	private BlockPos currentPosition;

	public PlaceTorchStrategy() {
		torchLightLevel = MinebotSettings.getSettings().getSaferules().getPlaceTorchLightLevel();
	}

	@Override
	public boolean checkShouldTakeOver(AIHelper helper) {
		if (!helper.canSelectItem(TORCH_FILTER)) {
			return false;
		}
		ClientPlayerEntity playerPosition = helper.getMinecraft().player;
		BlockPos playerBlockPosition = helper.getPlayerPosition();
		if (!done.contains(playerBlockPosition)
				&& playerBlockPosition.distanceSq(playerPosition.getPosX(),
						playerBlockPosition.getY() + .5, playerPosition.getPosZ(), true) < .3 * .3) {
			// we are close to the center of that block.
			int light = helper.getLightAt(playerBlockPosition);
			if (light <= torchLightLevel) {
				loadAttemptPositions(helper.getWorld());
				currentPosition = playerBlockPosition;
				return !attemptPositions.isEmpty();
			}
		}

		return false;
	}

	private void loadAttemptPositions(WorldData world) {
		attemptPositions.clear();
		for (BlockPos pos : new BlockPos[] { world.getPlayerPosition(),
				world.getPlayerPosition().add(0, 1, 0) }) {
			for (Direction f : Direction.values()) {
				if (f != Direction.UP) {
					loadAttemptPosition(world, new PosAndDir(pos, f));
				}
			}
		}
	}

	private void loadAttemptPosition(WorldData world, PosAndDir posAndDir) {
		final BlockPos placeOn = posAndDir.getPlaceOn();
		if (CAN_PLACE_ON.isAt(world, placeOn)
				&& BlockSets.AIR.isAt(world, posAndDir.place)) {
			attemptPositions.add(posAndDir);
		}
	}

	@Override
	protected void onActivate(AIHelper helper) {
		inCurrentTick = 0;
		LOGGER.debug("Took over to place a torch.");
		super.onActivate(helper);
	}

	@Override
	protected TickResult onGameTick(AIHelper helper) {
		if (inCurrentTick > MAX_TICKS_PER_PLACE) {
			attemptPositions.poll();
			inCurrentTick = 0;
			return TickResult.TICK_AGAIN;
		}

		PosAndDir pos = attemptPositions.peek();
		LOGGER.debug("Attempt to  place at " + pos + " in tick " + inCurrentTick);
		if (pos == null) {
			done();
			return TickResult.NO_MORE_WORK;
		}

		if (!helper.selectCurrentItem(TORCH_FILTER)) {
			return TickResult.NO_MORE_WORK;
		}

		if (BlockSets.TORCH.isAt(helper.getWorld(), pos.place)) {
			done();
			attemptPositions.clear();
			return TickResult.NO_MORE_WORK;
		}

		helper.faceSideOf(pos.getPlaceOn(), pos.dir.getOpposite());
		if (inCurrentTick > 4
				&& helper
						.isFacingBlock(pos.getPlaceOn(), pos.dir.getOpposite())) {
			helper.overrideUseItem();
		}

		inCurrentTick++;
		return TickResult.TICK_HANDLED;
	}

	private void done() {
		done.add(currentPosition);
	}

	@Override
	public String getDescription(AIHelper helper) {
		return "Place torch if light level <= " + torchLightLevel;
	}

}
