package dev.lightesp;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

public class LightEspScreen extends Screen {
	private final Screen parent;

	public LightEspScreen(Screen parent) {
		super(Text.literal("Light ESP"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		LightEspConfig cfg = LightEspConfig.get();
		int w = 200, h = 20, gap = 24;
		int x = this.width / 2 - w / 2;
		int y = this.height / 4 - 8;

		addDrawableChild(ButtonWidget.builder(onOff("Light ESP", cfg.enabled), b -> {
			cfg.enabled = !cfg.enabled;
			if (!cfg.enabled) LightScanner.clear();
			LightEspConfig.save();
			b.setMessage(onOff("Light ESP", cfg.enabled));
		}).dimensions(x, y, w, h).build());

		addDrawableChild(new IntSlider(x, y += gap, w, h, "Range", 16, 256, cfg.range, v -> {
			cfg.range = v;
			LightEspConfig.save();
		}));

		addDrawableChild(new IntSlider(x, y += gap, w, h, "Min depth", 0, 32, cfg.minDepth, v -> {
			cfg.minDepth = v;
			LightScanner.clear(); // depth rule changed, rescan
			LightEspConfig.save();
		}));

		addDrawableChild(ButtonWidget.builder(onOff("Overworld only", cfg.overworldOnly), b -> {
			cfg.overworldOnly = !cfg.overworldOnly;
			LightScanner.clear();
			LightEspConfig.save();
			b.setMessage(onOff("Overworld only", cfg.overworldOnly));
		}).dimensions(x, y += gap, w, h).build());

		addDrawableChild(ButtonWidget.builder(onOff("Filled boxes", cfg.filled), b -> {
			cfg.filled = !cfg.filled;
			LightEspConfig.save();
			b.setMessage(onOff("Filled boxes", cfg.filled));
		}).dimensions(x, y += gap, w, h).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Color: " + cfg.colorName()), b -> {
			cfg.colorIndex = (cfg.colorIndex + 1) % LightEspConfig.COLORS.length;
			LightEspConfig.save();
			b.setMessage(Text.literal("Color: " + cfg.colorName()));
		}).dimensions(x, y += gap, w, h).build());

		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
				.dimensions(x, y + gap + 8, w, h).build());
	}

	private static Text onOff(String label, boolean value) {
		return Text.literal(label + ": " + (value ? "ON" : "OFF"));
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 4 - 28, 0xFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		this.client.setScreen(parent);
	}

	private static class IntSlider extends SliderWidget {
		private final String label;
		private final int min, max;
		private final IntConsumer onChange;

		IntSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer onChange) {
			super(x, y, w, h, Text.empty(), (value - min) / (double) (max - min));
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
			setMessage(Text.literal(label + ": " + current()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}
}
