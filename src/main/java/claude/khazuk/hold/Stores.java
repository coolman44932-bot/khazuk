package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.work.Bins;
import claude.settlementcore.work.HoardWork;
import claude.settlementcore.work.MineWork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hold's stores (the user, 2026-11-09: "is there a storage room", then "yeah but also make the buildings passively add ore and
 * whatever they produce even if they dont mine it out or whatever"). The Storeroom is the engine's Hoard (work/HoardWork, the hall
 * "dawi_storeroom": barrels in five bays, Metals &amp; Gems, Stone, Food, Timber, Spoils); the engine's haul already carries the Mine's
 * Ore Carts there, and its larder counts the food in its barrels as the hold's. This is what Khazuk! adds on top:
 * <ul>
 * <li><b>What the digs cut</b> ({@link #cut}, from the Shrine's takeSpoils): an ore's drops, as an iron pick would get them, go to the
 * stores (gold ore stays the hold's gold, as it was); the rock itself goes to the Stone bay as cobble while that bay has room. Before
 * this all of it was lost.</li>
 * <li><b>What the halls make</b> ({@link #produce}, every {@value #PRODUCE_SECONDS} s, each hall of the hold, with nobody working it):
 * a Mine {@value #MINE_ROLLS} rolls of its depth's ores (the engine's MineWork#roll: a deeper Mine, better ore; raw gold as the hold's
 * gold) and {@value #MINE_STONE} cobble; a Brewery {@value #BREWERY_ALE} ale with no wheat brought; a Treasury {@value #TREASURY_GOLD}
 * gold; an Engineers' Guild {@value #GUILD_POWDER} black powder; a Runic Forge smelts up to {@value #FORGE_SMELTS} raw iron, copper
 * or gold in the Storeroom into ingots.</li>
 * </ul>
 * Goods go into the Storeroom (their own bay, then Spoils, then anywhere), and with no Storeroom yet onto the pile at the Shrine (the
 * engine's spoils, {@value Settlement#MAX_SPOIL_STACKS} stacks), which the haul carries to the first Storeroom dug. With nowhere left
 * to put them they are not made: nothing is ever thrown on the floor. Buffs only: a hold with no Storeroom is as it was, plus the pile.
 */
public final class Stores {

    public static final int PRODUCE_SECONDS = 60;
    public static final int MINE_ROLLS = 2, MINE_STONE = 4, BREWERY_ALE = 6, TREASURY_GOLD = 1, FORGE_SMELTS = 8, GUILD_POWDER = 2;
    /** Cobble waiting for the next second at most (a big dig cuts hundreds of cells a second with a big crew). */
    static final int CUT_STONE_MAX = 4096;
    /** Stacks on the pile at the Shrine before its god is told to dig a Storeroom. */
    static final int PILE_HINT = 4;
    /** The Stone bay's name: the engine's section. */
    public static final String STONE_BAY = HoardWork.Kind.STONE.bin;

    /** What the digs cut since the last second: an item and how many. */
    private final Map<Item, Integer> cut = new LinkedHashMap<>();
    private int clock;
    /** Whether the Stone bay had room when last looked (so the digs' rock is worth counting). */
    private boolean stoneRoom;
    private boolean toldPile;
    private boolean dirty;

    /** A dig cut {@code was} at {@code pos}: its ore's drops and (while the Stone bay has room) its rock, put away at the next second. */
    public void cut(ServerLevel level, BlockPos pos, BlockState was) {
        if (was.isAir()) {
            return;
        }
        if (was.is(Tags.Blocks.ORES)) {
            if (was.is(BlockTags.GOLD_ORES)) {
                return;                                          // the hold's gold: paid by the Shrine itself
            }
            for (ItemStack s : Block.getDrops(was, level, pos, null, null, new ItemStack(Items.IRON_PICKAXE))) {
                cut.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        } else if (stoneRoom && (was.is(BlockTags.BASE_STONE_OVERWORLD) || was.is(Tags.Blocks.COBBLESTONES))) {
            Item it = was.is(Blocks.STONE) ? Items.COBBLESTONE : was.is(Blocks.DEEPSLATE) ? Items.COBBLED_DEEPSLATE : was.getBlock().asItem();
            if (it != Items.AIR && cut.getOrDefault(it, 0) < CUT_STONE_MAX) {
                cut.merge(it, 1, Integer::sum);
            }
        }
    }

    /** The Shrine's second: what the digs cut is put away, and every {@value #PRODUCE_SECONDS} s the halls make their goods. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        clock++;
        dirty |= clock % PRODUCE_SECONDS == 0;                   // the clock saved now and then, so a restart keeps the next goods due
        boolean flush = !cut.isEmpty(), make = clock % PRODUCE_SECONDS == 0;
        if (!flush && !make && clock % 5 != 0) {
            return;
        }
        List<BlockPos> stoneBay = stoneBay(level, hold);
        if (flush) {
            for (Map.Entry<Item, Integer> e : cut.entrySet()) {
                boolean stone = HoardWork.kind(hold, new ItemStack(e.getKey())) == HoardWork.Kind.STONE;
                int left = e.getValue(), max = new ItemStack(e.getKey()).getMaxStackSize();
                while (left > 0) {
                    ItemStack part = new ItemStack(e.getKey(), Math.min(left, max));
                    left -= part.getCount();
                    if (stone) {
                        if (!Bins.insert(level, stoneBay, part).isEmpty()) {
                            break;                               // the bay is full: the rest of the rock is rubble, as it always was
                        }
                    } else if (!put(level, hold, part, "The digs")) {
                        break;
                    }
                }
            }
            cut.clear();
        }
        if (make) {
            produce(level, hold, stoneBay);
        }
        stoneRoom = !stoneBay.isEmpty() && Bins.hasRoom(level, stoneBay, new ItemStack(Items.COBBLED_DEEPSLATE));
        if (!toldPile && hold.works().all(HoardWork.class).isEmpty() && hold.spoils().size() >= PILE_HINT) {
            toldPile = true;
            dirty = true;
            hold.tellOwner(hold.settlementName() + ": the hold's goods are piling up at the Shrine with nowhere to go. Dig a Storeroom, "
                    + "and the Dawi carry them to its barrels.", ChatFormatting.YELLOW);
        }
    }

    /** The barrels of the Stone bay, in every Storeroom of the hold that is loaded. */
    private static List<BlockPos> stoneBay(ServerLevel level, ShrineBlockEntity hold) {
        List<BlockPos> out = new ArrayList<>();
        for (HoardWork h : hold.works().all(HoardWork.class)) {
            if (level.isLoaded(h.box().getCenter())) {
                out.addAll(Bins.named(level, h.box(), STONE_BAY));
            }
        }
        return out;
    }

    /**
     * Into the stores: the Storeroom (its own bay, Spoils, anywhere), else the pile at the Shrine while it has room. False, and nothing
     * done, when neither has room: goods are never dropped on the floor (Works#stow would, at the Shrine).
     */
    public static boolean put(ServerLevel level, ShrineBlockEntity hold, ItemStack s, String from) {
        if (s.isEmpty()) {
            return true;
        }
        boolean room = hold.spoils().size() < Settlement.MAX_SPOIL_STACKS;
        for (HoardWork h : hold.works().all(HoardWork.class)) {
            if (room) {
                break;
            }
            room = level.isLoaded(h.box().getCenter()) && h.hasRoom(level, s);
        }
        if (!room) {
            return false;
        }
        hold.works().stow(level, s, from);
        return true;
    }

    /** One round of the halls' goods, now (the tests call it too). */
    public void produce(ServerLevel level, ShrineBlockEntity hold) {
        produce(level, hold, stoneBay(level, hold));
    }

    private void produce(ServerLevel level, ShrineBlockEntity hold, List<BlockPos> stoneBay) {
        RandomSource r = level.getRandom();
        for (Building b : List.copyOf(hold.buildings())) {
            Rooms.Room room = Rooms.get(b.room());
            if (room == null) {
                continue;
            }
            Rooms.Function f = room.function();
            String name = room.displayName();
            if (f == Rooms.Function.OREMINE) {
                int y = b.box().minY() + 1;
                for (int i = 0; i < MINE_ROLLS; i++) {
                    ItemStack got = MineWork.roll(r, y);
                    if (got.isEmpty()) {
                        hold.addCurrency(1, name);               // the roll's odd one out (the Skaven's warpstone): a nugget's worth
                    } else if (got.is(Items.RAW_GOLD)) {
                        hold.addCurrency(got.getCount() * (ShrineBlockEntity.GOLD_PER_ORE
                                + (hold.has(HoldRooms.TREASURY) ? ShrineBlockEntity.TREASURY_BONUS : 0)), name);
                    } else {
                        put(level, hold, got, name);
                    }
                }
                Bins.insert(level, stoneBay, new ItemStack(y < 0 ? Items.COBBLED_DEEPSLATE : Items.COBBLESTONE, MINE_STONE));
            } else if (f == HoldRooms.BREWERY) {
                hold.brew().addAle(BREWERY_ALE);
            } else if (f == HoldRooms.TREASURY) {
                hold.addCurrency(TREASURY_GOLD, name);
            } else if (f == HoldRooms.ENGINEERS_GUILD) {
                put(level, hold, new ItemStack(Items.GUNPOWDER, GUILD_POWDER), name);
            } else if (f == HoldRooms.RUNIC_FORGE) {
                smelt(level, hold, name);
            }
        }
    }

    private static final Item[][] SMELTS = {{Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_COPPER, Items.COPPER_INGOT},
            {Items.RAW_GOLD, Items.GOLD_INGOT}};

    /** The Runic Forge's fires: up to {@value #FORGE_SMELTS} raw ore out of the Storeroom's barrels, back in as ingots. */
    private static void smelt(ServerLevel level, ShrineBlockEntity hold, String name) {
        int left = FORGE_SMELTS;
        for (HoardWork h : hold.works().all(HoardWork.class)) {
            if (!level.isLoaded(h.box().getCenter())) {
                continue;
            }
            List<BlockPos> barrels = h.barrels(level);
            for (Item[] s : SMELTS) {
                if (left <= 0) {
                    return;
                }
                ItemStack raw = Bins.take(level, barrels, st -> st.is(s[0]), left);
                if (!raw.isEmpty()) {
                    left -= raw.getCount();
                    ItemStack rest = h.put(level, new ItemStack(s[1], raw.getCount()));   // room for it: the ore just came out
                    if (!rest.isEmpty()) {
                        hold.works().stow(level, rest, name);
                    }
                }
            }
        }
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void save(CompoundTag tag) {
        tag.putInt("stores_clock", clock);
        tag.putBoolean("stores_told", toldPile);
    }

    public void load(CompoundTag tag) {
        clock = tag.getIntOr("stores_clock", 0);
        toldPile = tag.getBooleanOr("stores_told", false);
    }
}
