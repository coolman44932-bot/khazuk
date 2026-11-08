package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.CarvedGates;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Founding;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * A hold founded behind its gate (0.13.0; the user, 2026-11-04: the hold goes behind its gate, not before it): the Hold Gate is the hold's
 * founding hall (the engine's Founding; tools/gen_hold_gate.py --template --write). Its template is whole, the Shrine's spot open on the
 * dais in the hall at its passage's end; founded on open ground it is raised by the god, the Shrine set on the dais founds the hold owning
 * the gate, and the mountain rises round it. The old surface Keep is still known for the holds founded on one, and a Keep's plan digs the
 * King's Throne Room now. In the field (72 x 60 x 72, dirt at y 2, air above).
 */
@GameTestHolder(Khazuk.MODID)
public class FoundingGameTests {

    private static final BlockPos DOOR_GROUND = new BlockPos(4, 2, 36);
    private static final BlockPos HEART = new BlockPos(28, 4, 15);

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theHoldGateIsTheFoundingHallWithItsDaisOpen(GameTestHelper h) {
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        h.assertTrue(Founding.isFoundingHall(gate), "the Hold Gate isn't the founding hall");
        List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), gate.id());
        h.assertTrue(cells.size() == gate.sizeX() * gate.sizeY() * gate.sizeZ(), "the Hold Gate's template has " + cells.size() + " cells");
        boolean spotOpen = false, dais = false, headroom = true, rune = false;
        for (RoomTemplates.Entry e : cells) {
            BlockPos l = e.local();
            spotOpen |= l.equals(HEART) && e.state().isAir();
            dais |= l.equals(HEART.below()) && e.state().is(Blocks.POLISHED_DEEPSLATE);
            if (l.getX() == HEART.getX() && l.getZ() == HEART.getZ() && l.getY() > HEART.getY() && l.getY() <= HEART.getY() + 4) {
                headroom &= e.state().isAir();                  // room over the Shrine for its statue
            }
            rune |= l.getX() == HEART.getX() + 3 && e.state().is(KRegistry.GILDED_STONE.get());   // in the back wall, flush
        }
        h.assertTrue(spotOpen && dais && headroom && rune, "the Shrine's hall: spot open " + spotOpen + ", on the dais " + dais
                + ", room over it " + headroom + ", Grungni's rune behind it " + rune);
        Rooms.Room keep = HoldRooms.room("dawi_keep");
        h.assertTrue(Founding.isFoundingHall(keep), "the old Keep isn't known as a founding hall any more (holds founded on one)");
        h.assertTrue(KRegistry.PLAN_KEEP.get().room() != null && !Rooms.isRaised(KRegistry.PLAN_KEEP.get().room().id()),
                "the Keep's plan doesn't dig the King's Throne Room");
        h.assertTrue(KRegistry.PLAN_KEEP.get().room().function() == HoldRooms.KEEP, "the Throne Room isn't the hold's keep (its room for 2 more)");
        h.assertTrue(KRegistry.PLAN_GREAT_HALL.get().room().id().equals("dawi_grand_hall") && HoldRooms.room("dawi_great_hall").sizeX() == 27,
                "the Great Hall's plan doesn't dig the grand hall, or the first Great Hall isn't kept for the halls dug before");
        h.succeed();
    }

    /** Founded on open ground: raised by the god, its Shrine set on the dais founds the hold owning the gate, and the mountain rises round it. */
    @GameTest(template = "field", timeoutTicks = 600, batch = "khazuk")
    public static void aHoldGateFoundedIsRaisedAndItsShrineFoundsTheHold(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Hirn", 0, false));
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        Founding f = Founding.of(h.getLevel());
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(h.getLevel(), god, gate, h.absolutePos(DOOR_GROUND), Direction.EAST, c -> told.add(c.getString())),
                "the Hold Gate wasn't founded: " + told);
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(DOOR_GROUND), Direction.EAST);
        for (int s = 0; s < 400 && f.rising(pl.door()); s++) {
            f.second(h.getLevel(), Founding.PER_SECOND);
        }
        h.assertTrue(!f.rising(pl.door()), "the Hold Gate never finished rising");
        BlockPos spot = Founding.heartSpot(pl);
        h.assertTrue(h.getLevel().getBlockState(spot).isAir() && h.getLevel().getBlockState(spot.below()).is(Blocks.POLISHED_DEEPSLATE),
                "the Shrine's dais isn't there with its spot open");
        h.assertTrue(f.spot(spot, god.getUUID()), "the risen gate's spot doesn't wait for its Shrine");
        h.getLevel().setBlockAndUpdate(spot, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), spot, KRegistry.SHRINE.get().defaultBlockState(), god, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(spot);
        h.assertTrue(hold.has(HoldRooms.GATE) && hold.settlementName().equals("Karak Hirn"), "the hold doesn't own its gate: " + hold.settlementName());
        h.assertTrue(!f.spot(spot, god.getUUID()), "the gate's spot is still open after its Shrine took it");
        h.assertTrue(hold.inSettlement(pl.door()), "the gate's door is outside the hold founded behind it");
        hold.crags().second(h.getLevel(), hold, HoldCrag.PER_SECOND);
        h.assertTrue(hold.crags().raised(pl.door()) > 0, "the mountain doesn't rise round the founded gate");
        // a Shrine loaded from before 0.13.1 faces NORTH, the default: on its first second it turns to face its hall's door (west, down the
        // passage: the detail session's filming, 2026-11-08); one its god turned another way is kept; and its save marks it looked at
        var facing = claude.khazuk.ShrineBlock.FACING;
        h.getLevel().setBlock(spot, h.getLevel().getBlockState(spot).setValue(facing, Direction.NORTH), net.minecraft.world.level.block.Block.UPDATE_ALL);
        h.assertTrue(h.getLevel().getBlockEntity(spot) == hold, "turning the Shrine's block replaced its hold");
        hold.unfacedForTests();
        hold.second(h.getLevel());
        h.assertTrue(h.getLevel().getBlockState(spot).getValue(facing) == Direction.WEST && h.getLevel().getBlockEntity(spot) == hold,
                "the old Shrine faces " + h.getLevel().getBlockState(spot).getValue(facing) + " after its first second, not west to its hall's door");
        h.getLevel().setBlock(spot, h.getLevel().getBlockState(spot).setValue(facing, Direction.SOUTH), net.minecraft.world.level.block.Block.UPDATE_ALL);
        hold.unfacedForTests();
        hold.second(h.getLevel());
        h.assertTrue(h.getLevel().getBlockState(spot).getValue(facing) == Direction.SOUTH, "a Shrine its god turned south was turned again");
        net.minecraft.nbt.CompoundTag saved = hold.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getCompoundOrEmpty("khazuk").getBooleanOr("faced", false), "the Shrine's save doesn't mark its facing looked at");
        h.succeed();
    }

    /**
     * Grungni clears his gate's ground (the user, 2026-11-06: "shouldnt it break stuff out of the way, and fill hollow ground"): a cobblestone
     * pillar and a fence on the site are broken and drop, a cave under it is filled with stone, a pool standing over its floor is turned to
     * stone, and the gate is founded where the engine alone refused it.
     */
    @GameTest(template = "field", timeoutTicks = 40, batch = "khazuk")
    public static void grungniClearsHisGatesGround(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Hirn", 0, false));
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        var level = h.getLevel();
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(DOOR_GROUND), Direction.EAST);
        int floor = pl.door().getY() - 1;
        BlockPos mid = new BlockPos(pl.box().getCenter().getX(), floor, pl.box().getCenter().getZ());
        for (int y = 1; y <= 3; y++) {
            level.setBlockAndUpdate(mid.above(y), Blocks.COBBLESTONE.defaultBlockState());
        }
        level.setBlockAndUpdate(mid.east(2).above(), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos cave = mid.west(3).below();
        level.setBlockAndUpdate(cave, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(cave.below(), Blocks.AIR.defaultBlockState());
        BlockPos pool = mid.south(3).above();
        level.setBlockAndUpdate(pool, Blocks.WATER.defaultBlockState());
        h.assertTrue(claude.settlementcore.room.Siting.plan(level, pl, claude.settlementcore.room.RoomShoring.Warren.alone(), true)
                instanceof claude.settlementcore.room.Siting.Refusal, "the test needs a site the engine alone refuses");
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(level, god, gate, h.absolutePos(DOOR_GROUND), Direction.EAST, c -> told.add(c.getString())),
                "Grungni didn't clear his gate's ground: " + told);
        h.assertTrue(!level.getBlockState(mid.above(2)).is(Blocks.COBBLESTONE) && !level.getBlockState(mid.east(2).above()).is(Blocks.OAK_FENCE),
                "what stood on the site still stands");
        h.assertTrue(claude.settlementcore.room.Siting.ground(level.getBlockState(cave)) && claude.settlementcore.room.Siting.ground(level.getBlockState(cave.below())),
                "the cave under the site isn't filled: " + level.getBlockState(cave) + ", " + level.getBlockState(cave.below()));   // stone, or deepslate under y 0
        h.assertTrue(level.getFluidState(pool).isEmpty(), "the pool still stands on the site");
        h.assertTrue(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(mid).inflate(4),
                e -> e.getItem().is(Blocks.COBBLESTONE.asItem())).isEmpty(), "the broken pillar didn't drop");
        h.succeed();
    }

    /** A chest on the site is another's hoard: Grungni won't break it, so the gate is refused there and every block he changed is put back. */
    @GameTest(template = "field", timeoutTicks = 20, batch = "khazuk")
    public static void aHoardOnTheSiteIsLeftAndTheGroundPutBack(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Hirn", 0, false));
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        var level = h.getLevel();
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(DOOR_GROUND), Direction.EAST);
        int floor = pl.door().getY() - 1;
        BlockPos mid = new BlockPos(pl.box().getCenter().getX(), floor, pl.box().getCenter().getZ());
        level.setBlockAndUpdate(mid.above(), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(mid.east(3).above(), Blocks.COBBLESTONE.defaultBlockState());
        BlockPos cave = mid.west(3).below();
        level.setBlockAndUpdate(cave, Blocks.AIR.defaultBlockState());
        List<String> told = new ArrayList<>();
        h.assertTrue(!AncestorPlan.found(level, god, gate, h.absolutePos(DOOR_GROUND), Direction.EAST, c -> told.add(c.getString())),
                "the gate was founded over a chest");
        h.assertTrue(told.size() == 1 && told.get(0).startsWith("Something stands at " + mid.getX() + " " + (floor + 1) + " " + mid.getZ()),
                "the refusal doesn't point at the chest: " + told);
        h.assertTrue(level.getBlockState(mid.above()).is(Blocks.CHEST) && level.getBlockState(mid.east(3).above()).is(Blocks.COBBLESTONE)
                && level.getBlockState(cave).isAir(), "the ground wasn't put back as it was");
        h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(mid).inflate(6)).isEmpty(),
                "something dropped though the gate was refused");
        h.assertTrue(!Founding.of(level).rising(pl.door()), "a gate rose over the chest");
        h.succeed();
    }

    /**
     * Founded down in the rock (the user, 2026-11-06: "dwarves cant build underground?"): the gate's plan used by a god standing in a pocket
     * of solid rock is carved out of it by Grungni (Settlement Core's carved founding), its doorway left open onto the pocket; its Shrine
     * founds the hold as ever, and no mountain rises round it (the rock is its mountain: it would fill the pocket).
     */
    @GameTest(template = "field", timeoutTicks = 100, batch = "khazuk")
    public static void aHoldGateFoundedInTheRockIsCarvedOutOfIt(GameTestHelper h) {
        carvedIn(h, true);
    }

    /**
     * Carved from a shallow cave (the review of 5c1b27e): rock only a few blocks over the god, the 43-tall gate cut up through it into the
     * open air. Still carved and still no mountain round it (decided when the plan was used, CarvedGates), and a player's planks in its way
     * break and drop.
     */
    @GameTest(template = "field", timeoutTicks = 100, batch = "khazuk")
    public static void aHoldGateCarvedFromAShallowCaveRaisesNoMountain(GameTestHelper h) {
        carvedIn(h, false);
    }

    private static void carvedIn(GameTestHelper h, boolean deep) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Varn", 0, false));
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        var level = h.getLevel();
        Founding f = Founding.of(level);
        BlockPos ground = h.absolutePos(DOOR_GROUND);
        PlanPlacement pl = AncestorPlan.placement(gate, ground, Direction.EAST);
        net.minecraft.world.level.levelgen.structure.BoundingBox box = pl.box();
        int roof = deep ? Math.min(box.maxY() + 8, h.absolutePos(new BlockPos(0, 58, 0)).getY()) : ground.getY() + 12;
        net.minecraft.world.level.block.state.BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        int west = h.absolutePos(BlockPos.ZERO).getX();                  // the field's own west edge: nothing of a neighbour's touched
        for (int x = west; x <= box.maxX() + 2; x++) {                    // the mountain's rock, over the site and the god's pocket
            for (int z = box.minZ() - 2; z <= box.maxZ() + 2; z++) {
                for (int y = ground.getY() + 1; y <= roof; y++) {
                    level.setBlock(new BlockPos(x, y, z), rock, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockPos outside = pl.door().relative(Direction.WEST);            // the doorway's outside, in the god's pocket
        for (int x = Math.max(west + 1, outside.getX() - 3); x <= outside.getX(); x++) {
            for (int z = outside.getZ() - 1; z <= outside.getZ() + 1; z++) {
                for (int y = outside.getY(); y <= outside.getY() + 2; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            }
        }
        god.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(outside.relative(Direction.WEST, 2)));
        BlockPos planks = pl.world(new BlockPos(10, 3, 15));               // a player's planks in the gate's way
        level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        h.assertTrue(AncestorPlan.underRock(level, god.blockPosition()), "the test needs the god down in the rock");
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(level, god, gate, ground, Direction.EAST, c -> told.add(c.getString())),
                "the gate wasn't carved out of the rock: " + told);
        h.assertTrue(told.get(0).startsWith("Grungni carves") && f.carving(pl.door()), "the gate isn't being carved: " + told);
        h.assertTrue(!level.getBlockState(planks).is(Blocks.OAK_PLANKS) && !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(planks).inflate(2), e -> e.getItem().is(Blocks.OAK_PLANKS.asItem())).isEmpty(),
                "the planks in the gate's way didn't break and drop");
        for (int s = 0; s < 600 && f.rising(pl.door()); s++) {
            f.second(level, Founding.PER_SECOND);
        }
        h.assertTrue(!f.rising(pl.door()), "the gate was never carved through");
        BlockPos spot = Founding.heartSpot(pl);
        h.assertTrue(level.getBlockState(spot).isAir() && level.getBlockState(spot.below()).is(Blocks.POLISHED_DEEPSLATE),
                "the carved gate's dais isn't there with its spot open: " + level.getBlockState(spot) + " on " + level.getBlockState(spot.below()));
        h.assertTrue(level.getBlockState(pl.door()).isAir() && level.getBlockState(outside).isAir(), "the carved gate's doorway isn't open onto the pocket");
        h.assertTrue(!deep || level.getBlockState(new BlockPos(pl.door().getX(), box.maxY() + 2, pl.door().getZ() + 3)).is(Blocks.DEEPSLATE),
                "the rock over the gate was cut");
        h.assertTrue(f.spot(spot, god.getUUID()), "the carved gate's spot doesn't wait for its Shrine");
        level.setBlockAndUpdate(spot, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(level, spot, KRegistry.SHRINE.get().defaultBlockState(), god, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        ShrineBlockEntity hold = (ShrineBlockEntity) level.getBlockEntity(spot);
        h.assertTrue(hold.has(HoldRooms.GATE) && hold.settlementName().equals("Karak Varn"), "the hold doesn't own its carved gate");
        h.assertTrue(HoldCrag.carved(level, pl), "the carved gate doesn't read as down in the rock");
        BlockPos wall = pl.world(new BlockPos(16, 8, 4));             // round the gate's walls: open in the template, the mountain's on the surface
        h.assertTrue(level.getBlockState(wall).isAir(), "the test needs a cell round the gate's walls the carve left open: " + level.getBlockState(wall));
        for (int s = 0; s < 40 && !hold.crags().risen(level, pl.door()); s++) {
            hold.crags().second(level, hold, HoldCrag.PER_SECOND);
        }
        h.assertTrue(hold.crags().risen(level, pl.door()) && level.getBlockState(outside.relative(Direction.WEST)).isAir(),
                "a mountain rose out round a gate carved in the rock (the pocket: " + level.getBlockState(outside.relative(Direction.WEST)) + ")");
        h.assertTrue(!level.getBlockState(wall).isAir(), "the holes round a carved gate's walls weren't filled (the user's game: \"theres holes here\")");
        CarvedGates.of(level).remove(pl.door());
        h.succeed();
    }

    /**
     * A god whose first hold stands far off (loaded, so it is the nearest Shrine of theirs) uses the Hold Gate's plan out on open ground,
     * through the plan's own use: a second hold is founded there, not the gate set out for the far hold's Miners (the review of 31991f2).
     */
    @GameTest(template = "field", timeoutTicks = 60, batch = "khazuk")
    public static void aGodWithAHoldFarOffFoundsASecondThroughTheGate(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Hirn", 1, true));
        BlockPos first = new BlockPos(68, 3, 68);
        h.setBlock(first, KRegistry.SHRINE.get().defaultBlockState());
        ShrineBlockEntity far = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(first));
        far.claim(god, "Karak Hirn");
        h.runAfterDelay(2, () -> {                               // a Shrine set down this tick registers itself on its first load
            BlockPos ground = h.absolutePos(DOOR_GROUND);
            h.assertTrue(ShrineBlockEntity.ownedAt(god, ground) == far && !far.inSettlement(ground),
                    "the test needs the far hold as the god's nearest, the ground outside its land");
            god.setYRot(-90f);                                   // facing east: the gate runs east, its door toward the west
            net.minecraft.world.item.ItemStack plan = new net.minecraft.world.item.ItemStack(KRegistry.PLAN_HOLD_GATE.get());
            god.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, plan);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
            var result = KRegistry.PLAN_HOLD_GATE.get().useOn(new net.minecraft.world.item.context.UseOnContext(god, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
            PlanPlacement pl = AncestorPlan.placement(HoldRooms.room("dawi_hold_gate"), ground, Direction.EAST);
            h.assertTrue(result.consumesAction() && Founding.of(h.getLevel()).rising(pl.door()) && far.works().sites().isEmpty(),
                    "the gate's plan out on open ground didn't found a second hold: " + result + ", rising " + Founding.of(h.getLevel()).rising(pl.door())
                            + ", far hold's sites " + far.works().sites().size());
            h.succeed();
        });
    }

    /**
     * The old way still holds for a save from before 0.13.0: a surface Keep risen and waiting for its Shrine (a founding hall of room
     * dawi_keep) takes the Shrine set on its dais, and the hold is born owning the Keep, its room counted.
     */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aKeepFromAnOldSaveStillFoundsItsHold(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Kadrin", 0, false));
        Rooms.Room keep = HoldRooms.room("dawi_keep");
        Founding f = Founding.of(h.getLevel());
        h.assertTrue(f.begin(h.getLevel(), AncestorPlan.placement(keep, h.absolutePos(DOOR_GROUND), Direction.EAST), god.getUUID()) == null,
                "an old save's Keep couldn't be founded");
        PlanPlacement pl = AncestorPlan.placement(keep, h.absolutePos(DOOR_GROUND), Direction.EAST);
        for (int s = 0; s < 200 && f.rising(pl.door()); s++) {
            f.second(h.getLevel(), Founding.PER_SECOND);
        }
        BlockPos spot = Founding.heartSpot(pl);
        h.assertTrue(f.spot(spot, god.getUUID()), "the old Keep's dais doesn't wait for its Shrine");
        h.getLevel().setBlockAndUpdate(spot, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), spot, KRegistry.SHRINE.get().defaultBlockState(), god, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(spot);
        h.assertTrue(hold.has(HoldRooms.KEEP), "the hold founded on an old Keep doesn't own it");
        h.assertTrue(hold.peopleCap() == ShrineBlockEntity.UP_AT_ONCE + ShrineBlockEntity.KEEP_ROOM, "the old Keep's room isn't counted: cap "
                + hold.peopleCap());
        h.succeed();
    }
}
