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
package net.famzangl.minecraft.minebot.ai.task.inventory;

import net.famzangl.minecraft.minebot.ai.AIHelper;
import net.famzangl.minecraft.minebot.ai.task.AITask;
import net.famzangl.minecraft.minebot.ai.task.TaskOperations;
import net.famzangl.minecraft.minebot.ai.task.error.StringTaskError;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;

/**
 * Put items in a currently opened container.
 * 
 * @author michael
 *
 */
public abstract class PutItemInContainerTask extends AITask {

	private int slotToPlace = 0;
	private boolean placed = false;
	private boolean isFull;

	@Override
	public boolean isFinished(AIHelper aiHelper) {
		final ContainerScreen<?> screen = (ContainerScreen<?>) aiHelper.getMinecraft().currentScreen;
		return screen != null
				&& placed
				&& (slotToPlace < 0 || isFull || !screen.getContainer()
						.getSlot(slotToPlace).getHasStack());
	}

	@Override
	public void runTick(AIHelper aiHelper, TaskOperations taskOperations) {
		final ContainerScreen<?> screen = (ContainerScreen<?>) aiHelper.getMinecraft().currentScreen;
		if (screen == null) {
			taskOperations.desync(new StringTaskError("Expected container to be open"));
			return;
		}
		slotToPlace = getStackToPut(aiHelper);
		placed = true;
		if (slotToPlace < 0) {
			System.out.println("No item to put.");
			taskOperations.desync(new StringTaskError("No item to put in that slot."));
		} else {
			System.out.println("Moving from slot: " + slotToPlace);
			Slot slot = screen.getContainer().getSlot(slotToPlace);
			int oldContent, newContent = getSlotContentCount(slot);
			do {
				oldContent = newContent;
				aiHelper.getMinecraft().playerController.windowClick(
						screen.getContainer().windowId, slotToPlace, 0, ClickType.QUICK_MOVE,
						aiHelper.getMinecraft().player);
				newContent = getSlotContentCount(slot);
			} while (newContent != oldContent);
			if (newContent > 0) {
				containerIsFull();
			}
		}
	}

	protected void containerIsFull() {
		isFull = true;
	}

	private int getSlotContentCount(Slot slot) {
		return slot.getHasStack() ? slot.getStack().getMaxStackSize() : 0;
	}

	protected abstract int getStackToPut(AIHelper aiHelper);

}
