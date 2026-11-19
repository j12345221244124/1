package dev.lightesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Finds player-placed light sources that sit underground. Scans loaded chunks a couple per tick
 * and caches the result per chunk, so there is no per-frame world scanning.
 */
public final class LightScanner {
	private static final class Entry {
		final int cx, cz;
		final List<BlockPos> lights;
		final long scannedAt;

		Entry(int cx, int cz, List<BlockPos> lights, long scannedAt) {
			this.cx = cx;
			this.cz = cz;
			this.lights = lights;
			this.scannedAt = scannedAt;
		}
	}

	private static final Map<Long, Entry> CACHE = new HashMap<>();
	private static final ArrayDeque<int[]> QUEUE = new ArrayDeque<>();
	private static final Map<Block, Boolean> NAME_CACHE = new HashMap<>();
	private static ClientLevel lastLevel;
	private static int tickCounter;

	// Matched against the block's registry name. Natural light (lava, glow lichen, magma, fire, ...)
	// is deliberately not in this list.
	private static final String[] KEYWORDS = {
			"torch", "lantern", "campfire", "froglight", "candle", "copper_bulb", "end_rod",
			"glowstone", "shroomlight", "redstone_lamp", "furnace", "smoker",
			"beacon", "conduit", "respawn_anchor"
	};

	private LightScanner() {
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	private static boolean nameMatches(Block block) {
		return NAME_CACHE.computeIfAbsent(block, b -> {
			String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
			if (id.contains("torchflower")) return false;
			for (String k : KEYWORDS) {
				if (id.contains(k)) return true;
			}
			return false;
		});
	}

	public static boolean isArtificialLight(BlockState state) {
		// luminance > 0 also filters out unlit lamps / furnaces / candles / bulbs
		return state.getLightEmission() > 0 && nameMatches(state.getBlock());
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
		if (!cfg.enabled) return;
		if (cfg.overworldOnly && level.dimension() != Level.OVERWORLD) {
			clear();
			return;
		}

		if (++tickCounter % 10 == 0) {
			refreshQueue(level, mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4, cfg);
		}

		for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
			int[] c = QUEUE.poll();
			scan(level, c[0], c[1], cfg);
		}
	}

	private static void refreshQueue(ClientLevel level, int centerX, int centerZ, LightEspConfig cfg) {
		int r = (cfg.range >> 4) + 1;
		long now = level.getGameTime();

		CACHE.values().removeIf(e -> Math.abs(e.cx - centerX) > r + 1 || Math.abs(e.cz - centerZ) > r + 1);

		List<int[]> candidates = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int cx = centerX + dx;
				int cz = centerZ + dz;
				if (level.getChunk(cx, cz, ChunkStatus.FULL, false) == null) continue;
				Entry e = CACHE.get(key(cx, cz));
				if (e == null || now - e.scannedAt > 200) candidates.add(new int[]{cx, cz});
			}
		}
		candidates.sort(Comparator.comparingInt(c -> (c[0] - centerX) * (c[0] - centerX) + (c[1] - centerZ) * (c[1] - centerZ)));
		QUEUE.clear();
		QUEUE.addAll(candidates);
	}

	private static void scan(ClientLevel level, int cx, int cz, LightEspConfig cfg) {
		ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
		if (chunk == null) return;

		List<BlockPos> found = new ArrayList<>();
		LevelChunkSection[] sections = chunk.getSections();
		int minY = level.getMinY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(LightScanner::isArtificialLight)) continue;
			int baseY = minY + (i << 4);
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!isArtificialLight(section.getBlockState(x, y, z))) continue;
						pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
						if (isUnderground(level, pos, cfg)) found.add(pos.immutable());
					}
				}
			}
		}
		CACHE.put(key(cx, cz), new Entry(cx, cz, found, level.getGameTime()));
	}

	private static boolean isUnderground(ClientLevel level, BlockPos pos, LightEspConfig cfg) {
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
		// need at least minDepth blocks of cover between this block and the surface
		if (pos.getY() > top - 1 - cfg.minDepth) return false;
		return level.getBrightness(LightLayer.SKY, pos) == 0;
	}

	public static void forEachInRange(double x, double y, double z, int range, Consumer<BlockPos> consumer) {
		double r2 = (double) range * range;
		for (Entry e : CACHE.values()) {
			for (BlockPos p : e.lights) {
				double dx = p.getX() + 0.5 - x;
				double dy = p.getY() + 0.5 - y;
				double dz = p.getZ() + 0.5 - z;
				if (dx * dx + dy * dy + dz * dz <= r2) consumer.accept(p);
			}
		}
	}
}
