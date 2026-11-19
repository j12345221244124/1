package dev.lightesp;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public class LightEspScreen extends Screen {
	private final Screen parent;

	public LightEspScreen(Screen parent) {
		super(Component.literal("Light ESP"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LightEspConfig cfg = LightEspConfig.get();
		int w = 150, h = 20, gap = 22;
		int left = this.width / 2 - w - 5;
		int right = this.width / 2 + 5;
		int top = this.height / 6;

		// ---- left column: underground light ESP ----
		int y = top;
		addToggle(left, y, w, "Light ESP", () -> cfg.enabled, v -> {
			cfg.enabled = v;
			if (!v) LightScanner.clear();
		});

		addRenderableWidget(new IntSlider(left, y += gap, w, h, "Range", 16, 256, cfg.range, v -> {
			cfg.range = v;
			LightEspConfig.save();
		}));

		addRenderableWidget(new IntSlider(left, y += gap, w, h, "Min depth", 0, 32, cfg.minDepth, v -> {
			cfg.minDepth = v;
			LightScanner.clear(); // depth rule changed, rescan
			LightEspConfig.save();
		}));

		addToggle(left, y += gap, w, "Overworld only", () -> cfg.overworldOnly, v -> {
			cfg.overworldOnly = v;
			LightScanner.clear();
		});

		addToggle(left, y += gap, w, "Filled boxes", () -> cfg.filled, v -> cfg.filled = v);

		addRenderableWidget(Button.builder(Component.literal("Color: " + cfg.colorName()), b -> {
			cfg.colorIndex = (cfg.colorIndex + 1) % LightEspConfig.COLORS.length;
			LightEspConfig.save();
			b.setMessage(Component.literal("Color: " + cfg.colorName()));
		}).bounds(left, y += gap, w, h).build());

		// ---- right column: storage / spawner ESP and freecam ----
		y = top;
		addToggle(right, y, w, "Storage ESP", () -> cfg.storageEsp, v -> {
			cfg.storageEsp = v;
			StorageScanner.clear();
		});

		addToggle(right, y += gap, w, "Spawner ESP", () -> cfg.spawnerEsp, v -> {
			cfg.spawnerEsp = v;
			StorageScanner.clear();
		});

		addRenderableWidget(new IntSlider(right, y += gap, w, h, "Storage range", 16, 256, cfg.storageRange, v -> {
			cfg.storageRange = v;
			LightEspConfig.save();
		}));

		addRenderableWidget(Button.builder(onOff("Freecam", Freecam.isActive()), b -> {
			Freecam.toggle(this.minecraft);
			b.setMessage(onOff("Freecam", Freecam.isActive()));
		}).bounds(right, y += gap, w, h).build());

		addRenderableWidget(new IntSlider(right, y += gap, w, h, "Freecam speed", 1, 20, cfg.freecamSpeed, v -> {
			cfg.freecamSpeed = v;
			LightEspConfig.save();
		}));

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(this.width / 2 - 100, top + 6 * gap + 10, 200, h).build());
	}

	private void addToggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
		addRenderableWidget(Button.builder(onOff(label, get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			LightEspConfig.save();
			b.setMessage(onOff(label, get.getAsBoolean()));
		}).bounds(x, y, w, 20).build());
	}

	private static Component onOff(String label, boolean value) {
		return Component.literal(label + ": " + (value ? "ON" : "OFF"));
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	private static class IntSlider extends AbstractSliderButton {
		private final String label;
		private final int min, max;
		private final IntConsumer onChange;

		IntSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer onChange) {
			super(x, y, w, h, Component.empty(), (value - min) / (double) (max - min));
			this.label = label;
			this.min = min;
			this.max = max;
			this.onChange = onChange;
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(this.value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(label + ": " + current()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}
}
