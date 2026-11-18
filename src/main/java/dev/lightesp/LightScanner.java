package dev.lightesp;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.function.Consumer;

/**
 * Finds player-placed light sources that sit underground. Scans loaded chunks a couple
 * per tick and caches the result per chunk, so there is no per-frame world scanning.
 */
public final class LightScanner {
	private static final class Entry {
		final List<BlockPos> lights;
		final long scannedAt;

		Entry(List<BlockPos> lights, long scannedAt) {
			this.lights = lights;
			this.scannedAt = scannedAt;
		}
	}

	private static final Map<Long, Entry> CACHE = new HashMap<>();
	private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
	private static ClientWorld lastWorld;
	private static int tickCounter;

	// Natural light sources (lava, glow lichen, magma, fire, ...) are deliberately NOT listed.
	private static final Set<Block> LIGHT_BLOCKS = new HashSet<>(Arrays.asList(
			Blocks.TORCH, Blocks.WALL_TORCH,
			Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
			Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
			Blocks.LANTERN, Blocks.SOUL_LANTERN,
			Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT,
			Blocks.JACK_O_LANTERN, Blocks.REDSTONE_LAMP,
			Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
			Blocks.END_ROD,
			Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
			Blocks.BEACON, Blocks.CONDUIT, Blocks.RESPAWN_ANCHOR,
			Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER,
			Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
			Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB
	));

	private LightScanner() {
	}

	public static boolean isArtificialLight(BlockState state) {
		// getLuminance() > 0 also filters out unlit lamps/furnaces/candles/bulbs.
		if (state.getLuminance() <= 0) return false;
		return LIGHT_BLOCKS.contains(state.getBlock()) || state.isIn(BlockTags.CANDLES);
	}

	public static void clear() {
		CACHE.clear();
		QUEUE.clear();
	}

	public static void tick(MinecraftClient mc) {
		LightEspConfig cfg = LightEspConfig.get();
		ClientWorld world = mc.world;
		if (world == null || mc.player == null) {
			clear();
			lastWorld = null;
			return;
		}
		if (world != lastWorld) {
			clear();
			lastWorld = world;
		}
		if (!cfg.enabled) return;
		if (cfg.overworldOnly && world.getRegistryKey() != World.OVERWORLD) {
			clear();
			return;
		}

		if (++tickCounter % 10 == 0) refreshQueue(world, mc.player.getChunkPos(), cfg);

		for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
			scan(world, QUEUE.poll(), cfg);
		}
	}

	private static void refreshQueue(ClientWorld world, ChunkPos center, LightEspConfig cfg) {
		int r = (cfg.range >> 4) + 1;
		long now = world.getTime();

		CACHE.keySet().removeIf(key -> Math.abs(ChunkPos.getPackedX(key) - center.x) > r + 1
				|| Math.abs(ChunkPos.getPackedZ(key) - center.z) > r + 1);

		List<ChunkPos> candidates = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				int cx = center.x + dx;
				int cz = center.z + dz;
				if (world.getChunk(cx, cz, ChunkStatus.FULL, false) == null) continue;
				Entry e = CACHE.get(ChunkPos.toLong(cx, cz));
				if (e == null || now - e.scannedAt > 200) candidates.add(new ChunkPos(cx, cz));
			}
		}
		candidates.sort(Comparator.comparingInt(c -> (c.x - center.x) * (c.x - center.x) + (c.z - center.z) * (c.z - center.z)));
		QUEUE.clear();
		QUEUE.addAll(candidates);
	}

	private static void scan(ClientWorld world, ChunkPos cp, LightEspConfig cfg) {
		Chunk chunk = world.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
		if (chunk == null) return;

		List<BlockPos> found = new ArrayList<>();
		ChunkSection[] sections = chunk.getSectionArray();
		int bottomY = chunk.getBottomY();
		BlockPos.Mutable pos = new BlockPos.Mutable();

		for (int i = 0; i < sections.length; i++) {
			ChunkSection section = sections[i];
			if (section == null || section.isEmpty() || !section.hasAny(LightScanner::isArtificialLight)) continue;
			int baseY = bottomY + (i << 4);
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!isArtificialLight(section.getBlockState(x, y, z))) continue;
						pos.set((cp.x << 4) + x, baseY + y, (cp.z << 4) + z);
						if (isUnderground(world, pos, cfg)) found.add(pos.toImmutable());
					}
				}
			}
		}
		CACHE.put(ChunkPos.toLong(cp.x, cp.z), new Entry(found, world.getTime()));
	}

	private static boolean isUnderground(ClientWorld world, BlockPos pos, LightEspConfig cfg) {
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
		// need at least minDepth blocks of cover between this block and the surface
		if (pos.getY() > top - 1 - cfg.minDepth) return false;
		return world.getLightLevel(LightType.SKY, pos) == 0;
	}

	public static void forEachInRange(Vec3d cam, int range, Consumer<BlockPos> consumer) {
		double r2 = (double) range * range;
		for (Entry e : CACHE.values()) {
			for (BlockPos p : e.lights) {
				double dx = p.getX() + 0.5 - cam.x;
				double dy = p.getY() + 0.5 - cam.y;
				double dz = p.getZ() + 0.5 - cam.z;
				if (dx * dx + dy * dy + dz * dz <= r2) consumer.accept(p);
			}
		}
	}
}
