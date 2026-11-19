package dev.lightesp;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
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
		if (!cfg.enabled) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;

		List<BlockPos> targets = new ArrayList<>();
		LightScanner.forEachInRange(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), cfg.range, p -> {
			if (targets.size() < MAX_DRAWN) targets.add(p);
		});
		if (targets.isEmpty()) return;

		int rgb = cfg.color() & 0x00FFFFFF;
		int lineColor = 0xFF000000 | rgb;
		int fillColor = 0x55000000 | rgb;

		for (BlockPos p : targets) {
			AABB box = new AABB(
					p.getX() - PAD, p.getY() - PAD, p.getZ() - PAD,
					p.getX() + 1 + PAD, p.getY() + 1 + PAD, p.getZ() + 1 + PAD);
			if (cfg.filled) {
				Gizmos.cuboid(box, GizmoStyle.fill(fillColor)).setAlwaysOnTop();
			}
			Gizmos.cuboid(box, GizmoStyle.stroke(lineColor)).setAlwaysOnTop();
		}
	}
}
