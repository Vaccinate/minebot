package net.famzangl.minecraft.minebot.ai.path.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.IFluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.annotation.Nullable;
import java.util.HashMap;

public class BlockBoundsCache {
	private static final Marker MARKER_BOUNDS_PROBLEM = MarkerManager
			.getMarker("missing bounds");
	private static final Logger LOGGER = LogManager
			.getLogger(BlockBounds.class);

	private static BlockBounds[] bounds;

	private BlockBoundsCache() {
	}

	public static BlockBounds getBounds(BlockState blockState) {
		return getBounds(Block.getStateId(blockState));
	}

	public static BlockBounds getBounds(int blockStateId) {
		return bounds[blockStateId];
	}

	public static void initialize() {
		bounds = new BlockBounds[16 * 4096];
		HashMap<BlockBounds, BlockBounds> usedBounds = new HashMap<BlockBounds, BlockBounds>();
		usedBounds.put(BlockBounds.FULL_BLOCK, BlockBounds.FULL_BLOCK);
		usedBounds.put(BlockBounds.LOWER_HALF_BLOCK, BlockBounds.LOWER_HALF_BLOCK);
		usedBounds.put(BlockBounds.UPPER_HALF_BLOCK, BlockBounds.UPPER_HALF_BLOCK);
		for (int i = 0; i < bounds.length; i++) {
			try {
				BlockBounds bound = attemptLoad(i);
				usedBounds.computeIfAbsent(bound, __ -> bound);
				// Reduces instances we keep in memory
				bounds[i] = usedBounds.get(bound);
			} catch (Throwable e) {
				LOGGER.warn(MARKER_BOUNDS_PROBLEM,
						"Could not create bounds for " + Block.getStateById(i));
				bounds[i] = BlockBounds.FULL_BLOCK;
			}
		}
	}

	private static BlockBounds attemptLoad(int blockStateId) {
		BlockState state = Block.getStateById(blockStateId);
		IBlockReader world = new FakeBlockReader(state);

		return new BlockBounds(state.getCollisionShape(world, new BlockPos(0, 100, 0)));
	}

    private static class FakeBlockReader implements IBlockReader {
        private final BlockState state;

        public FakeBlockReader(BlockState state) {
            this.state = state;
        }

        @Nullable
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (Pos.ZERO.equals(pos)) {
                return state;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public IFluidState getFluidState(BlockPos pos) {
            return null;
        }

        @Override
        public int getLightValue(BlockPos pos) {
            return 0;
        }

        @Override
        public int getMaxLightLevel() {
            return 7;
        }

        @Override
        public int getHeight() {
            return 255;
        }

        @Override
        public BlockRayTraceResult rayTraceBlocks(RayTraceContext context) {
            return null;
        }

        @Nullable
        @Override
        public BlockRayTraceResult rayTraceBlocks(Vec3d p_217296_1_, Vec3d p_217296_2_, BlockPos p_217296_3_, VoxelShape p_217296_4_, BlockState p_217296_5_) {
            return null;
        }
    }
}
