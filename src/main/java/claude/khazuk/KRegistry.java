package claude.khazuk;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
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
            p -> p.mapColor(MapColor.COLOR_LIGHT_GREEN).strength(3f, 6f).sound(SoundType.AMETHYST).lightLevel(s -> 9));
    public static final DeferredItem<BlockItem> SHRINE_ITEM = ITEMS.registerSimpleBlockItem("ancestor_shrine", SHRINE, Item.Properties::new);

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

    /** The Ancestors' Plan of the Keep: the hold's founding hall, raised by the god; then the Shrine goes on its dais. */
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_KEEP = ITEMS.registerItem("plan_keep",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_keep"));

    /** The Ancestors' Plans of the dug halls: used on a wall of the hold's open space, the Miners dig the hall out behind it. */
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_GREAT_HALL = ITEMS.registerItem("plan_great_hall",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_great_hall"));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_BARRACKS = ITEMS.registerItem("plan_barracks",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_barracks"));
    public static final DeferredItem<claude.khazuk.hold.AncestorPlan> PLAN_MINE = ITEMS.registerItem("plan_mine",
            p -> new claude.khazuk.hold.AncestorPlan(p.stacksTo(1), "dawi_mine"));

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
                out.accept(PLAN_MINE.get());
                out.accept(THRONE_ITEM.get());
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
        e.put(THRONE.get(), claude.khazuk.throne.ThroneOfPower.createAttributes().build());
    }
}
