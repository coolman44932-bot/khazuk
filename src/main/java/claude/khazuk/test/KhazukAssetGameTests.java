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

    /**
     * The Ancestor Shrine's statue (the detail session's showpiece, handoffs/ancestor_shrine_2026-11-04_done.md): its geo bakes with the band's
     * ten gauge runes and their swells; its clips bake, with every clip the Shrine's two controllers play or can be triggered to (GeckoLib 5.5.2
     * throws on a missing name and takes the client down, and there is no plain "answer"), each trigger named as its clip; the controllers
     * come "main" then "rite" (the later wins on a bone both key); and its sheet, glowmask and GeckoLib item model are in the jar.
     */
    @GameTest(template = "digsite", timeoutTicks = 20, batch = "khazuk")
    public static void theShrinesStatueBakesWithItsGaugeAndEveryClipItPlays(GameTestHelper h) throws Exception {
        Identifier id = Identifier.fromNamespaceAndPath(Khazuk.MODID, "block/ancestor_shrine");
        Geometry geo;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.MODELS_PATH, id, ".geo.json"))) {
            geo = Geometry.GSON.fromJson(r, Geometry.class);
        }
        h.assertTrue(ModelFormatVersion.match(geo.formatVersion()) != null, "the statue's geo format " + geo.formatVersion() + " is none GeckoLib knows");
        BakedGeoModel model = geo.bake(id);
        for (int i = 1; i <= claude.khazuk.ShrineBlockEntity.RUNES; i++) {
            h.assertTrue(model.getBone("devotion_" + i).isPresent(), "the statue has no gauge rune devotion_" + i);
            h.assertTrue(model.getBone("devotion_" + i + "_swell").isPresent(), "the statue's rune " + i + " has no swell");
        }
        h.assertTrue(model.getBone("devotion_" + (claude.khazuk.ShrineBlockEntity.RUNES + 1)).isEmpty(), "the band has more runes than the gauge lights");
        BakedAnimations baked;
        try (InputStreamReader r = open(h, path(h, GeckoLibResources.ANIMATIONS_PATH, id, ".animation.json"))) {
            baked = ActorAnimations.GSON.fromJson(r, ActorAnimations.class).bake(id, MathParser.create());
        }
        List<com.geckolib.animation.RawAnimation> played = new java.util.ArrayList<>(List.of(claude.khazuk.ShrineBlockEntity.IDLE));
        played.addAll(claude.khazuk.ShrineBlockEntity.RITES.values());
        for (com.geckolib.animation.RawAnimation anim : played) {
            for (com.geckolib.animation.RawAnimation.Stage stage : anim.getAnimationStages()) {
                h.assertTrue(baked.getAnimation(stage.animationName()) != null, "the statue has no clip " + stage.animationName() + ": " + baked.animations().keySet());
            }
        }
        claude.khazuk.ShrineBlockEntity.RITES.forEach((trigger, anim) -> h.assertTrue(anim.getAnimationStages().size() == 1
                && anim.getAnimationStages().get(0).animationName().equals("animation.ancestor_shrine." + trigger), "the trigger " + trigger + " plays " + anim));
        h.assertTrue(claude.khazuk.ShrineBlockEntity.RITES.containsKey(claude.khazuk.ShrineBlockEntity.PRAY_TRIGGER), "the rite has no prayer");
        for (claude.khazuk.god.AncestorPowers.Power p : claude.khazuk.god.AncestorPowers.Power.values()) {
            String trigger = claude.khazuk.ShrineBlockEntity.answerTrigger(p);
            h.assertTrue(claude.khazuk.ShrineBlockEntity.RITES.containsKey(trigger), p + "'s answer " + trigger + " isn't a trigger of the rite");
        }
        var shrine = new claude.khazuk.ShrineBlockEntity(h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), claude.khazuk.KRegistry.SHRINE.get().defaultBlockState());
        var controllers = new com.geckolib.animatable.manager.AnimatableManager<>(shrine).getAnimationControllers();
        h.assertTrue(List.copyOf(controllers.keySet()).equals(List.of("main", claude.khazuk.ShrineBlockEntity.RITE)), "the Shrine's controllers are " + controllers.keySet());
        open(h, "assets/khazuk/textures/entity/ancestor_shrine_statue.png").close();
        open(h, "assets/khazuk/textures/entity/ancestor_shrine_statue_glowmask.png").close();
        String item;
        try (InputStreamReader r = open(h, "assets/khazuk/items/ancestor_shrine.json")) {
            item = new java.io.BufferedReader(r).lines().collect(java.util.stream.Collectors.joining(" "));
        }
        h.assertTrue(item.contains("\"geckolib:geckolib\"") && item.contains("\"khazuk:item/ancestor_shrine\""), "the Shrine's item isn't GeckoLib's render: " + item);
        open(h, "assets/khazuk/models/item/ancestor_shrine.json").close();
        h.succeed();
    }

    /**
     * The Shrine's block, for its statue: it has a facing (NORTH by default, so a Shrine saved before the statue loads facing north), it
     * doesn't occlude (else its own cell holds no light and the statue draws black), it draws no cube (the statue is the block entity's),
     * its collision is still the whole block, and set down by a god standing on any side and looking at it, it faces him (the statue looks
     * along its facing).
     */
    @SuppressWarnings("removal")
    @GameTest(template = "field", timeoutTicks = 40, batch = "khazuk")
    public static void theShrineFacesItsPlacerAndLetsTheLightIn(GameTestHelper h) {
        var block = claude.khazuk.KRegistry.SHRINE.get();
        var d = block.defaultBlockState();
        h.assertTrue(d.hasProperty(claude.khazuk.ShrineBlock.FACING) && d.getValue(claude.khazuk.ShrineBlock.FACING) == net.minecraft.core.Direction.NORTH,
                "the Shrine's default state doesn't face north: " + d);
        h.assertTrue(!d.canOcclude(), "the Shrine occludes: its statue would draw black");
        h.assertTrue(d.getRenderShape() == net.minecraft.world.level.block.RenderShape.INVISIBLE, "the Shrine still draws its cube under the statue");
        net.minecraft.core.BlockPos at = new net.minecraft.core.BlockPos(12, 3, 12);
        net.minecraft.core.BlockPos abs = h.absolutePos(at);
        h.setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE);
        var god = h.makeMockServerPlayerInLevel();
        god.setGameMode(net.minecraft.world.level.GameType.CREATIVE);   // a creative god may set one anywhere (ShrineBlock#mayRoot)
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            h.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
            net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atBottomCenterOf(abs);
            net.minecraft.world.phys.Vec3 stand = centre.add(side.getStepX() * 3, 0, side.getStepZ() * 3);
            god.snapTo(stand.x, stand.y, stand.z, side.getOpposite().toYRot(), 20f);   // on that side, looking at the spot
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs.below()).add(0, 0.5, 0),
                    net.minecraft.core.Direction.UP, abs.below(), false);
            var placed = block.getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(god, net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(claude.khazuk.KRegistry.SHRINE_ITEM.get()), hit));
            h.assertTrue(placed != null, "a creative god couldn't set a Shrine down");
            net.minecraft.core.Direction facing = placed.getValue(claude.khazuk.ShrineBlock.FACING);
            net.minecraft.world.phys.Vec3 toGod = god.position().subtract(centre);
            h.assertTrue(facing == side && facing.getUnitVec3().dot(toGod) > 0, "set down by a god to its " + side + ", the Shrine faces " + facing);
            h.setBlock(at, placed);
            h.assertTrue(h.getLevel().getBlockEntity(abs) instanceof claude.khazuk.ShrineBlockEntity, "no Shrine's block entity where it was set facing " + facing);
            h.assertTrue(h.getLevel().getBlockState(abs).isCollisionShapeFullBlock(h.getLevel(), abs), "the Shrine's collision isn't the whole block");
        }
        h.succeed();
    }
}
