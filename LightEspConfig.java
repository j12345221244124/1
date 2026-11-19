package dev.lightesp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LightEspConfig {
	public static final int[] COLORS = {0xFFFFD21F, 0xFF00E5FF, 0xFFFF3B30, 0xFF34C759, 0xFFFFFFFF, 0xFFFF2DF1};
	public static final String[] COLOR_NAMES = {"Yellow", "Cyan", "Red", "Green", "White", "Magenta"};

	public boolean enabled = true;
	public int range = 96;          // blocks
	public int minDepth = 4;        // blocks of solid cover above a light for it to count as underground
	public boolean overworldOnly = true;
	public boolean filled = true;
	public int colorIndex = 0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LightEspConfig instance;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("lightesp.json");
	}

	public static LightEspConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				instance = GSON.fromJson(Files.readString(p), LightEspConfig.class);
			}
		} catch (Exception ignored) {
		}
		if (instance == null) instance = new LightEspConfig();
	}

	public static void save() {
		try {
			Files.writeString(path(), GSON.toJson(get()));
		} catch (IOException ignored) {
		}
	}

	public int color() {
		return COLORS[Math.floorMod(colorIndex, COLORS.length)];
	}

	public String colorName() {
		return COLOR_NAMES[Math.floorMod(colorIndex, COLOR_NAMES.length)];
	}
}
