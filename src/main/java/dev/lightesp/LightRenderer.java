package dev.lightesp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Draws boxes with Minecraft's own gizmo (debug shape) system, always on top so they show through walls. */
public final class LightRenderer {
	private static final int MAX_DRAWN = 2000;
	private static final double PAD = 0.002;

	private LightRenderer() {
	}

	public static void render() {
		LightEspConfig cfg = LightEspConfig.get();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		double vx = viewer.getX(), vy = viewer.getY(), vz = viewer.getZ();

		if (cfg.enabled) {
			List<BlockPos> targets = new ArrayList<>();
			LightScanner.forEachInRange(vx, vy, vz, cfg.range, p -> {
				if (targets.size() < MAX_DRAWN) targets.add(p);
			});
			for (BlockPos p : targets) {
				drawBox(p, cfg.color(), cfg.filled);
			}
		}

		if (cfg.storageEsp || cfg.spawnerEsp) {
			int[] count = {0};
			StorageScanner.forEachInRange(vx, vy, vz, cfg.storageRange, hit -> {
				if (count[0] >= MAX_DRAWN) return;
				boolean spawner = hit.type() == StorageScanner.SPAWNER;
				if (spawner ? !cfg.spawnerEsp : !cfg.storageEsp) return;
				count[0]++;
				drawBox(hit.pos(), StorageScanner.COLORS[hit.type()], cfg.filled);
			});
		}
	}

	private static void drawBox(BlockPos p, int color, boolean filled) {
		int rgb = color & 0x00FFFFFF;
		int lineColor = 0xFF000000 | rgb;
		int fillColor = 0x55000000 | rgb;
		AABB box = new AABB(
				p.getX() - PAD, p.getY() - PAD, p.getZ() - PAD,
				p.getX() + 1 + PAD, p.getY() + 1 + PAD, p.getZ() + 1 + PAD);
		if (filled) {
			Gizmos.cuboid(box, GizmoStyle.fill(fillColor)).setAlwaysOnTop();
		}
		Gizmos.cuboid(box, GizmoStyle.stroke(lineColor)).setAlwaysOnTop();
	}
}
