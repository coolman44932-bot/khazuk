package claude.khazuk.test;

import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.khazuk.Khazuk;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.definition.animation.ActorAnimations;
import com.geckolib.loading.definition.geometry.Geometry;
import com.geckolib.loading.definition.geometry.object.ModelFormatVersion;
import com.geckolib.loading.math.MathParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Dwarfs' models and clips, found where GeckoLib 5's resource reload looks and read and baked by its own loader, so a model
 * GeckoLib can't find or read fails here and not in a client: GeckoLib 5 reads only {@code assets/<ns>/geckolib/models} and
 * {@code geckolib/animations} (4.x's {@code geo/} and {@code animations/} load nothing, and the first unit drawn crashed the game),
 * Blockbench 5.2 stamps a geo format GeckoLib knows as no version (the export sets 1.12.0), and a clip the controller asks for must
 * be in the file.
 */
@GameTestHolder(Khazuk.MODID)
public class KhazukAssetGameTests {

    private static final List<String> BONES = List.of("root", "body", "neck", "head", "right_arm", "right_hand", "left_arm", "right_leg", "left_leg");
    private static final List<String> CLIPS = List.of("idle", "walk", "work", "attack");

    /**
     * The file GeckoLib's reload files under {@code id} (what DefaultedEntityGeoModel asks for, {@code khazuk:entity/<kind>}):
     * a file in its folder whose name, stripped as GeckoLib strips it, is the id.
     */
    private static String path(GameTestHelper h, Identifier folder, Identifier id, String suffix) {
        String file = folder.getPath() + "/" + id.getPath() + suffix;
        Identifier filed = GeckoLibResources.stripPrefixAndSuffix(Identifier.fromNamespaceAndPath(id.getNamespace(), file));
        h.assertTrue(filed.equals(id), file + " is filed by GeckoLib as " + filed + ", not " + id);
        return "assets/" + id.getNamespace() + "/" + file;
    }

    private static Identifier unit(String kind) {
        return Identifier.fromNamespaceAndPath(Khazuk.MODID, "entity/" + kind);
    }

    private static InputStreamReader open(GameTestHelper h, String path) throws Exception {
        var jar = ModList.get().getModFileById(Khazuk.MODID).getFile().getContents();
        h.assertTrue(jar.containsFile(path), "the Dwarfs' jar has no " + path);
        InputStream in = jar.openFile(path);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    /** Both units' geo files are where GeckoLib looks, read with a known format version, bake, and carry every bone the race's code and the renderer name. */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void theUnitsModelsBakeInGeckoLib(GameTestHelper h) throws Exception {
        for (String kind : List.of("miner", "dwarf_warrior", "runesmith", "runelord", "quarreller", "thunderer", "longbeard", "longbeard_great")) {
            String path = path(h, GeckoLibResources.MODELS_PATH, unit(kind), ".geo.json");
            Geometry geo;
            try (InputStreamReader r = open(h, path)) {
                geo = Geometry.GSON.fromJson(r, Geometry.class);
            }
            h.assertTrue(ModelFormatVersion.match(geo.formatVersion()) != null, kind + "'s geo format " + geo.formatVersion() + " is none GeckoLib knows");
            BakedGeoModel baked = geo.bake(unit(kind));
            for (String bone : BONES) {
                h.assertTrue(baked.getBone(bone).isPresent(), kind + "'s model has no bone " + bone);
            }
        }
        h.succeed();
    }

    /** Both units' animation files are where GeckoLib looks, bake, with every clip the units' animation controller plays. */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void theUnitsClipsBakeInGeckoLib(GameTestHelper h) throws Exception {
        for (String kind : List.of("miner", "dwarf_warrior", "runesmith", "runelord", "quarreller", "thunderer", "longbeard", "longbeard_great")) {
            String path = path(h, GeckoLibResources.ANIMATIONS_PATH, unit(kind), ".animation.json");
            BakedAnimations baked;
            try (InputStreamReader r = open(h, path)) {
                baked = ActorAnimations.GSON.fromJson(r, ActorAnimations.class)
                        .bake(unit(kind), MathParser.create());
            }
            for (String clip : CLIPS) {
                h.assertTrue(baked.getAnimation("animation.khazuk." + clip) != null, kind + "'s animations have no " + clip + ": " + baked.animations().keySet());
            }
        }
        h.succeed();
    }

    /** The Anvil of Doom's showpiece (the detail session's): its geo bakes, its five clips bake, and its glowmask is in the jar. */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void theAnvilOfDoomsModelAndClipsBake(GameTestHelper h) throws Exception {
        Identifier id = unit("anvil_of_doom");
        Geometry geo;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.MODELS_PATH, id, ".geo.json"))) {
            geo = Geometry.GSON.fromJson(r, Geometry.class);
        }
        h.assertTrue(ModelFormatVersion.match(geo.formatVersion()) != null, "the Anvil's geo format " + geo.formatVersion() + " is none GeckoLib knows");
        geo.bake(id);
        BakedAnimations baked;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.ANIMATIONS_PATH, id, ".animation.json"))) {
            baked = ActorAnimations.GSON.fromJson(r, ActorAnimations.class).bake(id, MathParser.create());
        }
        for (String clip : List.of("idle", "strike", "strike_wrath", "strike_hearth", "strike_oath")) {
            h.assertTrue(baked.getAnimation("animation.anvil_of_doom." + clip) != null, "the Anvil has no " + clip + ": " + baked.animations().keySet());
        }
        open(h, "assets/khazuk/textures/entity/anvil_of_doom_glowmask.png").close();
        open(h, "assets/khazuk/textures/entity/runesmith_glowmask.png").close();   // the heroes' showpieces glow too
        open(h, "assets/khazuk/textures/entity/runelord_glowmask.png").close();
        h.succeed();
    }

    /** Grungni's Hammer (D7, the detail session's showpiece since 0.10.1): its geo bakes, its strike bakes, its glowmask is in the jar. */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void grungnisHammersModelAndStrikeBake(GameTestHelper h) throws Exception {
        Identifier id = unit("grungnis_hammer");
        Geometry geo;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.MODELS_PATH, id, ".geo.json"))) {
            geo = Geometry.GSON.fromJson(r, Geometry.class);
        }
        h.assertTrue(ModelFormatVersion.match(geo.formatVersion()) != null, "the Hammer's geo format " + geo.formatVersion() + " is none GeckoLib knows");
        geo.bake(id);
        BakedAnimations baked;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.ANIMATIONS_PATH, id, ".animation.json"))) {
            baked = ActorAnimations.GSON.fromJson(r, ActorAnimations.class).bake(id, MathParser.create());
        }
        h.assertTrue(baked.getAnimation("animation.grungnis_hammer.strike") != null, "the Hammer has no strike: " + baked.animations().keySet());
        open(h, "assets/khazuk/textures/entity/grungnis_hammer_glowmask.png").close();
        for (String token : List.of("grungnis_hammer", "valayas_ward", "grimnirs_fury")) {
            open(h, "assets/khazuk/textures/item/" + token + ".png").close();
            open(h, "assets/khazuk/items/" + token + ".json").close();
        }
        h.succeed();
    }

    /**
     * Unit variants (0.12): every rank-and-file kind's swappable parts are bones of its baked model, and every palette's texture is in the
     * jar, so no look a Dwarf rolls (claude.khazuk.Looks) names a bone or a texture that isn't there.
     */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void everyVariantsPartsAndPalettesAreThere(GameTestHelper h) throws Exception {
        for (claude.khazuk.Looks.Kind kind : claude.khazuk.Looks.ALL) {
            Geometry geo;
            try (InputStreamReader r = open(h, path(h, GeckoLibResources.MODELS_PATH, unit(kind.id()), ".geo.json"))) {
                geo = Geometry.GSON.fromJson(r, Geometry.class);
            }
            BakedGeoModel baked = geo.bake(unit(kind.id()));
            for (List<String> group : kind.groups()) {
                for (String bone : group) {
                    h.assertTrue(bone.isEmpty() || baked.getBone(bone).isPresent(), kind.id() + "'s model has no variant bone " + bone);
                }
            }
            for (int p = 0; p < claude.khazuk.Looks.PALETTES; p++) {
                Identifier tex = claude.khazuk.Looks.texture(kind, p);
                open(h, "assets/" + tex.getNamespace() + "/" + tex.getPath()).close();
            }
        }
        h.succeed();
    }

    /**
     * A Dwarf's look is his own and always the same: rolled twice from one UUID it matches; over 600 UUIDs every palette and every part
     * of every group turns up; and the bones a look hides are every part of each group but the one it shows.
     */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void aDwarfsLookIsHisOwnAndTheyVary(GameTestHelper h) {
        java.util.Random r = new java.util.Random(12);
        for (claude.khazuk.Looks.Kind kind : claude.khazuk.Looks.ALL) {
            boolean[] palettes = new boolean[claude.khazuk.Looks.PALETTES];
            boolean[][] parts = new boolean[kind.groups().size()][];
            for (int g = 0; g < parts.length; g++) {
                parts[g] = new boolean[kind.groups().get(g).size()];
            }
            for (int i = 0; i < 600; i++) {
                java.util.UUID id = new java.util.UUID(r.nextLong(), r.nextLong());
                claude.khazuk.Looks.Look a = claude.khazuk.Looks.roll(kind, id), b = claude.khazuk.Looks.roll(kind, id);
                h.assertTrue(a.palette() == b.palette() && java.util.Arrays.equals(a.picks(), b.picks()), kind.id() + ": one UUID rolled two looks");
                palettes[a.palette()] = true;
                int packed = claude.khazuk.Looks.pack(a);
                List<String> hidden = claude.khazuk.Looks.hidden(kind, packed);
                for (int g = 0; g < parts.length; g++) {
                    parts[g][a.picks()[g]] = true;
                    List<String> group = kind.groups().get(g);
                    for (int k = 0; k < group.size(); k++) {
                        boolean hides = hidden.contains(group.get(k));
                        h.assertTrue(group.get(k).isEmpty() || hides == (k != a.picks()[g]), kind.id() + ": the look " + packed + " hides " + hidden);
                    }
                }
            }
            for (int p = 0; p < palettes.length; p++) {
                h.assertTrue(palettes[p], kind.id() + ": palette " + p + " never came up in 600 Dwarfs");
            }
            for (int g = 0; g < parts.length; g++) {
                for (int k = 0; k < parts[g].length; k++) {
                    h.assertTrue(parts[g][k], kind.id() + ": part " + k + " of group " + g + " never came up in 600 Dwarfs");
                }
            }
        }
        h.succeed();
    }
}
