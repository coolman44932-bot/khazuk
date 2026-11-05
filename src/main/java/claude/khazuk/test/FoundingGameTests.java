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
}
