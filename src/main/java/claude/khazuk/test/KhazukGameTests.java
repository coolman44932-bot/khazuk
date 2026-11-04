package claude.khazuk.test;

import claude.settlementcore.food.Fare;
import claude.settlementcore.test.AfterBatch;
import claude.settlementcore.test.BeforeBatch;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.khazuk.Warrior;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Dawi;
import claude.khazuk.Miner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The Dwarfs on the engine, end to end in a real level: the Shrine makes its people out of food and brings them up, a worker sent home
 * sinks back into the reserve, and a guard falls on a foe. The engine's own tests stand in for a race with a stub; these are a race.
 */
@GameTestHolder(Khazuk.MODID)
public class KhazukGameTests {

    /** The Shrine on the arena's floor (the engine's tools/gen_testrace_arena.py: smooth stone at y 0, open above; the digsite is solid stone). */
    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity heart(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.addFood(200, Fare.COOKED, "the test");
        return heart;
    }

    private static <T extends Dawi> List<T> near(GameTestHelper h, Class<T> kind) {
        return h.getLevel().getEntitiesOfClass(kind, new AABB(h.absolutePos(HEART)).inflate(8));
    }

    /** A round makes two workers and a guard out of the Shrine's food (4, 4 and 6 points), and, with room for 8 up, brings all three up. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazuk")
    public static void aHeartRaisesItsPeopleFromFood(GameTestHelper h) {
        ShrineBlockEntity heart = heart(h);
        int food = heart.food();
        heart.roundForTests(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(near(h, Miner.class).size() == 2 && near(h, Warrior.class).size() == 1,
                        "up beside the Shrine: " + near(h, Miner.class).size() + " workers and " + near(h, Warrior.class).size() + " guards, not 2 and 1"))
                .thenExecute(() -> {
                    h.assertTrue(heart.peopleCount() == 3 && heart.reserve() == 0, "the roll has " + heart.peopleCount() + " and the reserve "
                            + heart.reserve() + ", not 3 and 0");
                    h.assertTrue(food - heart.food() == 14, "the round cost " + (food - heart.food()) + " food points, not 4 + 4 + 6");
                    for (Dawi u : near(h, Dawi.class)) {
                        h.assertTrue(u.settlement() == heart, u.getName().getString() + " is not bound to the Shrine");
                    }
                })
                .thenSucceed();
    }

    /**
     * The halls make room (0.12.2, the user's "raise the cap"): with none, a hold keeps 8 up and the rest wait in the deeps; with a Great
     * Hall and a Barracks, the rounds bring the waiting Dawi up until 18 are up.
     */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazuk")
    public static void theHallsMakeRoomAndTheDeepsComeUp(GameTestHelper h) {
        ShrineBlockEntity heart = heart(h);
        heart.addFood(500, claude.settlementcore.food.Fare.COOKED, "the test");
        heart.addToReserve(16, 6);
        for (int i = 0; i < 6; i++) {
            heart.roundForTests(h.getLevel());
        }
        h.assertTrue(heart.peopleCap() == ShrineBlockEntity.UP_AT_ONCE && heart.peopleCount() == ShrineBlockEntity.UP_AT_ONCE,
                "with no halls: " + heart.peopleCount() + " up of room for " + heart.peopleCap() + ", not 8");
        BlockPos a = h.absolutePos(new BlockPos(1, 1, 1)), b = h.absolutePos(new BlockPos(1, 1, 18));
        heart.addBuilding(new claude.settlementcore.settlement.Building("dawi_great_hall",
                new net.minecraft.world.level.levelgen.structure.BoundingBox(a.getX(), a.getY(), a.getZ(), a.getX() + 3, a.getY() + 3, a.getZ() + 3)), false);
        heart.addBuilding(new claude.settlementcore.settlement.Building("dawi_barracks",
                new net.minecraft.world.level.levelgen.structure.BoundingBox(b.getX(), b.getY(), b.getZ(), b.getX() + 3, b.getY() + 3, b.getZ() + 3)), false);
        int room = ShrineBlockEntity.UP_AT_ONCE + ShrineBlockEntity.GREAT_HALL_ROOM + ShrineBlockEntity.BARRACKS_ROOM;
        for (int i = 0; i < 6; i++) {
            heart.roundForTests(h.getLevel());
        }
        h.assertTrue(heart.peopleCap() == room && heart.peopleCount() == room, "with a Great Hall and a Barracks: " + heart.peopleCount()
                + " up of room for " + heart.peopleCap() + ", not " + room);
        h.succeed();
    }

    /** Sent home, a worker walks to the Shrine and sinks into the ground: off the roll and into the reserve, and gone from the world. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "khazuk")
    public static void aWorkerSentHomeSinksBackIntoTheReserve(GameTestHelper h) {
        ShrineBlockEntity heart = heart(h);
        heart.roundForTests(h.getLevel());
        Miner[] sent = new Miner[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(near(h, Miner.class).size() == 2, "the round's workers are not up"))
                .thenExecute(() -> {
                    sent[0] = near(h, Miner.class).get(0);
                    sent[0].goHome();
                })
                .thenWaitUntil(() -> h.assertTrue(sent[0].isRemoved(), "the worker sent home is still about: " + sent[0].runningGoals()))
                .thenExecute(() -> h.assertTrue(heart.reserveWorkers() == 1 && heart.peopleCount() == 2 && !heart.hasPerson(sent[0].getUUID()),
                        "after it sank: " + heart.reserveWorkers() + " workers below and " + heart.peopleCount() + " up, not 1 and 2"))
                .thenSucceed();
    }

    /**
     * A guard falls on a husk that comes near (a foe by the engine's word), hurts it, plays its attack clip, and its swing runs out after.
     * Hostiles need the Normal difficulty: the batch sets it.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "khazukhostile")
    public static void aGuardFallsOnAFoe(GameTestHelper h) {
        ShrineBlockEntity heart = heart(h);
        heart.roundForTests(h.getLevel());
        Husk[] foe = new Husk[1];
        Warrior[] guard = new Warrior[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(near(h, Warrior.class).size() == 1, "the round's guard is not up"))
                .thenExecute(() -> {
                    guard[0] = near(h, Warrior.class).get(0);
                    near(h, Miner.class).forEach(m -> m.discard());   // D2's Miners fight well enough to take the husk first
                    foe[0] = h.spawn(EntityType.HUSK, HEART.offset(5, 0, 0));
                    foe[0].setPersistenceRequired();
                })
                // the guard's own blow (the husk's health alone once passed at its first tick: something else had hurt it)
                .thenWaitUntil(() -> {
                    Warrior g = guard[0];
                    h.assertTrue(g.attacksForTests > 0, "the guard has landed no blow: " + (g.isRemoved() ? "it is gone (" + g.getRemovalReason() + ", burrowing "
                            + g.isBurrowing() + ", homeward " + g.isHomeward() + ", dead " + g.isDeadOrDying() + ")" : "its target is " + g.getTarget() + ", goals "
                            + g.runningGoals() + ", at " + g.blockPosition()) + "; the husk at " + foe[0].getHealth() + "/" + foe[0].getMaxHealth()
                            + ", last hurt by " + foe[0].getLastDamageSource());
                })
                .thenExecute(() -> {
                    h.assertTrue(foe[0].isDeadOrDying() || foe[0].getHealth() < foe[0].getMaxHealth(), "the guard's blow left the husk unhurt");
                    foe[0].discard();
                })
                // its arm's swing runs out (a settler's swing timer once never ticked, so it swung for good: the review of 737e467)
                .thenWaitUntil(() -> h.assertTrue(!guard[0].swinging, "the guard is still swinging long after its blow"))
                .thenSucceed();
    }

    private static Difficulty was = Difficulty.PEACEFUL;

    @BeforeBatch(batch = "khazukhostile")
    public static void hostileBegins(ServerLevel level) {
        was = level.getServer().getWorldData().getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
    }

    @AfterBatch(batch = "khazukhostile")
    public static void hostileEnds(ServerLevel level) {
        level.getServer().setDifficulty(was, true);
    }
}
