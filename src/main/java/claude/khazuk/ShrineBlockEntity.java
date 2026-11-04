package claude.khazuk;

import claude.khazuk.hold.HoldCrag;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.settlement.Brood;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementBlockEntity;
import claude.settlementcore.settlement.Spawning;
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Dwarfs' settlement: the engine's {@link SettlementBlockEntity} with the race's hooks answered in the plainest way. Its people eat
 * a little, a round of spawning every {@value #ROUND_SECONDS} s makes two workers for every guard out of food, and it brings them up beside
 * it while it has room ({@value #UP_AT_ONCE} up at once, {@value #RESERVE_CAP} in all). Its land is a {@value #RADIUS}-block square round it.
 */
public class ShrineBlockEntity extends SettlementBlockEntity {

    static final Keys KEYS = new Keys("hold_name", "gold", "people", "reserve_miners", "reserve_warriors");
    public static final int ROUND_SECONDS = 30;
    public static final int UP_AT_ONCE = 8;
    public static final int RESERVE_CAP = 24;
    /** A hold's land reaches 40 blocks round its Shrine: room for its gate (53 across) and its halls. */
    public static final int RADIUS = 40;
    /** A worker costs 4 food points, a guard 6. */
    static final Brood.Cost WORKER_COST = new Brood.Cost(4, 0, "Workers raised");
    static final Brood.Cost GUARD_COST = new Brood.Cost(6, 0, "Guards raised");

    /** Rounds of spawning so far, for the tests. */
    public int rounds;
    /** The mountains the god raises round the hold's gates. */
    private final HoldCrag crags = new HoldCrag();
    /** D3: the hold's malt, ale and hoard. */
    private final claude.khazuk.hold.Brewhouse brew = new claude.khazuk.hold.Brewhouse();
    /** D4: the Dammaz Kron, the hold's Book of Grudges. */
    private final claude.khazuk.hold.Grudges grudges = new claude.khazuk.hold.Grudges();
    /** D5: the hold's runes, and the Runesmith's work at its anvil. */
    private final claude.khazuk.hold.Runes runes = new claude.khazuk.hold.Runes();
    /** D6: the Barracks' missile troops. */
    private final claude.khazuk.hold.Ranks ranks = new claude.khazuk.hold.Ranks();
    /** D7: the hold's devotion, what the Ancestor Gods' powers are paid from. */
    private final claude.khazuk.god.Devotion devotion = new claude.khazuk.god.Devotion();

    public ShrineBlockEntity(BlockPos pos, BlockState state) {
        super(KRegistry.SHRINE_ENTITY.get(), pos, state, KEYS);
    }

    /** The Shrine {@code p} owns, among the loaded ones of its level. */
    @Nullable
    public static ShrineBlockEntity ownedAt(ServerPlayer p, BlockPos at) {
        ShrineBlockEntity inLand = null, nearest = null;
        double inLandD = Double.MAX_VALUE, nearestD = Double.MAX_VALUE;
        for (Settlement s : claude.settlementcore.settlement.SettlementSpawns.loaded(p.level())) {
            if (s instanceof ShrineBlockEntity h && p.getUUID().equals(h.owner())) {
                double d = h.getBlockPos().distSqr(at);
                if (d < nearestD) {
                    nearest = h;
                    nearestD = d;
                }
                if (d < inLandD && h.inSettlement(at)) {
                    inLand = h;
                    inLandD = d;
                }
            }
        }
        return inLand != null ? inLand : nearest;
    }

    /** The hold {@code p} owns: the one whose land they stand in, else the nearest. */
    @Nullable
    static ShrineBlockEntity ownedBy(ServerPlayer p) {
        return ownedAt(p, p.blockPosition());
    }

    public HoldCrag crags() {
        return crags;
    }

    public claude.khazuk.hold.Brewhouse brew() {
        return brew;
    }

    public claude.khazuk.hold.Grudges grudges() {
        return grudges;
    }

    public claude.khazuk.hold.Runes runes() {
        return runes;
    }

    public claude.khazuk.hold.Ranks ranks() {
        return ranks;
    }

    public claude.khazuk.god.Devotion devotion() {
        return devotion;
    }

    /** Broken: the kit at the Runesmith's anvil drops where the Shrine stood, never lost (the review of a3aecff). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel sl) {
            runes.dropKit(sl, pos);
        }
        super.preRemoveSideEffects(pos, state);
    }

    /** The engine's second, then a second's rise of a gate's mountain. */
    @Override
    public void second(ServerLevel level) {
        super.second(level);
        crags.second(level, this, HoldCrag.PER_SECOND);
        brew.second(level, this);
        runes.second(level, this);
        ranks.second(level, this);
        devotion.second(level, this);
        if (crags.takeDirty() | brew.takeDirty() | grudges.takeDirty() | runes.takeDirty() | devotion.takeDirty()) {
            setChanged();                                        // the last step too, so a restart never raises the summit twice
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        crags.save(tag);
        brew.save(tag);
        grudges.save(tag);
        runes.save(tag);
        devotion.save(tag);
        out.store("khazuk", CompoundTag.CODEC, tag);
        out.storeNullable("rune_kit", net.minecraft.world.item.ItemStack.CODEC, runes.kit().isEmpty() ? null : runes.kit());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        CompoundTag tag = in.read("khazuk", CompoundTag.CODEC).orElseGet(CompoundTag::new);
        crags.load(tag);
        brew.load(tag);
        grudges.load(tag);
        runes.load(tag);
        devotion.load(tag);
        runes.kit(in.read("rune_kit", net.minecraft.world.item.ItemStack.CODEC).orElse(net.minecraft.world.item.ItemStack.EMPTY));
    }

    @Override
    protected float eatsPerSecond() {
        return peopleCount() * 0.02f;                            // a point each per 50 s
    }

    @Override
    protected Spawning spawning() {
        return (level, settlement, brood) -> {
            rounds++;
            int workers = brood.add(false, 2, WORKER_COST);
            int guards = brood.add(true, 1, GUARD_COST);
            for (int i = 0; i < workers + guards; i++) {
                brood.bringUp(i >= workers, getBlockPos().above());
            }
        };
    }

    @Override
    protected int spawnSeconds() {
        return ROUND_SECONDS;
    }

    @Override
    protected int reserveCap() {
        return RESERVE_CAP;
    }

    @Override
    public int peopleCap() {
        return UP_AT_ONCE;
    }

    @Override
    protected String currencyRoad() {
        return "The hold's gold";
    }

    @Override
    public String settlementName() {
        String n = super.settlementName();
        return n == null || n.isEmpty() ? "The Hold" : n;
    }

    @Override
    public EntityType<? extends SettlerMob> workerType() {
        return KRegistry.MINER.get();
    }

    @Override
    public EntityType<? extends SettlerMob> soldierType() {
        return KRegistry.WARRIOR.get();
    }

    @Override
    protected void raised(SettlerMob mob, boolean soldier, boolean teem) {
        mob.hearten(100f);
    }

    @Override
    public Item currencyItem() {
        return Items.GOLD_INGOT;
    }

    @Override
    public Item currencyBlock() {
        return Items.GOLD_BLOCK;
    }

    @Override
    public boolean inSettlement(BlockPos p) {
        BlockPos c = getBlockPos();
        return Math.abs(p.getX() - c.getX()) <= RADIUS && Math.abs(p.getZ() - c.getZ()) <= RADIUS && Math.abs(p.getY() - c.getY()) <= RADIUS;
    }

    @Override
    public List<BoundingBox> settlementBoxes() {
        BlockPos c = getBlockPos();
        return List.of(new BoundingBox(c.getX() - RADIUS, c.getY() - RADIUS, c.getZ() - RADIUS, c.getX() + RADIUS, c.getY() + RADIUS, c.getZ() + RADIUS));
    }

    /**
     * A dig leaves the Shrine itself, and the hold's other buildings, as they are: their open space (halls dug off each other open into each
     * other) and their masonry (a cell their own template sets a block in). Anything else inside another building's box may be dug: the
     * mountain Grungni raised round the Hold Gate stands in the gate's box where its template is air, and a hall dug off the end of its
     * passage cuts its door and its walls there.
     */
    @Override
    public long[] keepOutCells(ServerLevel level, PlanPlacement pl) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet keep = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        BoundingBox area = pl.box();
        for (claude.settlementcore.settlement.Building b : buildings()) {
            PlanPlacement other = claude.settlementcore.settlement.BuildingBook.placementOf(b);
            if (other == null || b.box().equals(area) || !b.box().intersects(area)) {
                continue;
            }
            for (claude.settlementcore.room.RoomTemplates.Entry e : claude.settlementcore.room.RoomTemplates.get(level, other.room().id())) {
                BlockPos w = other.world(e.local());
                if (area.isInside(w) && (!e.state().isAir() || level.getBlockState(w).isAir())) {
                    keep.add(w.asLong());
                }
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (level.isLoaded(p) && level.getBlockEntity(p) != null) {
                keep.add(p.asLong());                             // a chest, a furnace, a sign in the way: left standing, never emptied
            }
        }
        keep.add(getBlockPos().asLong());
        return keep.toLongArray();
    }

    /**
     * Gold ore a dig cuts through goes to the hold's gold, two to the ore (a dwarf gets more out of it than a player's pick), three with a
     * Treasury dug (D3). A container
     * it cuts (one set down in the way after the dig began: the ones there before are left standing, keepOutCells) spills what it held
     * where it stood, before the dig empties it.
     */
    @Override
    public void takeSpoils(ServerLevel level, BlockPos pos, BlockState was, @Nullable BlockEntity be) {
        if (was.is(net.minecraft.tags.BlockTags.GOLD_ORES)) {
            addCurrency(GOLD_PER_ORE + (has(claude.khazuk.hold.HoldRooms.TREASURY) ? TREASURY_BONUS : 0), "Gold dug out");
        }
        if (be instanceof net.minecraft.world.Container c && !c.isEmpty()) {
            net.minecraft.world.Containers.dropContents(level, pos, c);
        }
    }

    public static final int GOLD_PER_ORE = 2, TREASURY_BONUS = 1;

    /** Tests: one round of spawning now, whatever its clock says. */
    public void roundForTests(ServerLevel level) {
        spawning().round(level, this, brood(level));
    }

    /** A unit of this Shrine is back at it: off the roll (or out of its crew) and into the reserve. */
    public void absorb(Dawi unit) {
        boolean soldier = !unit.isWorker();
        if (works().forget(unit.getUUID())) {
            return;
        }
        boolean person = hasPerson(unit.getUUID());
        if (!person && !muster().defends(unit.getUUID())) {
            return;
        }
        if (person) {
            removePerson(unit.getUUID());
        }
        addToReserve(soldier ? 0 : 1, soldier ? 1 : 0);
    }
}
