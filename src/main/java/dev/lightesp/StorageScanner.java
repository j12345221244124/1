package dev.lightesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Finds chests, barrels, shulker boxes, ender chests, hoppers, etc. and spawners in loaded chunks. */
public final class StorageScanner {
	public static final int CHEST = 0, TRAPPED = 1, ENDER = 2, BARREL = 3, SHULKER = 4, MISC = 5, SPAWNER = 6;
	public static final int[] COLORS = {
			0xFFFF9F1C, // chest - orange
			0xFFFF3B30, // trapped chest - red
			0xFFB14DFF, // ender chest - purple
			0xFFC8A165, // barrel - tan
			0xFFFF2DF1, // shulker box - magenta
			0xFFB0B0B0, // hopper / dispenser / dropper - grey
			0xFF34C759  // spawner - green
	};

	public record Hit(BlockPos pos, int type) {
	}

	private static final class Entry {
		final int cx, cz;
		final List<Hit> hits;
		final long scannedAt;

		Entry(int cx, int cz, List<Hit> hits, long scannedAt) {
			this.cx = cx;
			this.cz = cz;
			this.hits = hits;
			this.scannedAt = scannedAt;
		}
	}

	private static final Map<Long, Entry> CACHE = new HashMap<>();
	private static final ArrayDeque<int[]> QUEUE = new ArrayDeque<>();
	private static final Map<Block, Integer> TYPE_CACHE = new HashMap<>();
	private static ClientLevel lastLevel;
	private static int tickCounter;

	private StorageScanner() {
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	/** Returns one of the type constants, or -1 if the block is not interesting. */
	private static int classify(Block block) {
		return TYPE_CACHE.computeIfAbsent(block, b -> {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.contains("spawner")) return SPAWNER;
			if (id.equals("chest")) return CHEST;
			if (id.equals("trapped_chest")) return TRAPPED;
			if (id.equals("ender_chest")) return ENDER;
			if (id.equals("barrel")) return BARREL;
			if (id.endsWith("shulker_box")) return SHULKER;
			if (id.equals("hopper") || id.equals("dispenser") || id.equals("dropper")) return MISC;
			return -1;
		});
	}

	public static void clear() {
		CACHE.clear();
		QUEUE.clear();
	}

	public static void tick(Minecraft mc) {
		LightEspConfig cfg = LightEspConfig.get();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			clear();
			lastLevel = null;
			return;
		}
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (!cfg.storageEsp && !cfg.spawnerEsp) {
			clear();
			return;
		}

		if (++tickCounter % 10 == 0) {
			refreshQueue(level, mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4, cfg);
		}

		for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
			int[] c = QUEUE.poll();
			scan(level, c[0], c[1]);
		}
	}

	private static void refreshQueue(ClientLevel level, int centerX, int centerZ, LightEspConfig cfg) {
		int r = (cfg.storageRange >> 4) + 1;
		long now = level.getGameTime();

		CACHE.values().removeIf(e -> Math.abs(e.cx - centerX) > r + 1 || Math.abs(e.cz - centerZ) > r + 1);

		List<int[]> candidates = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int cx = centerX + dx;
				int cz = centerZ + dz;
				if (level.getChunk(cx, cz, ChunkStatus.FULL, false) == null) continue;
				Entry e = CACHE.get(key(cx, cz));
				if (e == null || now - e.scannedAt > 100) candidates.add(new int[]{cx, cz});
			}
		}
		candidates.sort(Comparator.comparingInt(c -> (c[0] - centerX) * (c[0] - centerX) + (c[1] - centerZ) * (c[1] - centerZ)));
		QUEUE.clear();
		QUEUE.addAll(candidates);
	}

	private static void scan(ClientLevel level, int cx, int cz) {
		ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return;

		List<Hit> found = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minY = level.getMinY();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> classify(s.getBlock()) >= 0)) continue;
			int baseY = minY + (i << 4);
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int type = classify(section.getBlockState(x, y, z).getBlock());
						if (type < 0) continue;
						found.add(new Hit(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z), type));
					}
				}
			}
		}
		CACHE.put(key(cx, cz), new Entry(cx, cz, found, level.getGameTime()));
	}

	public static void forEachInRange(double x, double y, double z, int range, Consumer<Hit> consumer) {
		double r2 = (double) range * range;
		for (Entry e : CACHE.values()) {
			for (Hit h : e.hits) {
				BlockPos p = h.pos();
				double dx = p.getX() + 0.5 - x;
				double dy = p.getY() + 0.5 - y;
				double dz = p.getZ() + 0.5 - z;
				if (dx * dx + dy * dy + dz * dz <= r2) consumer.accept(h);
			}
		}
	}
}
