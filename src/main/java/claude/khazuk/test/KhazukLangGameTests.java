package claude.khazuk.test;

import claude.khazuk.Khazuk;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every item, block and entity the mod registers has its English name (assets/khazuk/lang/en_us.json, read from the jar): a name missing shows
 * the raw key in the player's game (the review of the Waaagh!'s a76656b: an asset script's rewrite of the lang file dropped seven names).
 */
@GameTestHolder(Khazuk.MODID)
public class KhazukLangGameTests {

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void everyThingTheModAddsHasItsName(GameTestHelper h) throws Exception {
        JsonObject lang;
        try (var in = KhazukLangGameTests.class.getResourceAsStream("/assets/khazuk/lang/en_us.json")) {
            h.assertTrue(in != null, "no assets/khazuk/lang/en_us.json in the jar");
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> missing = new ArrayList<>();
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            var id = e.getKey().identifier();
            if (id.getNamespace().equals(Khazuk.MODID)) {
                String key = (e.getValue() instanceof BlockItem ? "block." : "item.") + id.getNamespace() + "." + id.getPath();
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
        }
        for (var e : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            var id = e.getKey().identifier();
            String key = "entity." + id.getNamespace() + "." + id.getPath();
            if (id.getNamespace().equals(Khazuk.MODID) && !lang.has(key)) {
                missing.add(key);
            }
        }
        h.assertTrue(missing.isEmpty(), missing.size() + " names missing from en_us.json: " + missing);
        h.succeed();
    }
}
