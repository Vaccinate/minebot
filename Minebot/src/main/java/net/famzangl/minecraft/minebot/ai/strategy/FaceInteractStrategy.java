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
import net.minecraft.entity.Entity;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;

import java.util.ArrayList;
import java.util.function.Predicate;

public abstract class FaceInteractStrategy extends AIStrategy {

	private final class NotOnBlacklistSelector implements
			Predicate<Entity> {
		@Override
		public boolean test(Entity var1) {
			return !blacklist.contains(var1);
		}
	}

	private static final int DISTANCE = 20;
	private int ticksRun;
	private int ticksSlow;
	private Entity lastFound;
	private final ArrayList<Entity> blacklist = new ArrayList<Entity>();

	@Override
	public boolean checkShouldTakeOver(AIHelper helper) {
		return getCloseEntity(helper) != null;
	}

	@Override
	protected TickResult onGameTick(AIHelper helper) {

		final Entity found = getCloseEntity(helper);

		if (found == null) {
			ticksRun = 0;
			ticksSlow = 0;
			lastFound = null;
			return TickResult.NO_MORE_WORK;
		} else if (lastFound != found) {
			ticksRun = 0;
			ticksSlow = 0;
		}

		final RayTraceResult position = helper.getObjectMouseOver();
		if (position != null && position instanceof EntityRayTraceResult
				&& doInteractWithCurrent(((EntityRayTraceResult) position).getEntity(), helper)) {
			ticksRun = 0;
		} else {
			double dx = helper.getMinecraft().player.getMotion().x;
			double dz = helper.getMinecraft().player.getMotion().z;
			final double speed = dx
					* dx
					+ dz
					* dz;
			helper.face(found.getPosX(), found.getPosY(), found.getPosZ());
			final MovementInput movement = new MovementInput();
			if (speed < 0.01 && ticksRun > 8) {
				movement.jump = ++ticksSlow > 5;
			} else {
				ticksSlow = 0;
			}
			movement.moveForward = 1;
			helper.overrideMovement(movement);
			ticksRun++;
			if (ticksSlow > 3 * 20 || ticksRun > 20 * 20) {
				blacklist .add(found);
			}
		}
		lastFound = found;
		return TickResult.TICK_HANDLED;
	}

	protected boolean doInteractWithCurrent(Entity entityHit, AIHelper helper) {
		if (entitiesToInteract(helper).test(entityHit)) {
			doInteract(entityHit, helper);
			return true;
		} else {
			return false;
		}
	}

	protected void doInteract(Entity entityHit, AIHelper helper) {
		helper.overrideUseItem();
	}

	private Entity getCloseEntity(AIHelper helper) {
		Predicate<Entity> collect = entitiesToFace(helper);
		collect = collect.and(new NotOnBlacklistSelector());
		return helper.getClosestEntity(DISTANCE, collect);
	}

	protected abstract Predicate<Entity> entitiesToInteract(AIHelper helper);

	protected Predicate<Entity> entitiesToFace(AIHelper helper) {
		return entitiesToInteract(helper);
	}
}
