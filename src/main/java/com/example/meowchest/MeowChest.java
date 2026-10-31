package com.example.meowchest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class MeowChest implements ModInitializer {

	private static final SoundEvent MEOW = SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace("entity.cat.ambient"));

	// Ohren-Einstellungen
	private static final String EAR_BLOCK = "minecraft:orange_concrete";
	private static final double EAR_SIZE = 0.26;   // Kantenlänge (Raute)
	private static final double EAR_DEPTH = 0.10;  // Dicke
	private static final double EAR_SIDE = 0.30;   // Abstand von der Mitte
	private static final double EAR_Y = 0.875;     // Höhe der Deckeloberkante

	// Animation
	private static final int POP_DELAY = 1;
	private static final int START_DELAY = 5;
	private static final int STEPS = 8;
	private static final int STEP_TICKS = 3;

	private record Task(long due, Runnable run) {}
	private record Key(Level level, long pos) {}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final Set<Key> BUSY = new HashSet<>();
	private static long tick = 0;
	private static int counter = 0;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tick++;
			for (int i = 0; i < TASKS.size(); ) {
				Task t = TASKS.get(i);
				if (t.due() <= tick) {
					TASKS.remove(i);
					t.run().run();
				} else {
					i++;
				}
			}
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown()) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof ChestBlock)) return InteractionResult.PASS;
			if (ChestBlock.isChestBlockedAt(level, pos)) return InteractionResult.PASS;

			Key key = new Key(level, pos.asLong());
			if (!BUSY.add(key)) return InteractionResult.PASS; // Animation läuft schon

			BlockPos otherPos = null;
			if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
			}

			level.playSound(null, pos, MEOW, SoundSource.BLOCKS, 1.0F, 1.0F);
			animate(serverLevel, pos, state.getValue(ChestBlock.FACING), otherPos, key);

			return InteractionResult.PASS; // Kiste öffnet sich normal
		});
	}

	private static void animate(ServerLevel level, BlockPos pos, Direction facing, BlockPos otherPos, Key key) {
		int id = ++counter;
		String tagA = "mc" + id + "a";
		String tagB = "mc" + id + "b";
		double cx = pos.getX() + 0.5, cy = pos.getY(), cz = pos.getZ() + 0.5;
		double base = facing.getAxis() == Direction.Axis.X ? Math.PI / 2 : 0;

		// Ohren erzeugen (winzig), Herzchen
		summon(level, cx, cy, cz, tagA, transform(+EAR_SIDE, base, 0.001));
		summon(level, cx, cy, cz, tagB, transform(-EAR_SIDE, base, 0.001));
		cmd(level, "particle minecraft:heart " + n(cx) + " " + n(cy + 1.1) + " " + n(cz) + " 0.3 0.2 0.3 0 4");

		// Aufpoppen
		later(POP_DELAY, () -> {
			merge(level, tagA, transform(+EAR_SIDE, base, 1.0), 4);
			merge(level, tagB, transform(-EAR_SIDE, base, 1.0), 4);
		});

		// Kreisen um 180°
		for (int k = 1; k <= STEPS; k++) {
			double alpha = base + Math.PI * k / STEPS;
			later(START_DELAY + (k - 1) * STEP_TICKS, () -> {
				merge(level, tagA, transform(+EAR_SIDE, alpha, 1.0), STEP_TICKS);
				merge(level, tagB, transform(-EAR_SIDE, alpha, 1.0), STEP_TICKS);
			});
		}

		int end = START_DELAY + STEPS * STEP_TICKS;

		// Kiste dreht sich am Ende
		later(end, () -> {
			flip(level, pos);
			if (otherPos != null) flip(level, otherPos);
		});

		// Ohren schrumpfen und verschwinden
		later(end + 10, () -> {
			double a = base + Math.PI;
			merge(level, tagA, transform(+EAR_SIDE, a, 0.001), 5);
			merge(level, tagB, transform(-EAR_SIDE, a, 0.001), 5);
		});
		later(end + 17, () -> {
			cmd(level, "kill @e[type=minecraft:block_display,tag=" + tagA + "]");
			cmd(level, "kill @e[type=minecraft:block_display,tag=" + tagB + "]");
			BUSY.remove(key);
		});
	}

	/** Transformation einer Ohr-Raute, um die Kistenmitte um Winkel alpha gedreht. */
	private static String transform(double side, double alpha, double scale) {
		double s = EAR_SIZE * scale;
		double d = EAR_DEPTH * scale;

		// L = Drehung um Y (alpha) nach Drehung um Z (45° -> Raute)
		double sy = Math.sin(alpha / 2), cy = Math.cos(alpha / 2);
		double sz = Math.sin(Math.PI / 8), cz = Math.cos(Math.PI / 8);
		double[] q = { sy * sz, sy * cz, cy * sz, cy * cz }; // x, y, z, w

		// Wunschposition der Ohrmitte relativ zur Kistenmitte (um Y gedreht)
		double px = side * Math.cos(alpha);
		double pz = -side * Math.sin(alpha);
		double py = EAR_Y;

		// Würfelmitte (nach Skalierung) wird mitgedreht
		double[] half = rotate(q, s / 2, s / 2, d / 2);

		double tx = px - half[0], ty = py - half[1], tz = pz - half[2];

		return "{left_rotation:[" + n(q[0]) + "f," + n(q[1]) + "f," + n(q[2]) + "f," + n(q[3]) + "f],"
				+ "right_rotation:[0f,0f,0f,1f],"
				+ "scale:[" + n(s) + "f," + n(s) + "f," + n(d) + "f],"
				+ "translation:[" + n(tx) + "f," + n(ty) + "f," + n(tz) + "f]}";
	}

	/** Vektor mit Quaternion q = (x,y,z,w) drehen. */
	private static double[] rotate(double[] q, double vx, double vy, double vz) {
		double ux = q[0], uy = q[1], uz = q[2], w = q[3];
		// t = 2 * (u x v)
		double tx = 2 * (uy * vz - uz * vy);
		double ty = 2 * (uz * vx - ux * vz);
		double tz = 2 * (ux * vy - uy * vx);
		// v' = v + w * t + u x t
		return new double[] {
				vx + w * tx + (uy * tz - uz * ty),
				vy + w * ty + (uz * tx - ux * tz),
				vz + w * tz + (ux * ty - uy * tx)
		};
	}

	private static void summon(ServerLevel level, double x, double y, double z, String tag, String transformation) {
		cmd(level, "summon minecraft:block_display " + n(x) + " " + n(y) + " " + n(z)
				+ " {block_state:{Name:\"" + EAR_BLOCK + "\"},Tags:[\"meowchest_ear\",\"" + tag + "\"],"
				+ "transformation:" + transformation + "}");
	}

	private static void merge(ServerLevel level, String tag, String transformation, int duration) {
		cmd(level, "data merge entity @e[type=minecraft:block_display,tag=" + tag + ",limit=1] "
				+ "{start_interpolation:0,interpolation_duration:" + duration + ",transformation:" + transformation + "}");
	}

	private static void cmd(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		server.getCommands().performPrefixedCommand(source, command);
	}

	private static void later(int delay, Runnable run) {
		TASKS.add(new Task(tick + delay, run));
	}

	private static String n(double v) {
		return String.format(Locale.ROOT, "%.4f", v);
	}

	/** 180° drehen. Bei Doppelkisten muss LEFT/RIGHT mitgetauscht werden. */
	private static void flip(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof ChestBlock)) return;
		Direction facing = state.getValue(ChestBlock.FACING);
		ChestType type = state.getValue(ChestBlock.TYPE);
		BlockState flipped = state
				.setValue(ChestBlock.FACING, facing.getOpposite())
				.setValue(ChestBlock.TYPE, type.getOpposite());
		level.setBlock(pos, flipped, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}
}
