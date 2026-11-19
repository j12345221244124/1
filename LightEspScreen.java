package dev.lightesp;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
		int w = 200, h = 20, gap = 24;
		int x = this.width / 2 - w / 2;
		int y = this.height / 4 - 8;

		addRenderableWidget(Button.builder(onOff("Light ESP", cfg.enabled), b -> {
			cfg.enabled = !cfg.enabled;
			if (!cfg.enabled) LightScanner.clear();
			LightEspConfig.save();
			b.setMessage(onOff("Light ESP", cfg.enabled));
		}).bounds(x, y, w, h).build());

		addRenderableWidget(new IntSlider(x, y += gap, w, h, "Range", 16, 256, cfg.range, v -> {
			cfg.range = v;
			LightEspConfig.save();
		}));

		addRenderableWidget(new IntSlider(x, y += gap, w, h, "Min depth", 0, 32, cfg.minDepth, v -> {
			cfg.minDepth = v;
			LightScanner.clear(); // depth rule changed, rescan
			LightEspConfig.save();
		}));

		addRenderableWidget(Button.builder(onOff("Overworld only", cfg.overworldOnly), b -> {
			cfg.overworldOnly = !cfg.overworldOnly;
			LightScanner.clear();
			LightEspConfig.save();
			b.setMessage(onOff("Overworld only", cfg.overworldOnly));
		}).bounds(x, y += gap, w, h).build());

		addRenderableWidget(Button.builder(onOff("Filled boxes", cfg.filled), b -> {
			cfg.filled = !cfg.filled;
			LightEspConfig.save();
			b.setMessage(onOff("Filled boxes", cfg.filled));
		}).bounds(x, y += gap, w, h).build());

		addRenderableWidget(Button.builder(Component.literal("Color: " + cfg.colorName()), b -> {
			cfg.colorIndex = (cfg.colorIndex + 1) % LightEspConfig.COLORS.length;
			LightEspConfig.save();
			b.setMessage(Component.literal("Color: " + cfg.colorName()));
		}).bounds(x, y += gap, w, h).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x, y + gap + 8, w, h).build());
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
