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
package net.famzangl.minecraft.minebot.ai.task.place;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.BlockItemFilter;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSets;
import net.famzangl.minecraft.minebot.ai.task.AITask;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.task.error.SelectTaskError;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;

/**
 * Sneak towards (x, y, z) and then place the block below you.
 * 
 * @author michael
 * 
 */
public class SneakAndPlaceTask extends AITask {

	protected final BlockPos destinationStandPosition;
	protected final BlockPos startStandPosition;
	protected final BlockItemFilter filter;
	/**
	 * Direction we need to walk.
	 */
	private final EnumFacing inDirection;
	private final double minBuildHeight;
	private int faceTimer;

	/**
	 * 
	 * @param destinationStandPosition The position over the block we are placing where we stand after the block was placed.
	 * @param filter The filter for the block to place.
	 * @param startStandPosition The old position where we were standing before placing the block.
	 * @param minBuildHeight The minimum height the player needs to have before placing the block. For jumping.
	 */
	public SneakAndPlaceTask(BlockPos destinationStandPosition, BlockItemFilter filter,
			BlockPos startStandPosition, double minBuildHeight) {
		this.destinationStandPosition = destinationStandPosition;
		this.filter = filter;
		this.startStandPosition = startStandPosition;
		this.minBuildHeight = minBuildHeight;
		final EnumFacing foundInDir = AIHelper.getDirectionFor(destinationStandPosition.subtract(startStandPosition));
		if (foundInDir.getFrontOffsetY() != 0 || foundInDir == null) {
			throw new IllegalArgumentException();
		}
		inDirection = foundInDir;
	}

	@Override
	public boolean isFinished(AIHelper aiHelper) {
		return !BlockSets.AIR.isAt(aiHelper.getWorld(), getPositionToPlaceAt()) && !aiHelper.isJumping();
	}

	protected BlockPos getPositionToPlaceAt() {
		return destinationStandPosition.add(0, -1, 0);
	}

	@Override
	public void runTick(AIHelper aiHelper, TaskOperations taskOperations) {
		if (faceTimer > 0) {
			faceTimer--;
		}
		if (aiHelper.sneakFrom(getFromPos(), inDirection, faceWhileSneaking())) {
			final boolean hasRequiredHeight = aiHelper.getMinecraft().player
					.getEntityBoundingBox().minY > minBuildHeight - 0.05;
			if (hasRequiredHeight) {
				if (faceTimer == 0) {
					if (faceBlock(aiHelper, taskOperations)) {
						faceTimer = 3;
					}
				} else if (isFacingRightBlock(aiHelper)) {
					if (aiHelper.selectCurrentItem(filter)) {
						aiHelper.overrideUseItem();
					} else {
						taskOperations.desync(new SelectTaskError(filter));
					}
				}
			} else {
				final MovementInput movement = new MovementInput();
				movement.jump = true;
				aiHelper.overrideMovement(movement);
			}
		}
	}

	protected boolean faceWhileSneaking() {
		return false;
	}

	protected boolean isFacingRightBlock(AIHelper aiHelper) {
		return aiHelper.isFacingBlock(getPositionToPlaceOn(), inDirection);
	}

	protected boolean faceBlock(AIHelper aiHelper, TaskOperations taskOperations) {
		return aiHelper.faceSideOf(getPositionToPlaceOn(), inDirection);
	}

	protected BlockPos getPositionToPlaceOn() {
		return startStandPosition.add(0, -1, 0);
	}
	
	protected BlockPos getFromPos() {
		return startStandPosition;
	}

	@Override
	public String toString() {
		return "SneakAndPlaceTask [pos=" + destinationStandPosition + ", filter=" + filter
				+ ", startStandPosition=" + startStandPosition + ", inDirection="
				+ inDirection + ", minBuildHeight=" + minBuildHeight + "]";
	}

}
