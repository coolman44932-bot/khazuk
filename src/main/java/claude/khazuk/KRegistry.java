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
            () -> EntityType.Builder.of(Miner::new, MobCategory.CREATURE).sized(0.6f, 1.5f).eyeHeight(1.3f).clientTrackingRange(10)
                    .build(key("miner")));
    /** About 1.8 blocks tall: the worker's armoured kin. */
    public static final DeferredHolder<EntityType<?>, EntityType<Warrior>> WARRIOR = ENTITIES.register("dwarf_warrior",
            () -> EntityType.Builder.of(Warrior::new, MobCategory.CREATURE).sized(0.7f, 1.8f).eyeHeight(1.6f).clientTrackingRange(10)
                    .build(key("dwarf_warrior")));

    public static final DeferredItem<SpawnEggItem> MINER_EGG = ITEMS.registerItem("miner_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(MINER.get())));
    public static final DeferredItem<SpawnEggItem> WARRIOR_EGG = ITEMS.registerItem("dwarf_warrior_spawn_egg",
            p -> new SpawnEggItem(p.spawnEgg(WARRIOR.get())));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("khazuk", () -> CreativeModeTab.builder()
            .title(Component.literal("Khazuk!"))
            .icon(() -> SHRINE_ITEM.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(SHRINE_ITEM.get());
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
    }
}
