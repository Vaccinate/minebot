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
import net.famzangl.minecraft.minebot.ai.ItemFilter;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.BlockPos;

/**
 * For blocks we collide with.
 * 
 * @author michael
 * 
 */
public class JumpingPlaceBlockAtFloorTask extends PlaceBlockAtFloorTask {
	public JumpingPlaceBlockAtFloorTask(BlockPos pos, ItemFilter filter) {
		super(pos, filter);
	}

	@Override
	protected int getRelativePlaceAtY() {
		return -1;
	}

	@Override
	public boolean isFinished(AIHelper aiHelper) {
		return hasPlacedBlock && isAtDesiredHeight(aiHelper) && super.isFinished(aiHelper);
	}

	@Override
	protected void tryPlaceBlock(AIHelper aiHelper) {
		super.tryPlaceBlock(aiHelper);
		final MovementInput movement = new MovementInput();
		movement.jump = true;
		aiHelper.overrideMovement(movement);
	}

	@Override
	public String toString() {
		return "JumpingPlaceBlockAtFloorTask [pos=" + pos + "]";
	}

}
