package com.example.meowchest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class MeowChest implements ModInitializer {

	@Override
	public void onInitialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND
					|| player.isSpectator() || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof ChestBlock)) return InteractionResult.PASS;
			if (ChestBlock.isChestBlockedAt(level, pos)) return InteractionResult.PASS;

			// Miau
			level.playSound(null, pos, SoundEvents.CAT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);

			// Doppelkiste: zweite Hälfte VOR dem Flip ermitteln
			BlockPos otherPos = null;
			if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
			}

			flip(level, pos, state);
			if (otherPos != null) {
				BlockState other = level.getBlockState(otherPos);
				if (other.getBlock() instanceof ChestBlock) flip(level, otherPos, other);
			}

			return InteractionResult.PASS; // Kiste öffnet sich normal
		});
	}

	/** 180° drehen. Bei Doppelkisten muss LEFT/RIGHT mitgetauscht werden. */
	private static void flip(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(ChestBlock.FACING);
		ChestType type = state.getValue(ChestBlock.TYPE);
		BlockState flipped = state
				.setValue(ChestBlock.FACING, facing.getOpposite())
				.setValue(ChestBlock.TYPE, type.getOpposite());
		level.setBlock(pos, flipped, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
