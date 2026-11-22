package dev.lightesp;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * While a grindstone is open and the option is on, feeds every item that has a
 * removable (non-curse) enchantment through the grindstone, one at a time.
 *
 * Each step is one shift-click, spaced a few ticks apart so the server can
 * answer between steps:
 *   result slot filled  -> shift-click the result into the inventory
 *   inputs empty        -> shift-click the next enchanted inventory item in
 */
public class AutoDisenchant {
	private static final int INPUT_TOP = 0;
	private static final int INPUT_BOTTOM = 1;
	private static final int RESULT = 2;
	private static final int FIRST_INVENTORY_SLOT = 3;

	private static final int CLICK_DELAY_TICKS = 3;  // wait between clicks for server sync
	private static final int STUCK_TICKS = 40;       // give up if the inputs never produce a result
	private static final int MAX_RESULT_RETRIES = 10; // give up if the result can't be taken (full inventory)

	private static int cooldown = 0;
	private static int idleTicks = 0;
	private static boolean stuck = false;

	public static void tick(Minecraft client) {
		LightEspConfig cfg = LightEspConfig.get();
		Player player = client.player;
		if (!cfg.autoDisenchant || player == null || client.gameMode == null
				|| !(player.containerMenu instanceof GrindstoneMenu menu)) {
			reset();
			return;
		}
		if (stuck) return;
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		ItemStack result = menu.getSlot(RESULT).getItem();
		boolean inputsEmpty = menu.getSlot(INPUT_TOP).getItem().isEmpty()
				&& menu.getSlot(INPUT_BOTTOM).getItem().isEmpty();

		if (!result.isEmpty()) {
			// take the disenchanted item (this also pays out the XP)
			click(client, menu, RESULT, player);
			return;
		}

		if (inputsEmpty) {
			int next = findEnchanted(menu);
			if (next < 0) {
				idleTicks = 0; // nothing left to do
				return;
			}
			click(client, menu, next, player);
			return;
		}

		// something is in the inputs but no result showed up yet: wait, then give up
		if (++idleTicks > STUCK_TICKS) stuck = true;
	}

	private static void click(Minecraft client, GrindstoneMenu menu, int slot, Player player) {
		client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
		cooldown = CLICK_DELAY_TICKS;
		if (slot == RESULT) {
			// repeated result clicks that never clear (full inventory) -> stop
			if (++idleTicks > MAX_RESULT_RETRIES) stuck = true;
		} else {
			idleTicks = 0;
		}
	}

	private static int findEnchanted(GrindstoneMenu menu) {
		for (int i = FIRST_INVENTORY_SLOT; i < menu.slots.size(); i++) {
			if (hasRemovableEnchant(menu.getSlot(i).getItem())) return i;
		}
		return -1;
	}

	/** True if the stack has at least one enchantment the grindstone would remove (curses stay). */
	private static boolean hasRemovableEnchant(ItemStack stack) {
		if (stack.isEmpty()) return false;
		return anyNonCurse(stack.get(DataComponents.ENCHANTMENTS))
				|| anyNonCurse(stack.get(DataComponents.STORED_ENCHANTMENTS));
	}

	private static boolean anyNonCurse(ItemEnchantments enchants) {
		if (enchants == null || enchants.isEmpty()) return false;
		for (Object2IntMap.Entry<Holder<Enchantment>> e : enchants.entrySet()) {
			if (!e.getKey().is(EnchantmentTags.CURSE)) return true;
		}
		return false;
	}

	private static void reset() {
		cooldown = 0;
		idleTicks = 0;
		stuck = false;
	}
}
