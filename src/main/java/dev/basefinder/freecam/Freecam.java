package dev.basefinder.freecam;

import dev.basefinder.config.BaseFinderConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Detached camera without any mixins. A client-side dummy entity becomes the camera entity; the real player's
 * movement input is swapped for an empty one so the body stays put, and the camera is flown with the normal
 * movement keys. Mouse movement still turns the real player (the camera copies its look direction).
 */
public final class Freecam {
	private static boolean active;
	private static RemotePlayer camera;
	private static ClientInput savedInput;
	private static double x;
	private static double y;
	private static double z;

	private Freecam() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void toggle(Minecraft mc) {
		if (active) {
			disable(mc);
		} else {
			enable(mc);
		}
	}

	private static void enable(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			return;
		}
		camera = new RemotePlayer(level, player.getGameProfile());
		x = player.getX();
		y = player.getY();
		z = player.getZ();
		place(player);
		camera.xo = x;
		camera.yo = y;
		camera.zo = z;
		camera.yRotO = player.getYRot();
		camera.xRotO = player.getXRot();

		savedInput = player.input;
		player.input = new ClientInput();
		mc.setCameraEntity(camera);
		active = true;
	}

	private static void disable(Minecraft mc) {
		if (mc.player != null) {
			if (savedInput != null) {
				mc.player.input = savedInput;
			}
			mc.setCameraEntity(mc.player);
		}
		reset();
	}

	/** Forget all state, used on disconnect. */
	public static void reset() {
		active = false;
		camera = null;
		savedInput = null;
	}

	public static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || camera == null) {
			reset();
			return;
		}
		camera.xo = x;
		camera.yo = y;
		camera.zo = z;
		camera.yRotO = camera.getYRot();
		camera.xRotO = camera.getXRot();

		if (mc.screen == null) {
			Options o = mc.options;
			Vec3 look = player.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() > 1.0E-6 ? right.normalize() : Vec3.ZERO;
			double mx = 0;
			double my = 0;
			double mz = 0;
			if (o.keyUp.isDown()) {
				mx += look.x;
				my += look.y;
				mz += look.z;
			}
			if (o.keyDown.isDown()) {
				mx -= look.x;
				my -= look.y;
				mz -= look.z;
			}
			if (o.keyRight.isDown()) {
				mx += right.x;
				mz += right.z;
			}
			if (o.keyLeft.isDown()) {
				mx -= right.x;
				mz -= right.z;
			}
			if (o.keyJump.isDown()) {
				my += 1;
			}
			if (o.keyShift.isDown()) {
				my -= 1;
			}
			double speed = BaseFinderConfig.get().freecamSpeed * 0.1;
			x += mx * speed;
			y += my * speed;
			z += mz * speed;
		}
		place(player);
	}

	private static void place(LocalPlayer player) {
		camera.setPos(x, y, z);
		camera.setYRot(player.getYRot());
		camera.setXRot(player.getXRot());
	}
}
