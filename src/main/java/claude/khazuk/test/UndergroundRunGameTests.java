package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.god.Waking;
import claude.khazuk.hold.AncestorPlan;
import claude.khazuk.hold.HallSeal;
import claude.khazuk.hold.HoldCrag;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Founding;
import claude.settlementcore.test.AfterBatch;
import claude.settlementcore.test.BeforeBatch;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hold played the way the user played it on 2026-11-09, down in the rock (the user: "i should have done a playthrough earlier", then "so that
 * means i can be lazy again? sure"). Every step that broke for him is a step here, so a ship can't break it again unseen:
 * <ul>
 * <li>the field filled with deep rock; two caves 30 to 40 blocks off, sealed in it, a persistent zombie in each (his: a zombie 38 off in a cave,
 * a creeper 47 off: the alarm never stood down, every Miner a defender, nobody at the dig);</li>
 * <li>the god, woken with the kit, founds the Hold Gate from a pocket in the rock: carved out of it, its great Shrine hall (his: "the hold gate is
 * too small no space to do anything"); the Shrine set on the dais; the kit's gold and bread handed to it, and fed as a player with a farm
 * would (32 bread every two minutes);</li>
 * <li>the Mine ordered through the great hall's back sealed door on the east, the Great Hall through the one on the west: the seals open and
 * a Miner of the crew walks in past each wall (his: ten Dawi bunched at a whole seal, "Are you sure they can get past a new door");</li>
 * <li>both halls dug out, the gate marked wide (no widening dug for a gate built great), and the alarm never up a minute on end while
 * the caves' zombies live.</li>
 * </ul>
 * Each step's tick, with the hold's food, gold and people, is logged as "KHAZUK underground:" lines. The batch runs on Normal (the caves' zombies
 * are real foes) and sprints.
 */
@GameTestHolder(Khazuk.MODID)
public class UndergroundRunGameTests {

    /** The gate's door ground: it runs east from here, its box z 29..59, so the Great Hall runs north to z 1 and the Mine south to z 69. */
    private static final BlockPos DOOR_GROUND = new BlockPos(4, 2, 44);
    /** The caves, each 8 x 3 x 8, 30 to 35 from the Shrine, clear of the gate, the Great Hall, the Mine and its drifts, in the rock. */
    private static final BlockPos CAVE_A = new BlockPos(58, 10, 30), CAVE_B = new BlockPos(5, 8, 15);
    public static final int FEED_EVERY = 2400, FEED_BREAD = 32;
    /** Budgets, in ticks: the gate carved, the seal opened, a Miner in past the wall, both halls dug, the alarm up on end at most. */
    public static final int CARVE_SECONDS = 600, SEAL_BUDGET = 1200, IN_BUDGET = 4000, HALLS_BUDGET = 40000, ALARM_MAX = 1200;

    private static Difficulty was = Difficulty.PEACEFUL;

    @BeforeBatch(batch = "khazukrun")
    public static void runBegins(ServerLevel level) {
        was = level.getServer().getWorldData().getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @AfterBatch(batch = "khazukrun")
    public static void runEnds(ServerLevel level) {
        level.getServer().tickRateManager().stopSprinting();
        level.getServer().setDifficulty(was, true);
    }

    private static void line(GameTestHelper h, ShrineBlockEntity hold, String step) {
        Khazuk.LOG.info("KHAZUK underground: {} at tick {} ({} s): food {}, gold {}, people {} up and {}+{} in the deeps, alarm {}", step, h.getTick(),
                h.getTick() / 20, hold.food(), hold.currency(), hold.peopleCount(), hold.reserveWorkers(), hold.reserveSoldiers(), hold.muster().alarmed());
    }

    private static void hand(GameTestHelper h, ServerPlayer god, BlockPos at, ItemStack stack) {
        h.getLevel().getBlockState(at).useItemOn(stack, h.getLevel(), god, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    @GameTest(template = "field", timeoutTicks = 48000, batch = "khazukrun")
    public static void aHoldCarvedInTheRockIsPlayedFromItsFoundingToItsHalls(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // the rock: the whole field deep rock from its ground up, 56 high
        BlockState rock = Blocks.DEEPSLATE.defaultBlockState();
        BlockPos lo = h.absolutePos(new BlockPos(0, 3, 0)), hi = h.absolutePos(new BlockPos(71, 58, 71));
        for (BlockPos p : BlockPos.betweenClosed(Math.min(lo.getX(), hi.getX()), lo.getY(), Math.min(lo.getZ(), hi.getZ()),
                Math.max(lo.getX(), hi.getX()), hi.getY(), Math.max(lo.getZ(), hi.getZ()))) {
            level.setBlock(p, rock, Block.UPDATE_CLIENTS);
        }
        // the caves, a zombie in each that never despawns
        List<Mob> lurkers = new ArrayList<>();
        for (BlockPos c : List.of(CAVE_A, CAVE_B)) {
            for (BlockPos p : BlockPos.betweenClosed(c, c.offset(7, 2, 7))) {
                h.setBlock(p, Blocks.AIR);
            }
            Mob z = h.spawn(EntityType.ZOMBIE, c.offset(3, 0, 3));
            z.setPersistenceRequired();
            lurkers.add(z);
        }
        // the god's pocket before the gate's door, and the god in it, woken (the strike itself is the long run's: SurvivalRunGameTests)
        Rooms.Room gate = HoldRooms.room("dawi_hold_gate");
        PlanPlacement pl = AncestorPlan.placement(gate, h.absolutePos(DOOR_GROUND), Direction.EAST);
        BlockPos outside = pl.door().relative(Direction.WEST);
        for (int x = outside.getX() - 3; x <= outside.getX(); x++) {
            for (int z = outside.getZ() - 1; z <= outside.getZ() + 1; z++) {
                for (int y = outside.getY(); y <= outside.getY() + 2; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        @SuppressWarnings("removal")
        ServerPlayer god = h.makeMockServerPlayerInLevel();
        Waking.setGod(god, new Waking.God(true, "Karak Dunn", 0, false));
        god.setPos(Vec3.atBottomCenterOf(outside.relative(Direction.WEST, 2)));
        h.assertTrue(AncestorPlan.underRock(level, god.blockPosition()), "the god isn't down in the rock");
        for (Mob z : lurkers) {
            double far = z.position().distanceTo(Vec3.atCenterOf(Founding.heartSpot(pl)));
            h.assertTrue(far > 25 && far < 41, "a cave's zombie is " + Math.round(far) + " from the Shrine's spot, not in the hold's old 81-block cube");
        }
        h.getLevel().getServer().tickRateManager().requestGameToSprint(48000);

        // the founding: carved out of the rock, the Shrine on the dais
        List<String> told = new ArrayList<>();
        h.assertTrue(AncestorPlan.found(level, god, gate, h.absolutePos(DOOR_GROUND), Direction.EAST, c -> told.add(c.getString())),
                "the gate wasn't founded: " + told);
        h.assertTrue(told.get(0).startsWith("Grungni carves"), "the gate isn't carved out of the rock: " + told);
        Founding f = Founding.of(level);
        for (int s = 0; s < CARVE_SECONDS && f.rising(pl.door()); s++) {
            f.second(level, Founding.PER_SECOND);
        }
        h.assertTrue(!f.rising(pl.door()), "the gate was never carved through");
        BlockPos spot = Founding.heartSpot(pl);
        level.setBlockAndUpdate(spot, KRegistry.SHRINE.get().defaultBlockState());
        KRegistry.SHRINE.get().setPlacedBy(level, spot, KRegistry.SHRINE.get().defaultBlockState(), god, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        ShrineBlockEntity hold = (ShrineBlockEntity) level.getBlockEntity(spot);
        h.assertTrue(hold != null && hold.has(HoldRooms.GATE) && HoldCrag.carved(level, pl), "the hold doesn't own its carved gate");
        hand(h, god, spot, new ItemStack(Items.BREAD, Waking.KIT_BREAD));
        hand(h, god, spot, new ItemStack(Items.GOLD_INGOT, Waking.KIT_GOLD));
        line(h, hold, "the gate carved, the Shrine set, the kit's bread and gold handed over");

        // the two halls through the great hall's back seals: the Mine east (its seal facing south in the template's frame), the Great Hall west
        PlanPlacement mine = AncestorPlan.aimedAt(level, HoldRooms.room("dawi_mine"), pl.world(new BlockPos(29, 4, 26)), 0, Direction.SOUTH);
        PlanPlacement great = AncestorPlan.aimedAt(level, HoldRooms.room("dawi_grand_hall"), pl.world(new BlockPos(29, 4, 4)), 0, Direction.NORTH);
        h.assertTrue(HallSeal.arch(level, mine.door()).size() == 9 && HallSeal.arch(level, great.door()).size() == 9,
                "the great hall's back seals aren't whole arches: " + HallSeal.arch(level, mine.door()).size() + ", " + HallSeal.arch(level, great.door()).size());
        int goldBefore = hold.currency();
        h.assertTrue(AncestorPlan.dig(hold, level, mine, false, c -> told.add(c.getString())), "the Mine was refused: " + told);
        h.assertTrue(AncestorPlan.dig(hold, level, great, false, c -> told.add(c.getString())), "the Great Hall was refused: " + told);
        line(h, hold, "the Mine and the Great Hall ordered through the sealed doors (" + (goldBefore - hold.currency()) + " gold)");

        Map<String, Long> at = new LinkedHashMap<>();
        long ordered = h.getTick();
        long[] alarmSince = {-1};
        h.onEachTick(() -> {
            long now = h.getTick();
            if ((now - ordered) % FEED_EVERY == FEED_EVERY - 1) {
                hand(h, god, spot, new ItemStack(Items.BREAD, FEED_BREAD));
            }
            boolean lurking = lurkers.stream().allMatch(Mob::isAlive);
            if (hold.muster().alarmed()) {
                if (alarmSince[0] < 0) {
                    alarmSince[0] = now;
                }
                h.assertTrue(!lurking || now - alarmSince[0] < ALARM_MAX, "the alarm has been up " + (now - alarmSince[0]) + " ticks on end with the far"
                        + " caves' zombies still lurking: foes " + hold.muster().foes() + ", defenders " + hold.muster().defenderIds().size() + ", crew "
                        + hold.works().crewCount() + "; " + at);
            } else {
                alarmSince[0] = -1;
            }
            step(h, hold, at, "wide", hold.widening().wide(pl.door()) && hold.works().digs().stream().noneMatch(d -> d.room() == HoldRooms.GATE_WIDENING),
                    "the gate marked wide, no widening dug");
            for (PlanPlacement dig : List.of(mine, great)) {
                String name = dig == mine ? "mine" : "great_hall";
                step(h, hold, at, name + "_seal", HallSeal.arch(level, dig.door()).isEmpty(), "the " + name + "'s seal open");
                step(h, hold, at, name + "_in", inside(h, hold, dig) > 0, "a Miner of the crew in past the " + name + "'s wall");
                var work = hold.works().digs().stream().filter(d -> d.job().door.equals(dig.door())).findFirst().orElse(null);
                if (work != null) {
                    BlockPos behind = dig.door().relative(dig.facing());
                    step(h, hold, at, name + "_done_some", work.job().finishedCount() > 0, "the " + name + "'s dig has its first cells done: "
                            + work.job().finishedCount() + " of " + work.job().taskCount() + ", the cell behind its door " + level.getBlockState(behind).getBlock());
                    step(h, hold, at, name + "_plug", !level.getBlockState(behind).isAir(), "the cell behind the " + name + "'s door is " + level.getBlockState(behind).getBlock()
                            + " with " + work.job().finishedCount() + " of " + work.job().taskCount() + " cells done");
                }
                if (!at.containsKey(name + "_seal")) {
                    h.assertTrue(now - ordered < SEAL_BUDGET, "the " + name + "'s seal still stands " + SEAL_BUDGET + " ticks after its order");
                }
            }
            step(h, hold, at, "mine", hold.has(Rooms.Function.OREMINE), "the Mine dug out");
            step(h, hold, at, "great_hall", hold.has(HoldRooms.GREAT_HALL), "the Great Hall dug out");
            if (!at.containsKey("mine_in") || !at.containsKey("great_hall_in")) {
                boolean done = at.containsKey("mine") && at.containsKey("great_hall");
                h.assertTrue(done || now - ordered < IN_BUDGET, "no Miner of the crew walked in past a sealed door in " + IN_BUDGET + " ticks: " + at
                        + ", crew " + hold.works().crewCount() + ", digs " + hold.works().digs().stream().map(d -> d.room().id() + " "
                        + Math.round(d.job().progress() * 100) + "%").toList() + " || mine: " + where(h, hold, mine) + " || great hall: "
                        + where(h, hold, great));
            }
            if (now % 1200 == 0) {
                line(h, hold, "a minute's look: digs " + hold.works().digs().stream().map(d -> d.room().id() + " " + Math.round(d.job().progress() * 100) + "%").toList());
            }
            boolean halls = at.containsKey("mine") && at.containsKey("great_hall");
            h.assertTrue(halls || now - ordered < HALLS_BUDGET, "the halls weren't dug in " + HALLS_BUDGET + " ticks: " + at + ", digs "
                    + hold.works().digs().stream().map(d -> d.room().id() + " " + Math.round(d.job().progress() * 100) + "%").toList());
            if (halls && at.containsKey("mine_in") && at.containsKey("great_hall_in") && at.containsKey("wide")) {
                h.assertTrue(hold.peopleCount() + hold.reserveWorkers() + hold.reserveSoldiers() > Waking.FIRST_MINERS + Waking.FIRST_WARRIORS,
                        "the hold never grew past its first Dawi: " + hold.peopleCount() + " up, food " + hold.food());
                line(h, hold, "the run's end: " + at);
                h.getLevel().getServer().tickRateManager().stopSprinting();
                h.succeed();
            }
        });
    }

    /** How many of the crew stand inside {@code dig}'s box 2 or more past its door, along the way it runs. */
    private static int inside(GameTestHelper h, ShrineBlockEntity hold, PlanPlacement dig) {
        int n = 0;
        Direction runs = dig.facing();
        for (java.util.UUID id : hold.works().crewIds()) {
            var e = h.getLevel().getEntity(id);
            if (e == null) {
                continue;
            }
            BlockPos p = e.blockPosition();
            int along = (p.getX() - dig.door().getX()) * runs.getStepX() + (p.getZ() - dig.door().getZ()) * runs.getStepZ();
            n += along >= 2 && dig.box().isInside(p) ? 1 : 0;
        }
        return n;
    }

    /**
     * Where the crew stands against {@code dig}, and what is in its way: each member's cell relative to the test and how far along the dig it is
     * (door = 0, "in" inside its box), then the dig's task count against its template's, and the first cells behind its door along the way it runs.
     */
    private static String where(GameTestHelper h, ShrineBlockEntity hold, PlanPlacement dig) {
        Direction runs = dig.facing();
        StringBuilder sb = new StringBuilder("door " + h.relativePos(dig.door()).toShortString() + " running " + runs + ", crew: ");
        for (java.util.UUID id : hold.works().crewIds()) {
            var e = h.getLevel().getEntity(id);
            if (e == null) {
                continue;
            }
            BlockPos p = e.blockPosition();
            int along = (p.getX() - dig.door().getX()) * runs.getStepX() + (p.getZ() - dig.door().getZ()) * runs.getStepZ();
            sb.append(h.relativePos(p).toShortString()).append(" [").append(along).append(dig.box().isInside(p) ? " in" : "").append("] ");
        }
        for (var d : hold.works().digs()) {
            if (d.job().door.equals(dig.door())) {
                sb.append("; ").append(d.job().taskCount()).append(" tasks of the template's ")
                        .append(claude.settlementcore.room.RoomTemplates.get(h.getLevel(), dig.room().id()).size()).append(" cells; behind the door:");
                for (int k = 1; k <= 6; k++) {
                    BlockPos p = dig.door().relative(runs, k);
                    sb.append(' ').append(k).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(h.getLevel().getBlockState(p).getBlock()).getPath());
                }
            }
        }
        return sb.toString();
    }

    private static void step(GameTestHelper h, ShrineBlockEntity hold, Map<String, Long> at, String key, boolean done, String what) {
        if (done && !at.containsKey(key)) {
            at.put(key, h.getTick());
            line(h, hold, what);
        }
    }
}
