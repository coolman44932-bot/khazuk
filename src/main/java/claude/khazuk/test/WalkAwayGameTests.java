package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.PlayerLoadedChunks;
import claude.settlementcore.test.SurvivalGod;
import claude.settlementcore.work.DigWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Walking away from a dig (the user plays that way: he orders a hall, goes off to mine or farm, and comes back). In a real world only the
 * chunks round a player tick; in a GameTest the runner forces the whole plot. So: the god orders the Barracks dug (his plan on the
 * corridor's wall, paid out of the gold he handed the Shrine), and once its Miners are into it he goes 300 blocks off and the plot's
 * chunks are let go of (PlayerLoadedChunks, with a real server's simulation distance of 10): the hold and its Miners unload, the dig stands
 * still. Then he walks back, his own chunk tickets load it again, and the dig must carry on and finish: every cell of the Barracks' template
 * as the template has it (none lost, none left as rock), the dig's every task done once (none doubled), the hold's Dawi all there, no
 * Miner stuck in the stone.
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazukaway", time = 6000, daylightCycle = BatchWorld.Rule.OFF, weather = BatchWorld.Weather.CLEAR,
        difficulty = Difficulty.NORMAL, simulationDistance = 10)
public class WalkAwayGameTests {

    static final String TAG = "away";
    private static final BlockPos SHRINE = new BlockPos(2, 3, 20), DOOR = new BlockPos(4, 3, 31), STAND = new BlockPos(2, 3, 31);
    private static final BlockPos FAR = new BlockPos(320, 25, 0);
    /** The dig this far along before he goes; then ticks away, and the budget for the rest once he's back. */
    public static final float GO_AT = 0.25f;
    public static final int GO_BUDGET = 8000, AWAY = 1200, SETTLE = 200, BACK_BUDGET = 16000;

    @GameTest(template = "digsite", timeoutTicks = GO_BUDGET + AWAY + SETTLE + BACK_BUDGET + 400, batch = "khazukaway")
    public static void aDigLeftBehindStandsStillAndFinishesWhenTheGodComesBack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        level.getServer().tickRateManager().requestGameToSprint(GO_BUDGET + AWAY + SETTLE + BACK_BUDGET);   // a long run, not a slow one
        SurvivalPlay.fill(h, new BlockPos(1, 3, 1), new BlockPos(3, 5, 42), Blocks.AIR.defaultBlockState());   // the corridor down the west side
        for (BlockPos p : BlockPos.betweenClosed(FAR.offset(-3, -1, -3), FAR.offset(3, -1, 3))) {
            h.setBlock(p, Blocks.STONE);                         // somewhere to stand, far off
        }
        SurvivalGod god = SurvivalGod.join(h, SHRINE.offset(0, 0, 2));
        ShrineBlockEntity hold = SurvivalPlay.shrineFor(h, god, SHRINE, true);
        god.give(Items.BREAD, 64).give(Items.GOLD_INGOT, 20).give(KRegistry.PLAN_BARRACKS.get(), 1);
        SurvivalPlay.handAll(h, god, SHRINE, Items.BREAD);
        SurvivalPlay.handAll(h, god, SHRINE, Items.GOLD_INGOT);
        int gold = hold.currency();
        god.moveTo(STAND);
        god.hold(KRegistry.PLAN_BARRACKS.get()).useOn(DOOR, h.getAbsoluteDirection(Direction.WEST));
        BlockPos door = h.absolutePos(DOOR);
        DigWork dig = hold.works().digs().stream().filter(d -> d.job().door.equals(door)).findFirst().orElse(null);
        h.assertTrue(dig != null && gold - hold.currency() == HoldRooms.room("dawi_barracks").cost(), "the Barracks' plan began no paid dig: told "
                + SurvivalPlay.last(god, 3));
        PlanPlacement pl = PlanPlacement.compute(HoldRooms.room("dawi_barracks"), door, h.getAbsoluteDirection(Direction.EAST));
        SurvivalPlay.line(h, TAG, hold, "the Barracks ordered (" + dig.job().taskCount() + " cells)");
        god.moveTo(SHRINE.offset(0, 0, 2));

        int[] people = {0};
        int[] doneAtRelease = {0}, doneAway = {0};
        PlayerLoadedChunks.Released[] released = {null};
        long[] t = {0};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(dig.job().progress() >= GO_AT, "the dig is " + Math.round(dig.job().progress() * 100) + "% ("
                        + SurvivalPlay.digs(hold) + ", crew " + hold.works().crewCount() + ")"))
                .thenExecute(() -> {
                    people[0] = hold.peopleCount() + hold.reserve();
                    god.moveTo(FAR);
                    released[0] = PlayerLoadedChunks.release(h, god, -1);
                    h.assertTrue(released[0].isReleased(h.absolutePos(SHRINE)) && released[0].isReleased(door), "the hold's chunks weren't let go of: "
                            + released[0].released() + ", shared " + released[0].shared());
                    SurvivalPlay.line(h, TAG, hold, "he walks 320 blocks off with the dig at " + Math.round(dig.job().progress() * 100) + "%");
                })
                .thenIdle(SETTLE)                                // the chunks drop their tickets and unload
                .thenExecute(() -> {
                    h.assertTrue(!PlayerLoadedChunks.blockTicking(h, SHRINE) && !PlayerLoadedChunks.blockTicking(h, DOOR),
                            "the hold's chunks still tick with the god 320 blocks off (simulation distance " + PlayerLoadedChunks.simulationDistance(level) + ")");
                    doneAtRelease[0] = finished(h, door);
                })
                .thenIdle(AWAY)
                .thenExecute(() -> {
                    doneAway[0] = finished(h, door);
                    h.assertTrue(doneAway[0] == doneAtRelease[0], "the dig went on with nobody near: " + doneAtRelease[0] + " -> " + doneAway[0] + " cells");
                    god.moveTo(SHRINE.offset(0, 0, 2));          // back
                    t[0] = h.getTick();
                })
                .thenWaitUntil(() -> {
                    ShrineBlockEntity now = shrine(h);
                    h.assertTrue(now != null && now.has(HoldRooms.BARRACKS), "the Barracks isn't dug " + (h.getTick() - t[0]) + " ticks after he came back: "
                            + (now == null ? "no Shrine" : SurvivalPlay.digs(now) + ", crew " + now.works().crewCount()));
                })
                .thenExecute(() -> {
                    ShrineBlockEntity now = shrine(h);
                    List<String> lost = new ArrayList<>();
                    int cells = 0;
                    for (RoomTemplates.Entry e : RoomTemplates.get(level, "dawi_barracks")) {
                        BlockPos p = pl.world(e.local());
                        BlockState want = e.state().rotate(pl.rotation());
                        Block is = level.getBlockState(p).getBlock();
                        cells++;
                        if (is != want.getBlock() && !(want.isAir() && level.getBlockState(p).getCollisionShape(level, p).isEmpty())) {
                            if (lost.size() < 12) {
                                lost.add(h.relativePos(p).toShortString() + " is " + is + ", not " + want.getBlock());
                            } else if (lost.size() == 12) {
                                lost.add("...");
                            }
                        }
                    }
                    h.assertTrue(lost.isEmpty(), "the Barracks isn't as its template has it after the walk away (" + cells + " cells): " + lost);
                    h.assertTrue(now.works().digs().stream().noneMatch(d -> d.job().door.equals(door)), "the Barracks' dig is still open after it was handed over");
                    String leak = SurvivalPlay.leak(level, pl, List.of());
                    h.assertTrue(leak == null, "the Barracks isn't closed by its own walls: " + leak);
                    int nowPeople = now.peopleCount() + now.reserve();
                    h.assertTrue(nowPeople >= people[0], "the hold had " + people[0] + " Dawi as he left, " + nowPeople + " after");
                    for (Miner m : level.getEntitiesOfClass(Miner.class, new AABB(h.absolutePos(SHRINE)).inflate(40))) {
                        h.assertTrue(!m.isInWall(), "a Miner is stuck in the stone at " + h.relativeVec(m.position()) + ": " + m.runningGoals());
                    }
                    SurvivalPlay.line(h, TAG, now, "the Barracks dug out " + (h.getTick() - t[0]) + " ticks after he came back; " + cells + " cells as the template has them");
                    level.getServer().tickRateManager().stopSprinting();
                })
                .thenSucceed();
    }

    @claude.settlementcore.test.AfterBatch(batch = "khazukaway")
    public static void runEnds(ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
    }

    /** The cells of the dig at {@code door} done (read off the Shrine as it stands now: a chunk loaded again holds a new block entity). */
    private static int finished(GameTestHelper h, BlockPos door) {
        ShrineBlockEntity s = shrine(h);
        if (s == null) {
            return -1;
        }
        return s.works().digs().stream().filter(d -> d.job().door.equals(door)).mapToInt(d -> d.job().finishedCount()).findFirst().orElse(-2);
    }

    private static ShrineBlockEntity shrine(GameTestHelper h) {
        return h.getLevel().getBlockEntity(h.absolutePos(SHRINE)) instanceof ShrineBlockEntity s ? s : null;
    }
}
