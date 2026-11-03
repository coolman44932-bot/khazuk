package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Founding;
import claude.settlementcore.settlement.Walls;
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
 * The Keep, the hold's founding hall (the engine's Founding; tools/gen_keep.py): its template is whole, its Shrine's spot open on the dais
 * and its gate of fence gates; founded on open ground it is raised by the god, and the Shrine set on its dais founds the hold owning it,
 * its yard walled; raised round a Shrine that already stands, that hold has it. In the field (72 x 60 x 72, dirt at y 2, air above).
 */
@GameTestHolder(Khazuk.MODID)
public class KeepGameTests {

    private static final BlockPos DOOR_GROUND = new BlockPos(4, 2, 36);

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theKeepsTemplateIsWhole(GameTestHelper h) {
        Rooms.Room keep = HoldRooms.room("dawi_keep");
        h.assertTrue(Founding.isFoundingHall(keep), "the Keep isn't a founding hall");
        List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), keep.id());
        h.assertTrue(cells.size() == keep.sizeX() * keep.sizeY() * keep.sizeZ(), "the Keep's template has " + cells.size() + " cells");
        int gates = 0;
        boolean spotOpen = false, dais = false;
        for (RoomTemplates.Entry e : cells) {
            gates += e.state().is(Blocks.DARK_OAK_FENCE_GATE) ? 1 : 0;
            spotOpen |= e.local().equals(new BlockPos(14, 1, 12)) && e.state().isAir();
            dais |= e.local().equals(new BlockPos(14, 0, 12)) && e.state().is(Blocks.CHISELED_DEEPSLATE);
        }
        h.assertTrue(gates == 3 && spotOpen && dais, "the Keep has " + gates + " gate leaves, its spot open " + spotOpen + ", its dais " + dais);
        h.succeed();
    }

    /** Founded on open ground: raised by the god, its Shrine set on the dais founds the hold owning it, and the yard is walled. */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aKeepFoundedIsRaisedAndItsShrineFoundsTheHold(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Hirn", 0, false));
        Rooms.Room keep = HoldRooms.room("dawi_keep");
        Founding f = Founding.of(h.getLevel());
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(h.getLevel(), god, keep, h.absolutePos(DOOR_GROUND), Direction.EAST, c -> told.add(c.getString())),
                "the Keep wasn't founded: " + told);
        PlanPlacement pl = AncestorPlan.placement(keep, h.absolutePos(DOOR_GROUND), Direction.EAST);
        for (int s = 0; s < 200 && f.rising(pl.door()); s++) {
            f.second(h.getLevel(), Founding.PER_SECOND);
        }
        h.assertTrue(!f.rising(pl.door()), "the Keep never finished rising");
        BlockPos spot = Founding.heartSpot(pl);
        h.assertTrue(h.getLevel().getBlockState(spot).isAir() && h.getLevel().getBlockState(spot.below()).is(Blocks.CHISELED_DEEPSLATE),
                "the Keep's dais isn't there with its spot open");
        h.getLevel().setBlockAndUpdate(spot, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(h.getLevel(), spot, KRegistry.SHRINE.get().defaultBlockState(), god, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(spot);
        h.assertTrue(hold.has(HoldRooms.KEEP) && hold.settlementName().equals("Karak Hirn"), "the hold doesn't own its Keep: " + hold.settlementName());
        h.assertTrue(!f.spot(spot, god.getUUID()), "the Keep's spot is still open after its Shrine took it");
        Walls.Scan walls = Walls.walk(h.getLevel(), spot);
        h.assertTrue(walls.walled() && walls.columns().size() >= Walls.MIN_COLUMNS && walls.gates().size() == 3,
                "the Keep's yard isn't walled: walled " + walls.walled() + ", " + walls.columns().size() + " columns, " + walls.gates().size()
                        + " gates, out at " + walls.leak());
        h.succeed();
    }

    /** The Keep's plan used beside a Shrine set down before keeps: the Keep rises round it, the Shrine standing, and that hold has it. */
    @GameTest(template = "field", timeoutTicks = 400, batch = "khazuk")
    public static void aKeepIsRaisedRoundAShrineThatStands(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Norn", 1, true));
        BlockPos shrine = new BlockPos(36, 3, 36);
        h.setBlock(shrine, KRegistry.SHRINE.get().defaultBlockState());
        ShrineBlockEntity hold = (ShrineBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(shrine));
        hold.claim(god, "Karak Norn");
        h.runAfterDelay(2, () -> raiseRound(h, god, shrine, hold));   // a Shrine set down this tick registers itself on its first load
    }

    private static void raiseRound(GameTestHelper h, net.minecraft.server.level.ServerPlayer god, BlockPos shrine, ShrineBlockEntity hold) {
        Rooms.Room keep = HoldRooms.room("dawi_keep");
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(h.getLevel(), god, keep, h.absolutePos(shrine.west(6).below()), Direction.EAST, c -> told.add(c.getString())),
                "the Keep wasn't raised round the Shrine: " + told);
        Founding f = Founding.of(h.getLevel());
        PlanPlacement pl = Founding.roundHeart(keep, h.absolutePos(shrine), Direction.EAST);
        for (int s = 0; s < 200 && f.rising(pl.door()); s++) {
            f.second(h.getLevel(), Founding.PER_SECOND);
        }
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(shrine)).is(KRegistry.SHRINE.get()), "the Shrine didn't stand through the Keep's rising");
        h.assertTrue(hold.has(HoldRooms.KEEP), "the hold round which the Keep rose doesn't have it");
        h.succeed();
    }
}
