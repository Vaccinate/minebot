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

import java.util.ArrayList;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.enchanting.CloseScreenTask;
import net.famzangl.minecraft.minebot.ai.scanner.BlockRangeFinder;
import net.famzangl.minecraft.minebot.ai.scanner.BlockRangeScanner;
import net.famzangl.minecraft.minebot.ai.scanner.FurnaceBlockHandler;
import net.famzangl.minecraft.minebot.ai.scanner.FurnaceBlockHandler.FurnaceData;
import net.famzangl.minecraft.minebot.ai.task.AITask;
import net.famzangl.minecraft.minebot.ai.task.ConditionalWaitTask;
import net.famzangl.minecraft.minebot.ai.task.ConditionalWaitTask.WaitCondition;
import net.famzangl.minecraft.minebot.ai.task.RunOnceTask;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.task.UseItemOnBlockAtTask;
import net.famzangl.minecraft.minebot.ai.task.WaitTask;
import net.famzangl.minecraft.minebot.ai.task.error.StringTaskError;
import net.famzangl.minecraft.minebot.ai.task.inventory.ItemWithSubtype;
import net.famzangl.minecraft.minebot.ai.task.inventory.MoveInInventoryTask;
import net.famzangl.minecraft.minebot.ai.task.inventory.TakeResultItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;

public class FurnaceStrategy extends PathFinderStrategy {

	public static class FurnaceTaskList {
		private boolean putItem;
		private boolean putFuel;
		private boolean take;

		public FurnaceTaskList(boolean putItem, boolean putFuel, boolean take) {
			this.putItem = putItem;
			this.putFuel = putFuel;
			this.take = take;
		}

		@Override
		public String toString() {
			return "FurnaceTaskList [putItem=" + putItem + ", putFuel="
					+ putFuel + ", take=" + take + "]";
		}

		public boolean hasSomePutTasks(FurnaceData f, ItemStack stack) {
			return couldPutFuel(f, stack) || couldPutItem(f, stack);
		}

		public boolean couldTake(FurnaceData f) {
			return f.couldTake();
		}

		public boolean couldPutItem(FurnaceData f, ItemStack stack) {
			if (stack == null) {
				return false;
			}
			return putItem && f.couldPut(new ItemWithSubtype(stack));
		}

		public boolean couldPutFuel(FurnaceData f, ItemStack stack) {
			if (stack == null) {
				return false;
			}
			return putItem && f.couldPutFuel(new ItemWithSubtype(stack));
		}
	}

	private static abstract class MoveToWhatever extends MoveInInventoryTask
			implements WaitCondition {
		protected int stackIndex;
		protected FurnaceTaskList list;
		protected FurnaceData f;
		private boolean moved;

		public MoveToWhatever(int stackIndex, FurnaceTaskList list,
				FurnaceData f) {
			super();
			this.stackIndex = stackIndex;
			this.list = list;
			this.f = f;
		}

		protected abstract boolean shouldMove(AIHelper aiHelper);

		@Override
		public boolean shouldWait() {
			return moved;
		}

		@Override
		protected int getMissingAmount(AIHelper aiHelper, int currentCount) {
			return 64;
		}

		@Override
		protected int getFromStack(AIHelper aiHelper) {
			return convertPlayerInventorySlot(stackIndex) + 3;
		}

		@Override
		public void runTick(AIHelper aiHelper, TaskOperations taskOperations) {
			f.update(aiHelper);
			if (shouldMove(aiHelper)) {
				moved = true;
			}
			super.runTick(aiHelper, taskOperations);
			f.update(aiHelper);
		}

		protected ItemStack getStack(AIHelper aiHelper) {
			return aiHelper.getMinecraft().player.inventory
					.getStackInSlot(stackIndex);
		}

		@Override
		public boolean isFinished(AIHelper aiHelper) {
			return !shouldMove(aiHelper) || super.isFinished(aiHelper);
		}
	}

	private static final class MoveToBurnable extends MoveToWhatever {

		public MoveToBurnable(int stackIndex, FurnaceTaskList list,
				FurnaceData f) {
			super(stackIndex, list, f);
		}

		@Override
		protected int getToStack(AIHelper aiHelper) {
			return 0;
		}

		@Override
		protected boolean shouldMove(AIHelper aiHelper) {
			return list.couldPutItem(f, getStack(aiHelper));
		}
	}

	private static final class MoveToFuel extends MoveToWhatever {
		public MoveToFuel(int stackIndex, FurnaceTaskList list, FurnaceData f) {
			super(stackIndex, list, f);
		}

		@Override
		protected int getToStack(AIHelper aiHelper) {
			return 1;
		}

		@Override
		protected boolean shouldMove(AIHelper aiHelper) {
			return list.couldPutFuel(f, getStack(aiHelper));
		}

	}

	private static class FurnacePathFinder extends BlockRangeFinder {

		private static final class UpdateFurnaceDataTask extends RunOnceTask {
			private final FurnaceData f;

			private UpdateFurnaceDataTask(FurnaceData f) {
				this.f = f;
			}

			@Override
			protected void runOnce(AIHelper aiHelper, TaskOperations taskOperations) {
				GuiScreen gui = aiHelper.getMinecraft().currentScreen;
				if (!(gui instanceof GuiFurnace)) {
					taskOperations.desync(new StringTaskError("No furnace open"));
				} else {
					f.update((GuiFurnace) gui);
				}
			}
		}

		private final class TakeFurnaceResult extends TakeResultItem {
			private final FurnaceData f;

			private TakeFurnaceResult(FurnaceData f) {
				super(GuiFurnace.class, 2);
				this.f = f;
			}

			@Override
			public boolean isFinished(AIHelper aiHelper) {
				return !list.couldTake(f) || super.isFinished(aiHelper);
			}

			@Override
			public void runTick(AIHelper aiHelper, TaskOperations taskOperations) {
				super.runTick(aiHelper, taskOperations);
				f.update(aiHelper);
			}
		}

		private FurnaceBlockHandler blockHandler;
		private final FurnaceTaskList list;

		public FurnacePathFinder(FurnaceTaskList list) {
			super();
			this.list = list;
		}

		@Override
		protected BlockRangeScanner constructScanner(BlockPos playerPosition) {
			BlockRangeScanner scanner = super.constructScanner(playerPosition);
			blockHandler = new FurnaceBlockHandler();
			scanner.addHandler(blockHandler);
			return scanner;
		}

		@Override
		protected float rateDestination(int distance, int x, int y, int z) {
			ArrayList<FurnaceData> furnaces = blockHandler
					.getReachableForPos(new BlockPos(x, y, z));
			if (furnaces != null) {
				for (FurnaceData f : furnaces) {
					if (list.couldTake(f)) {
						return distance;
					}
					for (ItemStack stack : helper.getMinecraft().player.inventory.mainInventory) {
						if (list.hasSomePutTasks(f, stack)) {
							return distance;
						}
					}
				}
			}
			return -1;
		}

		@Override
		protected void addTasksForTarget(BlockPos currentPos) {
			ArrayList<FurnaceData> furnaces = blockHandler
					.getReachableForPos(currentPos);
			for (FurnaceData f : furnaces) {
				addFurnaceTasks(f);
			}

		}

		private void addFurnaceTasks(final FurnaceData f) {
			ArrayList<AITask> furnaceTasks = new ArrayList<AITask>();
			NonNullList<ItemStack> mainInventory = helper.getMinecraft().player.inventory.mainInventory;
			for (int i = 0; i < mainInventory.size(); i++) {
				ItemStack stack = mainInventory.get(i);
				if (list.couldPutItem(f, stack)) {
					furnaceTasks.add(new MoveToBurnable(i, list, f));
				}
				if (list.couldPutFuel(f, stack)) {
					furnaceTasks.add(new MoveToFuel(i, list, f));
				}
			}
			if (list.couldTake(f)) {
				furnaceTasks.add(new TakeFurnaceResult(f));
			}

			if (!furnaceTasks.isEmpty()) {
				addTask(new UseItemOnBlockAtTask(f.getPos()) {
					@Override
					public boolean isFinished(AIHelper aiHelper) {
						return aiHelper.getMinecraft().currentScreen instanceof GuiFurnace;
					}
				});
				addTask(new WaitTask(5));
				addTask(new UpdateFurnaceDataTask(f));

				for (AITask t : furnaceTasks) {
					addTask(t);
					if (t instanceof WaitCondition) {
						addTask(new ConditionalWaitTask(5, (WaitCondition) t));
					}
				}
				addTask(new UpdateFurnaceDataTask(f));
				addTask(new CloseScreenTask());
			}
		}

	}

	public FurnaceStrategy(FurnaceTaskList list) {
		super(new FurnacePathFinder(list), null);
	}

	@Override
	public void searchTasks(AIHelper helper) {
		// If gui open, close it.
		if (helper.getMinecraft().currentScreen instanceof GuiFurnace) {
			addTask(new CloseScreenTask());
		}
		super.searchTasks(helper);
	}

	@Override
	public String getDescription(AIHelper helper) {
		return "Handle furnace.";
	}
}
