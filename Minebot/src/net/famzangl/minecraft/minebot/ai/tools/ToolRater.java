package net.famzangl.minecraft.minebot.ai.tools;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.core.config.plugins.ResolverUtil.IsA;

import com.google.common.base.Predicate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.famzangl.minecraft.minebot.ai.ItemFilter;
import net.famzangl.minecraft.minebot.ai.ItemFilter.OrItemFilter;
import net.famzangl.minecraft.minebot.ai.ItemFilter.AndItemFilter;
import net.famzangl.minecraft.minebot.ai.ItemFilter.NotItemFilter;
import net.famzangl.minecraft.minebot.ai.path.world.BlockFloatMap;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.famzangl.minecraft.minebot.ai.tools.rate.AndRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.EnchantmentRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.FilterRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.MatchesRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.MultiplyEnchantmentRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.NotRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.OrRater;
import net.famzangl.minecraft.minebot.ai.tools.rate.Rater;
import net.famzangl.minecraft.minebot.settings.MinebotSettings;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

/**
 * This class rates tools.
 * 
 * @author Michael Zangl
 *
 */
public class ToolRater {

	private static class IsEnchanted implements ItemFilter {
		@Override
		public boolean matches(ItemStack itemStack) {
			return itemStack != null && itemStack.isItemEnchanted();
		}

		@Override
		public String toString() {
			return "IsEnchanted []";
		}
	}

	private static class TakesDamage implements ItemFilter {
		@Override
		public boolean matches(ItemStack itemStack) {
			return itemStack != null && itemStack.isItemStackDamageable();
		}

		@Override
		public String toString() {
			return "TakesDamage []";
		}
	}

	private static class Depleted implements ItemFilter {
		@Override
		public boolean matches(ItemStack itemStack) {
			return itemStack != null
					&& itemStack.isItemStackDamageable()
					&& (itemStack.getMaxDamage() - itemStack.getItemDamage()) <= 1;
		}

		@Override
		public String toString() {
			return "Depleted []";
		}
	}

	private static class Hand implements ItemFilter {
		@Override
		public boolean matches(ItemStack itemStack) {
			return itemStack == null;
		}

		@Override
		public String toString() {
			return "Hand []";
		}

	}

	private static class ItemMaterial implements ItemFilter {
		private ToolMaterial toolMaterial;

		public ItemMaterial(ToolMaterial toolMaterial) {
			super();
			this.toolMaterial = toolMaterial;
		}

		@Override
		public boolean matches(ItemStack itemStack) {
			if (itemStack == null) {
				return false;
			} else if (itemStack.getItem() instanceof ItemTool) {
				ItemTool itemTool = (ItemTool) itemStack.getItem();
				return itemTool.getToolMaterial() == toolMaterial;
			} else {
				return false;
			}
		}

		@Override
		public String toString() {
			return "ItemMaterial [toolMaterial=" + toolMaterial + "]";
		}

	}

	public enum ToolType implements ItemFilter {
		SWORD("sword", ItemSword.class), AXE("axe", ItemAxe.class), PICKAXE(
				"pickaxe", ItemPickaxe.class), SPADE("shovel", ItemSpade.class), FISHING_ROD(
				"fishingrod", ItemFishingRod.class);

		private final Class<? extends Item> itemClass;
		private final String name;

		ToolType(String name, Class<? extends Item> itemClass) {
			this.name = name;
			this.itemClass = itemClass;
		}

		@Override
		public boolean matches(ItemStack itemStack) {
			return itemStack != null && itemStack.getItem() != null
					&& itemClass.isInstance(itemStack.getItem());
		}

		public String getName() {
			return name;
		}
	}

	private static final Hashtable<String, Integer> ENCHANTMENTS = new Hashtable<String, Integer>();

	static {
		ENCHANTMENTS.put("efficiency", Enchantment.efficiency.effectId);
		ENCHANTMENTS.put("fortune", Enchantment.fortune.effectId);
		ENCHANTMENTS.put("unbreaking", Enchantment.unbreaking.effectId);
		ENCHANTMENTS.put("silk_touch", Enchantment.silkTouch.effectId);
	}

	private static final Hashtable<String, ItemFilter> FILTERS = new Hashtable<String, ItemFilter>();

	static {
		FILTERS.put("is_enchanted", new IsEnchanted());
		FILTERS.put("takes_damage", new TakesDamage());
		FILTERS.put("depleted", new Depleted());
		FILTERS.put("hand", new Hand());
		FILTERS.put("wood", new ItemMaterial(ToolMaterial.WOOD));
		FILTERS.put("stone", new ItemMaterial(ToolMaterial.STONE));
		FILTERS.put("gold", new ItemMaterial(ToolMaterial.GOLD));
		FILTERS.put("emerald", new ItemMaterial(ToolMaterial.IRON));
		FILTERS.put("diamond", new ItemMaterial(ToolMaterial.EMERALD));
		for (ToolType tt : ToolType.values()) {
			FILTERS.put(tt.getName(), tt);
		}
	}

	private static Rater getCompoundRater(String name, BlockFloatMap values) {
		// todo: advanced boolean parsing, parentheses, ...
		String[] orPartStrings = name.split("\\s*\\|\\s*");
		Rater[] orParts = new Rater[orPartStrings.length];
		for (int i = 0; i < orParts.length; i++) {
			orParts[i] = getCompoundAndRater(values, orPartStrings[i]);
		}
		return new OrRater(values, orParts);
	}

	private static AndRater getCompoundAndRater(BlockFloatMap values,
			String string) {
		String[] parts = string.split("\\s*\\&\\s*");
		Rater[] raters = new Rater[parts.length];
		for (int j = 0; j < raters.length; j++) {
			raters[j] = createRaterWithNot(parts[j], values);
		}
		AndRater andRater = new AndRater(values, raters);
		return andRater;
	}

	private static Rater createRaterWithNot(String string, BlockFloatMap values) {
		Matcher match = Pattern.compile("(\\!?)\\s*(\\w+)").matcher(string);
		if (!match.matches()) {
			throw new IllegalArgumentException("Could not match filter: "
					+ string);
		}

		Rater rater = createRater(match.group(2), values);
		if (!match.group(1).isEmpty()) {
			rater = new NotRater(values, rater);
		}
		return rater;
	}

	private static Rater createRater(String name, BlockFloatMap values) {
		if ("matches".equals(name)) {
			return new MatchesRater(name, values);
		}

		Integer enchantmentId = ENCHANTMENTS.get(name);
		if (enchantmentId != null) {
			return new MultiplyEnchantmentRater(enchantmentId, name, values);
		}

		if (name.endsWith("_present")) {
			enchantmentId = ENCHANTMENTS.get(name.replace("_present", ""));
			if (enchantmentId != null) {
				return new EnchantmentRater(enchantmentId, name, values);
			}
		}

		ItemFilter filter = FILTERS.get(name);
		if (filter != null) {
			return new FilterRater(filter, name, values);
		}

		throw new IllegalArgumentException("Could not find rater: " + name);
	}

	private final ArrayList<Rater> raters = new ArrayList<Rater>();

	public void addRater(Rater rater) {
		raters.add(rater);
	}

	public void addRater(String name, BlockFloatMap values) {
		addRater(getCompoundRater(name, values));
	}

	public List<Rater> getRaters() {
		return Collections.unmodifiableList(raters);
	}

	public float rateTool(ItemStack stack, int forBlockAndMeta) {
		float f = 1;
		for (Rater rater : raters) {
			f *= rater.rate(stack, forBlockAndMeta);
		}
		return f;
	}

	@Override
	public String toString() {
		return "ToolRater [raters=" + raters + "]";
	}

	/**
	 * Gets a simple rater that returns 2 if the tool matches, 1 otherwise.
	 * 
	 * @return
	 */
	public static Rater getToolMatchesRater() {
		BlockFloatMap values = new BlockFloatMap();
		values.setDefault(2);
		return new MatchesRater("", values);
	}

	public static ToolRater createDefaultRater() {
		Gson gson = MinebotSettings.getGson();
		InputStream res = ToolRater.class.getResourceAsStream("tools.json");
		return gson.fromJson(new InputStreamReader(res), ToolRater.class);
		// ToolRater rater = new ToolRater();
		// BlockFloatMap map = new BlockFloatMap();
		// BlockFloatMap map2 = new BlockFloatMap();
		// map.setDefault(2);
		// map2.setDefault(1.01f);
		// map2.setBlock(Blocks.leaves, .99f);
		// map2.setBlock(Blocks.leaves2, .99f);
		// rater.addRater(getRater("is_enchanted", map));
		// rater.addRater(getRater("matches", map2));
		// return rater;
	}
}
