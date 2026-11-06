package claude.khazuk;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Dwarfs' registry entries: the Shrine (block, item, block entity), the Miner and the Warrior (and their eggs), and a tab. */
public final class KRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Khazuk.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Khazuk.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Khazuk.MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Khazuk.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Khazuk.MODID);

    public static final DeferredBlock<ShrineBlock> SHRINE = BLOCKS.registerBlock("ancestor_shrine", ShrineBlock::new,
            // noOcclusion: the statue (a GeckoLib block entity, drawn past the block) is lit by the light in its own cell, and an occluding block
            // holds none there, which draws it black (handoffs/ancestor_shrine_2026-11-04_done.md); its 1x1 collision stays. Blast-proof (1200):
            // a blast would drop it bare and lose the hold with it (the bug hunt of 2d0b04c); its god lifts it whole (ShrineBlock#playerDestroy)
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(3f, 1200f).sound(SoundType.AMETHYST).lightLevel(s -> 9).noOcclusion());
    /**
     * The halls' gilding (the bug hunt of 2d0b04c): the hold's templates were built of real gold blocks, and a Great Hall dug for 30 gold
     * held 249 of them. Gilded Stone looks like a gold block and Hoard Gold like raw gold, and each drops itself: the hold's look, not its money.
     */
    public static final DeferredBlock<net.minecraft.world.level.block.Block> GILDED_STONE = BLOCKS.registerBlock("gilded_stone",
            net.minecraft.world.level.block.Block::new, p -> p.mapColor(MapColor.GOLD).strength(3f, 6f).sound(SoundType.METAL));
    public static final DeferredBlock<net.minecraft.world.level.block.Block> HOARD_GOLD = BLOCKS.registerBlock("hoard_gold",
            net.minecraft.world.level.block.Block::new, p -> p.mapColor(MapColor.RAW_IRON).strength(3f, 6f).sound(SoundType.METAL));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> GILDED_STONE_ITEM = ITEMS.registerItem("gilded_stone",
            p -> new net.minecraft.world.item.BlockItem(GILDED_STONE.get(), p.useBlockDescriptionPrefix()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> HOARD_GOLD_ITEM = ITEMS.registerItem("hoard_gold",
            p -> new net.minecraft.world.item.BlockItem(HOARD_GOLD.get(), p.useBlockDescriptionPrefix()));

    /** The Shrine's item: the statue itself in hand and in the inventory (GeckoLib's item render, client/ShrineRenderer). */
    public static final DeferredItem<ShrineItem> SHRINE_ITEM = ITEMS.registerItem("ancestor_shrine",
            p -> new ShrineItem(SHRINE.get(), p.useBlockDescriptionPrefix().stacksTo(1).fireResistant()));   // a hold may ride in it: one a stack, unburnt

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrineBlockEntity>> SHRINE_ENTITY = BLOCK_ENTITIES.register("ancestor_shrine",
            () -> new BlockEntityType<>(ShrineBlockEntity::new, SHRINE.get()));

    /** About 1.5 blocks tall: a small worker. */
    public static final DeferredHolder<EntityType<?>, EntityType<Miner>> MINER = ENTITIES.register("miner",
            () -> EntityType.Builder.of(Miner::new, MobCategory.CREATURE).sized(0.8f, 1.45f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("miner")));
    /** About 1.8 blocks tall: the worker's armoured kin. */
    public static final DeferredHolder<EntityType<?>, EntityType<Warrior>> WARRIOR = ENTITIES.register("dwarf_warrior",
            () -> EntityType.Builder.of(Warrior::new, MobCategory.CREATURE).sized(0.8f, 1.45f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("dwarf_warrior")));

    /** D5: the Runesmith, the hold's hero at its Runic Forge. A Dwarf, a little taller in his crested helm. */
    public static final DeferredHolder<EntityType<?>, EntityType<Runesmith>> RUNESMITH = ENTITIES.register("runesmith",
            () -> EntityType.Builder.of(Runesmith::new, MobCategory.CREATURE).sized(0.8f, 1.5f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("runesmith")));

    /** 0.11: the Longbeards, the hold's veterans: hand weapon and shield, or the great axe. A Dwarf under a great-horned helm. */
    public static final DeferredHolder<EntityType<?>, EntityType<Longbeard>> LONGBEARD = ENTITIES.register("longbeard",
            () -> EntityType.Builder.of(Longbeard::new, MobCategory.CREATURE).sized(0.8f, 1.5f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("longbeard")));
    public static final DeferredHolder<EntityType<?>, EntityType<Longbeard>> LONGBEARD_GREAT = ENTITIES.register("longbeard_great",
            () -> EntityType.Builder.of(Longbeard::new, MobCategory.CREATURE).sized(0.8f, 1.5f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("longbeard_great")));

    /** 0.14: the Ironbreakers (the deeps' guard, gromril and Cinderblast bombs) and the Hammerers (the King's Guard, great hammers). */
    public static final DeferredHolder<EntityType<?>, EntityType<Ironbreaker>> IRONBREAKER = ENTITIES.register("ironbreaker",
            () -> EntityType.Builder.of(Ironbreaker::new, MobCategory.CREATURE).sized(0.8f, 1.5f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("ironbreaker")));
    public static final DeferredHolder<EntityType<?>, EntityType<Hammerer>> HAMMERER = ENTITIES.register("hammerer",
            () -> EntityType.Builder.of(Hammerer::new, MobCategory.CREATURE).sized(0.8f, 1.5f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("hammerer")));
    /** An Ironbreaker's Cinderblast bomb in flight. */
    public static final DeferredHolder<EntityType<?>, EntityType<Cinderblast>> CINDERBLAST = ENTITIES.register("cinderblast",
            () -> EntityType.Builder.<Cinderblast>of(Cinderblast::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(5)
                    .noSave().build(key("cinderblast")));

    /** D6: the hold's missile troops, raised by its Barracks: the Quarreller (crossbow) and the Thunderer (handgun). */
    public static final DeferredHolder<EntityType<?>, EntityType<Quarreller>> QUARRELLER = ENTITIES.register("quarreller",
            () -> EntityType.Builder.of(Quarreller::new, MobCategory.CREATURE).sized(0.8f, 1.45f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("quarreller")));
    public static final DeferredHolder<EntityType<?>, EntityType<Thunderer>> THUNDERER = ENTITIES.register("thunderer",
            () -> EntityType.Builder.of(Thunderer::new, MobCategory.CREATURE).sized(0.8f, 1.45f).eyeHeight(1.2f).clientTrackingRange(10)
                    .build(key("thunderer")));
    /** D5b: the Runelord, the Runesmith raised to a lord. */
    public static final DeferredHolder<EntityType<?>, EntityType<Runelord>> RUNELORD = ENTITIES.register("runelord",
            () -> EntityType.Builder.of(Runelord::new, MobCategory.CREATURE).sized(0.8f, 1.55f).eyeHeight(1.25f).clientTrackingRange(10)
                    .build(key("runelord")));
    /** D5b: the Anvil of Doom, the god's to stand on and strike: the detail session's deck 2.75 x 2.44 blocks, the anvil's face 1.56 up. */
    public static final DeferredHolder<EntityType<?>, EntityType<claude.khazuk.hold.AnvilOfDoom>> ANVIL_OF_DOOM = ENTITIES.register("anvil_of_doom",
            () -> EntityType.Builder.of(claude.khazuk.hold.AnvilOfDoom::new, MobCategory.MISC).sized(2.6f, 1.6f).eyeHeight(1.2f).fireImmune()
                    .clientTrackingRange(10).build(key("anvil_of_doom")));

    /** D7: Grungni's Hammer as it falls (god.GrungniHammer): drawn from the sky down, so its box is a block and it is never culled. */
    public static final DeferredHolder<EntityType<?>, EntityType<claude.khazuk.god.GrungniHammer>> GRUNGNIS_HAMMER_STRIKE = ENTITIES.register("grungnis_hammer",
            () -> EntityType.Builder.<claude.khazuk.god.GrungniHammer>of(claude.khazuk.god.GrungniHammer::new, MobCategory.MISC).sized(1.0f, 1.0f)
                    .clientTrackingRange(16).updateInterval(20).noSave().build(key("grungnis_hammer")));
    /** Its blow (data/khazuk/damage_type/grungnis_hammer.json): "struck down by Grungni's Hammer". */
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> HAMMER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(Khazuk.MODID, "grungnis_hammer"));
    /** D7: the Ancestor Gods' tokens (god.AncestorPowers): each calls its power on its hold's devotion. */
    public static final DeferredItem<claude.khazuk.god.AncestorPowers.Token> GRUNGNIS_HAMMER = ITEMS.registerItem("grungnis_hammer",
            p -> new claude.khazuk.god.AncestorPowers.Token(p.stacksTo(1).fireResistant(), claude.khazuk.god.AncestorPowers.Power.HAMMER));
    public static final DeferredItem<claude.khazuk.god.AncestorPowers.Token> VALAYAS_WARD = ITEMS.registerItem("valayas_ward",
            p -> new claude.khazuk.god.AncestorPowers.Token(p.stacksTo(1).fireResistant(), claude.khazuk.god.AncestorPowers.Power.WARD));
    public static final DeferredItem<claude.khazuk.god.AncestorPowers.Token> GRIMNIRS_FURY = ITEMS.registerItem("grimnirs_fury",
            p -> new claude.khazuk.god.AncestorPowers.Token(p.stacksTo(1).fireResistant(), claude.khazuk.god.AncestorPowers.Power.FURY));

    /** The Ancestors' Plan of the Keep: since 0.13.0 the King's Throne Room, dug off the hold's halls (the hold is founded through its Hold Gate). */
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_KEEP = ITEMS.registerItem("plan_keep",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_throne_room"));   // 0.13.0: the Keep is the King's Throne Room, dug

    /** The Ancestors' Plans of the dug halls: used on a wall of the hold's open space, the Miners dig the hall out behind it. */
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_GREAT_HALL = ITEMS.registerItem("plan_great_hall",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_grand_hall"));   // 0.13.0: the grand one
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_BARRACKS = ITEMS.registerItem("plan_barracks",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_barracks"));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_MINE = ITEMS.registerItem("plan_mine",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_mine"));
    /** D4: the Dammaz Kron, the hold's Book of Grudges as its god carries it. */
    public static final DeferredItem<claude.khazuk.hold.BookOfGrudges> DAMMAZ_KRON = ITEMS.registerItem("dammaz_kron",
            p -> new claude.khazuk.hold.BookOfGrudges(p.stacksTo(1)));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_BREWERY = ITEMS.registerItem("plan_brewery",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_brewery"));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_TREASURY = ITEMS.registerItem("plan_treasury",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_treasury"));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_RUNIC_FORGE = ITEMS.registerItem("plan_runic_forge",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_runic_forge"));
    public static final DeferredItem<SpawnEggItem> RUNESMITH_EGG = ITEMS.registerItem("runesmith_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(RUNESMITH.get())));
    public static final DeferredItem<SpawnEggItem> RUNELORD_EGG = ITEMS.registerItem("runelord_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(RUNELORD.get())));
    public static final DeferredItem<SpawnEggItem> QUARRELLER_EGG = ITEMS.registerItem("quarreller_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(QUARRELLER.get())));
    public static final DeferredItem<SpawnEggItem> THUNDERER_EGG = ITEMS.registerItem("thunderer_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(THUNDERER.get())));
    public static final DeferredItem<SpawnEggItem> LONGBEARD_EGG = ITEMS.registerItem("longbeard_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(LONGBEARD.get())));
    public static final DeferredItem<SpawnEggItem> LONGBEARD_GREAT_EGG = ITEMS.registerItem("longbeard_great_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(LONGBEARD_GREAT.get())));
    public static final DeferredItem<SpawnEggItem> IRONBREAKER_EGG = ITEMS.registerItem("ironbreaker_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(IRONBREAKER.get())));
    public static final DeferredItem<SpawnEggItem> HAMMERER_EGG = ITEMS.registerItem("hammerer_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(HAMMERER.get())));
    /** D5b: the Anvil of Doom, carried: set it down on the ground, facing the way you face. */
    public static final DeferredItem<claude.khazuk.hold.AnvilOfDoomItem> ANVIL_OF_DOOM_ITEM = ITEMS.registerItem("anvil_of_doom",
            p -> new claude.khazuk.hold.AnvilOfDoomItem(p.stacksTo(1).fireResistant()));

    /** Thorgrim's Throne of Power, the god's mount: 2.4 across the bearers' shields, the seat 2 up (a rider sits above its box). */
    public static final DeferredHolder<EntityType<?>, EntityType<claude.khazuk.throne.ThroneOfPower>> THRONE = ENTITIES.register("throne_of_power",
            () -> EntityType.Builder.of(claude.khazuk.throne.ThroneOfPower::new, MobCategory.MISC).sized(2.4f, 2.1f).eyeHeight(1.8f)
                    .passengerAttachments(new net.minecraft.world.phys.Vec3(0.0, 1.85, -0.5)).fireImmune().clientTrackingRange(10)
                    .build(key("throne_of_power")));
    /** The Throne folded up: set it down on the ground, facing the way you face. */
    public static final DeferredItem<claude.khazuk.throne.ThroneItem> THRONE_ITEM = ITEMS.registerItem("throne_of_power",
            p -> new claude.khazuk.throne.ThroneItem(p.stacksTo(1).fireResistant()));

    public static final DeferredItem<SpawnEggItem> MINER_EGG = ITEMS.registerItem("miner_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(MINER.get())));
    public static final DeferredItem<SpawnEggItem> WARRIOR_EGG = ITEMS.registerItem("dwarf_warrior_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(WARRIOR.get())));

    /** The Ancestors' Plan of the Hold Gate: set out on the ground, the Miners raise it. */
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_HOLD_GATE = ITEMS.registerItem("plan_hold_gate",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_hold_gate"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("khazuk", () -> CreativeModeTab.builder()
            .title(Component.literal("Khazuk!"))
            .icon(() -> SHRINE_ITEM.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(SHRINE_ITEM.get());
                out.accept(PLAN_KEEP.get());
                out.accept(PLAN_HOLD_GATE.get());
                out.accept(PLAN_GREAT_HALL.get());
                out.accept(PLAN_BARRACKS.get());
                out.accept(DAMMAZ_KRON.get());
                out.accept(PLAN_BREWERY.get());
                out.accept(PLAN_TREASURY.get());
                out.accept(PLAN_RUNIC_FORGE.get());
                out.accept(RUNESMITH_EGG.get());
                out.accept(RUNELORD_EGG.get());
                out.accept(QUARRELLER_EGG.get());
                out.accept(THUNDERER_EGG.get());
                out.accept(LONGBEARD_EGG.get());
                out.accept(LONGBEARD_GREAT_EGG.get());
                out.accept(IRONBREAKER_EGG.get());
                out.accept(HAMMERER_EGG.get());
                out.accept(ANVIL_OF_DOOM_ITEM.get());
                out.accept(GRUNGNIS_HAMMER.get());
                out.accept(VALAYAS_WARD.get());
                out.accept(GRIMNIRS_FURY.get());
                out.accept(PLAN_MINE.get());
                out.accept(THRONE_ITEM.get());
                out.accept(GILDED_STONE_ITEM.get());
                out.accept(HOARD_GOLD_ITEM.get());
                out.accept(MINER_EGG.get());
                out.accept(WARRIOR_EGG.get());
            })
            .build());

    private KRegistry() {
    }

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Khazuk.MODID, name));
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(KRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(MINER.get(), Miner.createAttributes().build());
        e.put(WARRIOR.get(), Warrior.createAttributes().build());
        e.put(RUNESMITH.get(), Runesmith.createAttributes().build());
        e.put(RUNELORD.get(), Runelord.createAttributes().build());
        e.put(QUARRELLER.get(), Quarreller.createAttributes().build());
        e.put(THUNDERER.get(), Thunderer.createAttributes().build());
        e.put(LONGBEARD.get(), Longbeard.createAttributes(false).build());
        e.put(LONGBEARD_GREAT.get(), Longbeard.createAttributes(true).build());
        e.put(IRONBREAKER.get(), Ironbreaker.createAttributes().build());
        e.put(HAMMERER.get(), Hammerer.createAttributes().build());
        e.put(ANVIL_OF_DOOM.get(), claude.khazuk.hold.AnvilOfDoom.createAttributes().build());
        e.put(THRONE.get(), claude.khazuk.throne.ThroneOfPower.createAttributes().build());
    }
}
