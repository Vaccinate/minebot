package net.famzangl.minecraft.minebot.settings.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.famzangl.minecraft.minebot.ai.path.world.BlockSet;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Type;

/**
 * JSON ouptut:
 * 
 * [ "dirt", "minecraft:stone", 0, {block: 1, meta: 1}, {block: "dirt", meta: 1}
 * ]
 * 
 * 
 * @author michael
 *
 */
public class BlockSetAdapter implements JsonSerializer<BlockSet>,
		JsonDeserializer<BlockSet> {

	@Override
	public JsonElement serialize(BlockSet src, Type typeOfSrc,
			JsonSerializationContext context) {
		JsonArray obj = new JsonArray();
		// TODO
		for (int blockId = 0; blockId < 0; blockId++) {
			if (src.contains(blockId)) {
				obj.add(getName(blockId));
			} else if (src.contains(blockId)) {
				for (int i = 0; i < 16; i++) {
					if (src.contains(blockId * 16 + i)) {
						JsonObject object = new JsonObject();
						object.add("block", getName(blockId));
						object.addProperty("meta", i);
						obj.add(object);
					}
				}
			}
		}
		return obj;
	}

	public static JsonElement getName(int blockId) {
		/*
		Object name = Block.REGISTRY.getNameForObject(Block.getBlockById(blockId));
		if (name != null && name instanceof ResourceLocation) {
			return new JsonPrimitive(BlockNameBuilder.toString((ResourceLocation) name));
		} else {
		 */
			return new JsonPrimitive(blockId);
		//}
	}

	@Override
	public BlockSet deserialize(JsonElement json, Type typeOfT,
			JsonDeserializationContext context) throws JsonParseException {
		if (!json.isJsonArray()) {
			throw new JsonParseException("need an array.");
		}
/*
		BlockSet metaRes = new BlockSet(new int[0]);
		JsonArray jsonArray = json.getAsJsonArray();
		for (JsonElement element : jsonArray) {
			if (element.isJsonObject()) {
				metaRes = metaRes.unionWith(getBlockWithMeta(element
						.getAsJsonObject()));
			} else if (element.isJsonPrimitive()) {
				Block block = getBlockId(element.getAsJsonPrimitive());
				metaRes = metaRes.unionWith(new BlockSet(block));
			} else {
				throw new JsonParseException("could not understand this.");
			}
		}

		return metaRes;
 */
		return BlockSet.builder().build();
	}
}
