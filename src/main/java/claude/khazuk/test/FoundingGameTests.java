package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
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
            rune |= l.getX() == HEART.getX() + 2 && e.state().is(Blocks.GOLD_BLOCK);
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
