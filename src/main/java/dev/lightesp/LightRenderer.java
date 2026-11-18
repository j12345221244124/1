package dev.lightesp;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class LightRenderer {
	private static final int MAX_DRAWN = 3000;
	private static final float PAD = 0.002f;

	private LightRenderer() {
	}

	public static void render(WorldRenderContext ctx) {
		LightEspConfig cfg = LightEspConfig.get();
		if (!cfg.enabled) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null || mc.player == null) return;

		MatrixStack matrices = ctx.matrixStack();
		if (matrices == null) return;

		Vec3d cam = ctx.camera().getPos();
		List<BlockPos> targets = new ArrayList<>();
		LightScanner.forEachInRange(cam, cfg.range, p -> {
			if (targets.size() < MAX_DRAWN) targets.add(p);
		});
		if (targets.isEmpty()) return;

		int rgb = cfg.color() & 0x00FFFFFF;
		int lineColor = 0xFF000000 | rgb;
		int fillColor = 0x55000000 | rgb;

		matrices.push();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.peek().getPositionMatrix();

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableDepthTest(); // draw through walls
		RenderSystem.disableCull();
		RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

		if (cfg.filled) {
			BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
			for (BlockPos p : targets) fillBox(fill, m, p, fillColor);
			BufferRenderer.drawWithGlobalProgram(fill.end());
		}

		BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
		for (BlockPos p : targets) outlineBox(lines, m, p, lineColor);
		BufferRenderer.drawWithGlobalProgram(lines.end());

		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.disableBlend();
		matrices.pop();
	}

	private static void v(BufferBuilder b, Matrix4f m, float x, float y, float z, int argb) {
		b.vertex(m, x, y, z).color(argb);
	}

	private static void fillBox(BufferBuilder b, Matrix4f m, BlockPos p, int c) {
		float x1 = p.getX() - PAD, y1 = p.getY() - PAD, z1 = p.getZ() - PAD;
		float x2 = p.getX() + 1 + PAD, y2 = p.getY() + 1 + PAD, z2 = p.getZ() + 1 + PAD;

		// bottom
		v(b, m, x1, y1, z1, c); v(b, m, x2, y1, z1, c); v(b, m, x2, y1, z2, c); v(b, m, x1, y1, z2, c);
		// top
		v(b, m, x1, y2, z1, c); v(b, m, x1, y2, z2, c); v(b, m, x2, y2, z2, c); v(b, m, x2, y2, z1, c);
		// north
		v(b, m, x1, y1, z1, c); v(b, m, x1, y2, z1, c); v(b, m, x2, y2, z1, c); v(b, m, x2, y1, z1, c);
		// south
		v(b, m, x1, y1, z2, c); v(b, m, x2, y1, z2, c); v(b, m, x2, y2, z2, c); v(b, m, x1, y2, z2, c);
		// west
		v(b, m, x1, y1, z1, c); v(b, m, x1, y1, z2, c); v(b, m, x1, y2, z2, c); v(b, m, x1, y2, z1, c);
		// east
		v(b, m, x2, y1, z1, c); v(b, m, x2, y2, z1, c); v(b, m, x2, y2, z2, c); v(b, m, x2, y1, z2, c);
	}

	private static void line(BufferBuilder b, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, int c) {
		v(b, m, ax, ay, az, c);
		v(b, m, bx, by, bz, c);
	}

	private static void outlineBox(BufferBuilder b, Matrix4f m, BlockPos p, int c) {
		float x1 = p.getX() - PAD, y1 = p.getY() - PAD, z1 = p.getZ() - PAD;
		float x2 = p.getX() + 1 + PAD, y2 = p.getY() + 1 + PAD, z2 = p.getZ() + 1 + PAD;

		// bottom ring
		line(b, m, x1, y1, z1, x2, y1, z1, c);
		line(b, m, x2, y1, z1, x2, y1, z2, c);
		line(b, m, x2, y1, z2, x1, y1, z2, c);
		line(b, m, x1, y1, z2, x1, y1, z1, c);
		// top ring
		line(b, m, x1, y2, z1, x2, y2, z1, c);
		line(b, m, x2, y2, z1, x2, y2, z2, c);
		line(b, m, x2, y2, z2, x1, y2, z2, c);
		line(b, m, x1, y2, z2, x1, y2, z1, c);
		// verticals
		line(b, m, x1, y1, z1, x1, y2, z1, c);
		line(b, m, x2, y1, z1, x2, y2, z1, c);
		line(b, m, x2, y1, z2, x2, y2, z2, c);
		line(b, m, x1, y1, z2, x1, y2, z2, c);
	}
}
