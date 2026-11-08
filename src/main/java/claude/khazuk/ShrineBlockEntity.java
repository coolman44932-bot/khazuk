package claude.khazuk;

import claude.khazuk.hold.HoldCrag;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.settlement.Brood;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementBlockEntity;
import claude.settlementcore.settlement.Spawning;
import claude.settlementcore.unit.SettlerMob;
import com.geckolib.animatable.GeoBlockEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
 * <p>
 * It is drawn as the statue of the three Ancestors (the detail session's GeckoLib model, handoffs/ancestor_shrine_2026-11-04_done.md;
 * client/ShrineRenderer), two controllers in this order, the later winning on a bone both key: "main" loops {@code idle}; "rite" plays,
 * when triggered, {@code pray} as the hold prays (god/Devotion) and an Ancestor's answer as his power is paid from this hold's devotion
 * (god/AncestorPowers). Its band of ten runes reads the devotion, one lit for every {@value #DEVOTION_PER_RUNE}. That count is all the
 * client is sent of it, and only when it changes.
 */
public class ShrineBlockEntity extends SettlementBlockEntity implements GeoBlockEntity {

    /** The statue's clips: GeckoLib 5.5.2 throws on a clip missing from the file, so these are the file's exact names (KhazukAssetGameTests). */
    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ancestor_shrine.idle");
    public static final RawAnimation PRAY = RawAnimation.begin().thenPlay("animation.ancestor_shrine.pray");
    public static final RawAnimation ANSWER_GRUNGNI = RawAnimation.begin().thenPlay("animation.ancestor_shrine.answer_grungni");
    public static final RawAnimation ANSWER_VALAYA = RawAnimation.begin().thenPlay("animation.ancestor_shrine.answer_valaya");
    public static final RawAnimation ANSWER_GRIMNIR = RawAnimation.begin().thenPlay("animation.ancestor_shrine.answer_grimnir");
    /** The triggered controller, and its prayer's trigger (each trigger is named as its clip). */
    public static final String RITE = "rite", PRAY_TRIGGER = "pray";
    /** The rite's triggers and their clips, in order: what registerControllers registers, and the tests read. */
    public static final java.util.Map<String, RawAnimation> RITES;

    static {
        java.util.LinkedHashMap<String, RawAnimation> rites = new java.util.LinkedHashMap<>();
        rites.put(PRAY_TRIGGER, PRAY);
        rites.put("answer_grungni", ANSWER_GRUNGNI);
        rites.put("answer_valaya", ANSWER_VALAYA);
        rites.put("answer_grimnir", ANSWER_GRIMNIR);
        RITES = java.util.Collections.unmodifiableMap(rites);
    }
    /** The devotion one rune of the band stands for, and the band's runes (bones devotion_1 to devotion_10): a full hold lights them all. */
    public static final int DEVOTION_PER_RUNE = 20, RUNES = 10;
    private static final String LIT_KEY = "lit_runes";

    static final Keys KEYS = new Keys("hold_name", "gold", "people", "reserve_miners", "reserve_warriors");
    public static final int ROUND_SECONDS = 30;
    public static final int UP_AT_ONCE = 8;
    /**
     * 0.12.2 (the user, 2026-11-04: "raise the cap"): the hold's halls make room for more Dawi up at once: the Keep {@value #KEEP_ROOM}, the
     * Great Hall {@value #GREAT_HALL_ROOM}, the Barracks {@value #BARRACKS_ROOM}, the Mine and the Brewery {@value #SMALL_ROOM} each; and a
     * round of spawning brings up to {@value #RISE_PER_ROUND} more out of the deeps while there's room (two Miners to a Warrior) and no hall is
     * being dug or raised (those crews come up out of the deeps at their work).
     */
    public static final int KEEP_ROOM = 2, GREAT_HALL_ROOM = 6, BARRACKS_ROOM = 4, SMALL_ROOM = 2, RISE_PER_ROUND = 3;
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
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The client's: the band's runes lit, as the server last sent. */
    private int litRunes;
    /** The server's: the lit runes the clients were last sent (-1 before the first). */
    private int sentLit = -1;

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
        if (level instanceof ServerLevel sl && !lifting) {
            runes.dropKit(sl, this);                             // lifted by its god, the kit goes with the hold
        }
        super.preRemoveSideEffects(pos, state);
    }

    /** Its god is lifting it (ShrineBlock#playerWillDestroy): what it holds goes with it, nothing is dropped. */
    private boolean lifting;

    public void lifting() {
        lifting = true;
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
        if (unfaced && !buildings().isEmpty()) {                  // its buildings read first (the review of 5d72f74)
            unfaced = false;
            faceTheDoor(level);
            setChanged();                                        // saved with its "faced" mark: looked at once
        }
        if (crags.takeDirty() | brew.takeDirty() | grudges.takeDirty() | runes.takeDirty() | devotion.takeDirty()) {
            setChanged();                                        // the last step too, so a restart never raises the summit twice
        }
        syncRunes();
    }

    // ------------------------------------------------------------------ the statue

    /**
     * Loaded from a save without the "faced" mark (a Shrine saved before this): looked at once, on its first second. A Shrine saved before
     * 0.13.1 had no facing and loads facing NORTH, the default, whatever its hall (the detail session's filming, 2026-11-08: in an old Keep
     * with its door to the south the gods turned their backs on the door and the stairs, the lit gauge 4 blocks from the back wall).
     */
    private boolean unfaced;

    /** Tests: as if loaded from a save before the "faced" mark. */
    public void unfacedForTests() {
        unfaced = true;
    }

    /**
     * A Shrine still facing NORTH (the old default) in its hold's founding hall (the Keep, a Hold Gate's Shrine hall) is turned to face that
     * hall's door, as one set down there since 0.13.1 faces whoever came up to set it. One facing any other way was turned by its god: kept.
     * A god can't turn it himself without breaking the hold's heart-stone.
     */
    void faceTheDoor(ServerLevel level) {
        BlockState st = getBlockState();
        if (!st.hasProperty(ShrineBlock.FACING) || st.getValue(ShrineBlock.FACING) != net.minecraft.core.Direction.NORTH) {
            return;
        }
        for (claude.settlementcore.settlement.Building b : buildings()) {
            claude.settlementcore.room.Rooms.Room room = claude.settlementcore.room.Rooms.get(b.room());
            if (room == null || !claude.settlementcore.settlement.Founding.isFoundingHall(room) || !b.box().isInside(worldPosition)) {
                continue;
            }
            PlanPlacement pl = claude.settlementcore.settlement.BuildingBook.placementOf(b);
            if (pl == null) {
                return;
            }
            net.minecraft.core.Direction d = toward(worldPosition, pl.door());
            if (d != net.minecraft.core.Direction.NORTH) {
                level.setBlock(worldPosition, st.setValue(ShrineBlock.FACING, d), Block.UPDATE_ALL);   // the same block: this block entity stays
            }
            return;
        }
    }

    /** The way from {@code from} to {@code to} along its longer run. */
    static net.minecraft.core.Direction toward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
        }
        return dz >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
    }

    /** How many of the band's runes {@code devotion} lights: one for every {@value #DEVOTION_PER_RUNE}, up to {@value #RUNES}. */
    public static int litFor(int devotion) {
        return Math.max(0, Math.min(RUNES, devotion / DEVOTION_PER_RUNE));
    }

    /** The band's runes lit: on the server from the devotion, on the client as last sent. */
    public int litRunes() {
        return level != null && level.isClientSide() ? litRunes : litFor(devotion.devotion());
    }

    /** The clients are sent the lit runes when the count changes (a block update of a few bytes, a few times an hour at most). */
    public void syncRunes() {
        int now = litFor(devotion.devotion());
        if (now != sentLit && level instanceof ServerLevel sl) {
            sentLit = now;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The hold prayed (god/Devotion): the band's lit runes swell, the gods' eyes kindle. */
    public void prayed() {
        if (level instanceof ServerLevel) {
            triggerAnim(RITE, PRAY_TRIGGER);
        }
    }

    /** {@code p} was paid out of this hold's devotion (god/AncestorPowers): its Ancestor answers on the statue. */
    public void answered(claude.khazuk.god.AncestorPowers.Power p) {
        if (level instanceof ServerLevel) {
            triggerAnim(RITE, answerTrigger(p));
            syncRunes();                                         // the price may have put out a rune or two
        }
    }

    /** The trigger (and clip) of the Ancestor who grants {@code p}. There is no plain "answer". */
    public static String answerTrigger(claude.khazuk.god.AncestorPowers.Power p) {
        return switch (p) {
            case HAMMER -> "answer_grungni";
            case WARD -> "answer_valaya";
            case FURY -> "answer_grimnir";
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ShrineBlockEntity>("main", 0, test -> test.setAndContinue(IDLE)));
        AnimationController<ShrineBlockEntity> rite = new AnimationController<>(RITE, 2, test -> PlayState.STOP);
        RITES.forEach(rite::triggerableAnim);
        controllers.add(rite);                                   // after "main": its clips win on the bones they key
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** What a client is sent of the Shrine, on the chunk's load and on a block update: only the lit runes (the rest is the server's). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        int now = litFor(devotion.devotion());
        tag.putInt(LIT_KEY, now);
        return tag;                                              // no state written: a chunk sent to one player mustn't stop the update the
                                                                 // others tracking it are owed (the review of 90d3931)
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The client reads only the lit runes, never the engine's whole load (the update doesn't carry it). */
    @Override
    public void onDataPacket(Connection net, ValueInput in) {
        litRunes = in.getIntOr(LIT_KEY, 0);
    }

    @Override
    public void handleUpdateTag(ValueInput in) {
        litRunes = in.getIntOr(LIT_KEY, 0);
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
        ranks.save(tag);
        tag.putBoolean("faced", true);                           // its facing looked at (faceTheDoor), or set by whoever placed it
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
        ranks.load(tag);
        unfaced = !tag.getBooleanOr("faced", false);
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
            // while a hall is dug or raised the hold keeps to UP_AT_ONCE up, as before the halls made room: a dig's or a site's crew comes up
            // out of the deeps right at its work, and Dawi brought up first would walk there instead (the laptop's runs of 4120042 and
            // 33e8376: the Great Hall 95 s, then about 30 s, slower)
            boolean building = !works().digs().isEmpty() || !works().sites().isEmpty();
            for (int i = 0; i < workers + guards; i++) {
                if (!building || peopleCount() < UP_AT_ONCE) {
                    brood.bringUp(i >= workers, getBlockPos().above());
                }
            }
            // the deeps' waiting Dawi, while the halls have room and nothing is being dug or raised
            for (int i = 0; !building && i < RISE_PER_ROUND && peopleCount() < peopleCap(); i++) {
                boolean soldier = i % 3 == 2;
                if (brood.bringUp(soldier, getBlockPos().above()) == null && brood.bringUp(!soldier, getBlockPos().above()) == null) {
                    break;
                }
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
        return UP_AT_ONCE + (has(claude.khazuk.hold.HoldRooms.KEEP) ? KEEP_ROOM : 0) + (has(claude.khazuk.hold.HoldRooms.GREAT_HALL) ? GREAT_HALL_ROOM : 0)
                + (has(claude.khazuk.hold.HoldRooms.BARRACKS) ? BARRACKS_ROOM : 0) + (has(claude.settlementcore.room.Rooms.Function.OREMINE) ? SMALL_ROOM : 0)
                + (has(claude.khazuk.hold.HoldRooms.BREWERY) ? SMALL_ROOM : 0) + (has(claude.khazuk.hold.HoldRooms.SLAYER_SHRINE) ? SMALL_ROOM : 0)
                + (has(claude.khazuk.hold.HoldRooms.ENGINEERS_GUILD) ? SMALL_ROOM : 0);
    }

    @Override
    protected String currencyRoad() {
        return "The hold's gold";
    }

    /**
     * The Miners fill the hollows under what they raise (a cave, a pocket of water or lava under its ground) instead of the site being
     * refused (the user, 2026-11-08: "wherever they are gonna build at so i dont get the message for hollow ground"): Settlement Core's
     * fillHollows, laid in the plinth phase with the land's own stone.
     */
    @Override
    public boolean fillsHollows() {
        return true;
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
