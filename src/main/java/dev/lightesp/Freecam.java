package dev.lightesp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;

/**
 * Freecam without any mixins: a hidden marker entity becomes the camera, and the real player's
 * input object is swapped for an empty one so the character stands still while you fly around.
 * Note: mouse look still turns the real player's head, so the server sees you looking around.
 */
public final class Freecam {
	private static boolean active;
	private static Marker cam;
	private static ClientLevel camLevel;
	private static ClientInput savedInput;

	private Freecam() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void toggle(Minecraft mc) {
		if (active) {
			stop(mc);
		} else {
			start(mc);
		}
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("Freecam: " + (active ? "ON" : "OFF")));
		}
	}

	private static void start(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return;

		camLevel = mc.level;
		cam = new Marker(EntityType.MARKER, mc.level);
		cam.setPos(p.getX(), p.getEyeY(), p.getZ());
		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();
		cam.setYRot(p.getYRot());
		cam.setXRot(p.getXRot());
		cam.yRotO = cam.getYRot();
		cam.xRotO = cam.getXRot();

		savedInput = p.input;
		p.input = new ClientInput(); // does nothing, so the real player stays put
		mc.setCameraEntity(cam);
		active = true;
	}

	private static void stop(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p != null) {
			if (savedInput != null) p.input = savedInput;
			mc.setCameraEntity(p);
		}
		reset();
	}

	private static void reset() {
		active = false;
		cam = null;
		camLevel = null;
		savedInput = null;
	}

	/** Called every frame: keeps the view rotation in sync with the mouse without lag. */
	public static void frame(Minecraft mc) {
		if (!active || cam == null || mc.player == null) return;
		cam.setYRot(mc.player.getYRot());
		cam.setXRot(mc.player.getXRot());
		cam.yRotO = cam.getYRot();
		cam.xRotO = cam.getXRot();
	}

	/** Called every client tick: moves the camera. */
	public static void tick(Minecraft mc) {
		if (!active) return;
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.level != camLevel) {
			reset();
			return;
		}
		if (mc.getCameraEntity() != cam) mc.setCameraEntity(cam);

		cam.xo = cam.getX();
		cam.yo = cam.getY();
		cam.zo = cam.getZ();

		// only fly while the mouse is captured (no menu / chat open)
		if (!mc.mouseHandler.isMouseGrabbed()) return;

		double yaw = Math.toRadians(cam.getYRot());
		double pitch = Math.toRadians(cam.getXRot());
		double fx = -Math.sin(yaw) * Math.cos(pitch);
		double fy = -Math.sin(pitch);
		double fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);

		double mx = 0, my = 0, mz = 0;
		if (mc.options.keyUp.isDown()) { mx += fx; my += fy; mz += fz; }
		if (mc.options.keyDown.isDown()) { mx -= fx; my -= fy; mz -= fz; }
		if (mc.options.keyRight.isDown()) { mx += rx; mz += rz; }
		if (mc.options.keyLeft.isDown()) { mx -= rx; mz -= rz; }
		if (mc.options.keyJump.isDown()) my += 1;
		if (mc.options.keyShift.isDown()) my -= 1;

		double len = Math.sqrt(mx * mx + my * my + mz * mz);
		if (len < 1.0E-4) return;

		double speed = LightEspConfig.get().freecamSpeed * 0.1 * (mc.options.keySprint.isDown() ? 2.5 : 1.0);
		cam.setPos(cam.getX() + mx / len * speed, cam.getY() + my / len * speed, cam.getZ() + mz / len * speed);
	}
}
