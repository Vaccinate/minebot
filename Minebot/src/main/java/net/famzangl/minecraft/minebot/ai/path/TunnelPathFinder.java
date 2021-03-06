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
package net.famzangl.minecraft.minebot.ai.path;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.BlockItemFilter;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSets;
import net.famzangl.minecraft.minebot.ai.path.world.WorldData;
import net.famzangl.minecraft.minebot.ai.path.world.WorldWithDelta;
import net.famzangl.minecraft.minebot.ai.task.DestroyInRangeTask;
import net.famzangl.minecraft.minebot.ai.task.PlaceTorchSomewhereTask;
import net.famzangl.minecraft.minebot.ai.task.RunOnceTask;
import net.famzangl.minecraft.minebot.ai.task.SkipWhenSearchingPrefetch;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.utils.BlockCuboid;
import net.famzangl.minecraft.minebot.ai.utils.BlockFilteredArea;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Hashtable;

public class TunnelPathFinder extends AlongTrackPathFinder {
	/**
	 * Marks a section of the tunnel as done.
	 * 
	 * @author Michael Zangl
	 */
	@SkipWhenSearchingPrefetch
	private final class MarkAsDoneTask extends RunOnceTask {
		private final int stepNumber;

		private MarkAsDoneTask(int stepNumber) {
			this.stepNumber = stepNumber;
		}

		@Override
		protected void runOnce(AIHelper aiHelper, TaskOperations taskOperations) {
			finishedTunnels.set(stepNumber);
			inQueueTunnels.clear(stepNumber);
			taskOperations.faceAndDestroyForNextTask();
		}
		
		@Override
		public void onCanceled() {
			inQueueTunnels.clear(stepNumber);
			super.onCanceled();
		}

		@Override
		public boolean applyToDelta(WorldWithDelta world) {
			return true;
		}

		@Override
		public String toString() {
			return "MarkAsDoneTask [stepNumber=" + stepNumber + "]";
		}
	}

	/**
	 * Marks that we have reached a given section of the tunnel.
	 * 
	 * @author Michael Zangl
	 */
	@SkipWhenSearchingPrefetch
	private final class MarkAsReachedTask extends RunOnceTask {
		private final int stepNumber;

		private MarkAsReachedTask(int stepNumber) {
			this.stepNumber = stepNumber;
		}

		@Override
		protected void runOnce(AIHelper aiHelper, TaskOperations taskOperations) {
			tunnelPositionStartCount.put(stepNumber,
					getStartCount(stepNumber) + 1);

			synchronized (currentStepNumberMutex) {
				currentStepNumber = stepNumber;
			}
			taskOperations.faceAndDestroyForNextTask();
		}

		@Override
		public boolean applyToDelta(WorldWithDelta world) {
			return true;
		}

		@Override
		public String toString() {
			return "MarkAsReachedTask [stepNumber=" + stepNumber + "]";
		}
	}

	/**
	 * The side of the tunnel at which the torch is placed.
	 * 
	 * @author Michael Zangl
	 *
	 */
	public enum TorchSide {
		TORCH_NONE(false, false, false),
		TORCH_LEFT(true, false, false),
		TORCH_RIGHT(false, true,false),
		TORCH_BOTH(true, true, false),
		TORCH_FLOOR(false, false, true);

		private final boolean left;
		private final boolean right;
		private final boolean floor;

		private TorchSide(boolean left, boolean right, boolean floor) {
			this.left = left;
			this.right = right;
			this.floor = floor;
		}
	}

	public interface TunnelMode {
		default int getAddToSide() {
			return 0;
		}
		default int getAddToTop() {
			return 0;
		}
		default boolean addBranches() {
			return false;
		}
	}

	private final static BlockSet FREE_TUNNEL_BLOCKS = BlockSet.builder().add(BlockSets.AIR)
			.add(BlockSets.TORCH).build();

	private BitSet finishedTunnels = new BitSet();
	private BitSet inQueueTunnels = new BitSet();
	private final TunnelMode tunnelMode;

	/**
	 * How often did we attempt to tunnel at a given position?
	 */
	private Hashtable<Integer, Integer> tunnelPositionStartCount = new Hashtable<Integer, Integer>();

	private final TorchSide torches;

	private int currentStepNumber = 0;
	private final Object currentStepNumberMutex = new Object();

	public TunnelPathFinder(int dx, int dz, int cx, int cy, int cz,
			TunnelMode tunnelMode, TorchSide torches, int length) {
		super(dx, dz, cx, cy, cz, length);
		this.tunnelMode = tunnelMode;
		this.torches = torches;
	}

	@Override
	protected float rateDestination(int distance, int x, int y, int z) {
		if (shouldTunnel(distance, x, y, z)) {
			return distance + 1;
		} else {
			return -1;
		}
	}

	/**
	 * Test if we should dig a tunnel to that position.
	 * 
	 * @param distance 
	 * @param x
	 *            The x pos.
	 * @param y
	 *            The y pos
	 * @param z
	 *            The z pos
	 * @return
	 */
	private boolean shouldTunnel(int distance, int x, int y, int z) {
		if (y != cy || !isOnTrack(x, z)) {
			// not on track
			return false;
		}
		int stepNumber = getStepNumber(x, z);

		if (finishedTunnels.get(stepNumber) || inQueueTunnels.get(stepNumber)) {
			// we already handled that position.
			return false;
		}

		boolean isFree = FREE_TUNNEL_BLOCKS.isAt(world, x, y, z)
				&& FREE_TUNNEL_BLOCKS.isAt(world, x, y + 1, z);

		int startCount = getStartCount(stepNumber);

		if (startCount > 0 && startCount < 5) {
			// we started something but got stopped by e.g. eat/,,,
			return true;
		} else {
			// More than 30 blocks: Walk there first, check next later
			return !isFree || (distance > 30 && stepNumber > currentStepNumber) || (distance > 1 && stepNumber == length);
		}
	}

	private int getStartCount(int stepNumber) {
		return tunnelPositionStartCount.getOrDefault(stepNumber, 0);
	}

	@Override
	protected void addTasksForTarget(BlockPos currentPos) {
		final int stepNumber = getStepNumber(currentPos.getX(),
				currentPos.getZ());
		// if (getStartCount(stepNumber) < 1) {
		// tunnelPositionStartCount.put(stepNumber, 1);
		// }
		addTask(new MarkAsReachedTask(stepNumber));
		BlockPos p1, p2;
		if (dx == 0) {
			p1 = currentPos.add(tunnelMode.getAddToSide(), 0, 0);
			p2 = currentPos.add(-tunnelMode.getAddToSide(), 1 + tunnelMode.getAddToTop(), 0);
		} else {
			p1 = currentPos.add(0, 0, tunnelMode.getAddToSide());
			p2 = currentPos.add(0, 1 + tunnelMode.getAddToTop(), -tunnelMode.getAddToSide());
		}
		BlockCuboid<WorldData> tunnelArea = new BlockCuboid<>(p1, p2);
		BlockFilteredArea<WorldData> area = new BlockFilteredArea<>(tunnelArea,
				FREE_TUNNEL_BLOCKS.invert());
		addTask(new DestroyInRangeTask(area));

		if (helper.canSelectItem(new BlockItemFilter(Blocks.TORCH))) {
			final boolean isTorchStep = stepNumber % 8 == 0;
			// TODO: Only check for right torch.
			if (torches.right && isTorchStep && !containsTorches(tunnelArea)) {
				addTorchesTask(currentPos, -dz, dx);
			}
			if (torches.left && isTorchStep && !containsTorches(tunnelArea)) {
				addTorchesTask(currentPos, dz, -dx);
			}
			if (torches.floor && isTorchStep && !containsTorches(tunnelArea)) {
				addTask(new PlaceTorchSomewhereTask(
						Collections.singletonList(currentPos), Direction.DOWN));
			}
		}
		final boolean isBranchStep = stepNumber % 4 == 2;
		if (tunnelMode.addBranches() && isBranchStep) {
			addBranchTask(currentPos, -dz, dx);
			addBranchTask(currentPos, dz, -dx);
		}
		addTask(new MarkAsDoneTask(stepNumber));
	}

	private boolean containsTorches(BlockCuboid<WorldData> tunnelArea) {
		BlockFilteredArea<WorldData> torchArea = new BlockFilteredArea<>(tunnelArea,
				BlockSets.TORCH);
		return torchArea.getVolume(world) > 0;
	}

	private void addBranchTask(BlockPos currentPos, int dx, int dz) {
		int branchMax = 0;
		for (int i = 1; i <= 4; i++) {
			if (!isSafeBranchPos(currentPos.add(dx * i, 1, dz * i)))
				break;
			branchMax = i;
		}
		if (branchMax > 0) {
			addTask(new DestroyInRangeTask(currentPos.add(dx * 1, 1, dz * 1),
					currentPos.add(dx * branchMax, 1, dz * branchMax)));
		}
	}

	private boolean isSafeBranchPos(BlockPos pos) {
		return BlockSets.safeSideAndCeilingAround(world, pos)
				&& BlockSets.SAFE_SIDE.isAt(world, pos.add(0, -1, 0));
	}

	private void addTorchesTask(BlockPos currentPos, int dirX, int dirZ) {
		final ArrayList<BlockPos> positions = new ArrayList<BlockPos>();
		positions.add(new BlockPos(currentPos.getX() + dirX * tunnelMode.getAddToSide(), currentPos
				.getY() + 1, currentPos.getZ() + dirZ * tunnelMode.getAddToSide()));

		for (int i = tunnelMode.getAddToSide(); i >= 0; i--) {
			positions.add(new BlockPos(currentPos.getX() + dirX * i, currentPos
					.getY(), currentPos.getZ() + dirZ * i));
		}
		addTask(new PlaceTorchSomewhereTask(positions,
				AIHelper.getDirectionForXZ(dirX, dirZ), Direction.DOWN));
	}

	public String getProgress() {
		synchronized (currentStepNumberMutex) {
			String str = currentStepNumber + "";
			if (length >= 0) {
				str += "/" + length;
			}
			return str + "m";
		}
	}

	@Override
	public String toString() {
		return "TunnelPathFinder [tunnelMode=" + tunnelMode + ", dx=" + dx + ", dz=" + dz + ", cx=" + cx
				+ ", cy=" + cy + ", cz=" + cz + ", torches=" + torches
				+ ", length=" + length + "]";
	}

}
