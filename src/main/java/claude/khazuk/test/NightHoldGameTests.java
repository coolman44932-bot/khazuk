package claude.khazuk.test;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.settlementcore.food.Fare;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A hold at night on Hard (the user plays survival, nights and all; the floors are his rule: memory unit-strength-from-lore, per stat the
 * stronger of the army book and TWW3, and Khazuk!'s own floors in DawiFightGameTests, LongbeardGameTests and EliteGameTests, fought there
 * on Normal by day). Here at midnight with the clock stopped, on Hard, natural spawning on:
 * <ul>
 * <li>khazuknight: the hold keeps its halls free of monsters. A hollow held by the hold (inside its Shrine's 81-block cube) and the same
 * hollow outside it, both sealed in stone and both DARK (the hold's real halls are lit by their own lanterns; dark proves the hold itself
 * keeps them, SettlementSpawns#position): the outside one gets a monster, the held one none, with a survival god near enough for spawning.
 * The flat test world fills the mob cap within ticks once it is night, so every monster outside the two hollows is taken away each tick
 * (as the engine's harness does): where and whether one comes is NaturalSpawner's.</li>
 * <li>khazuknightfight: the floors at night on Hard against zombies spawned as the night spawns them (finalizeSpawn NATURAL on Hard: their
 * armour and weapons by Hard's odds, babies and all), with Hard's reinforcements (a hurt zombie on Hard calls more while monsters spawn;
 * no player is near, so nothing else spawns): one Warrior beats three; a Longbeard an axe vindicator and two; an Ironbreaker an axe
 * vindicator and three; and a zombie that walks in on a hold is fought off by its guards, the alarm up and the hold losing no one.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazuknight", time = 18000, daylightCycle = BatchWorld.Rule.OFF, mobSpawning = BatchWorld.Rule.ON,
        difficulty = Difficulty.HARD, weather = BatchWorld.Weather.CLEAR, simulationDistance = 6)
@BatchWorld(batch = "khazuknightfight", time = 18000, daylightCycle = BatchWorld.Rule.OFF, mobSpawning = BatchWorld.Rule.ON,
        difficulty = Difficulty.HARD, weather = BatchWorld.Weather.CLEAR, simulationDistance = 6)
public class NightHoldGameTests {

    // ------------------------------------------------------------------ khazuknight

    /** The two corners (test-relative, inclusive) of a box laid out for a spawn at the plot's (0, 0) corner, mirrored across the 72-wide plot as asked. */
    private static BlockPos[] mirrored(boolean flipX, boolean flipZ, int x0, int y0, int z0, int x1, int y1, int z1) {
        int ax = flipX ? 71 - x0 : x0, bx = flipX ? 71 - x1 : x1, az = flipZ ? 71 - z0 : z0, bz = flipZ ? 71 - z1 : z1;
        return new BlockPos[]{new BlockPos(Math.min(ax, bx), y0, Math.min(az, bz)), new BlockPos(Math.max(ax, bx), y1, Math.max(az, bz))};
    }

    @GameTest(template = "field", timeoutTicks = 6000, batch = "khazuknight")
    public static void aHeldHallGetsNoMonsterWhereTheRockOutsideTheHoldDoes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        // The world's spawn is the first plot's corner (SurvivalHarnessGameTests#aDarkHollowSpawnsAMonsterAndALitOneDoesNot), and no monster
        // spawns within 24 of it: the first run had the held hollow 4 blocks from it. So the hollows go in the far corner from the spawn: the
        // layout below is for a spawn at the plot's (0, 0) corner and is mirrored when the spawn is nearer another (the plot is 72 wide).
        Vec3 spawnAt = Vec3.atCenterOf(level.getRespawnData().pos());
        Vec3 spawnRel = h.relativeVec(spawnAt);
        boolean flipX = spawnRel.x > 36, flipZ = spawnRel.z > 36;
        SurvivalPlay.fill(h, new BlockPos(0, 3, 0), new BlockPos(71, 14, 71), Blocks.STONE.defaultBlockState());
        BlockPos[] heldCorners = mirrored(flipX, flipZ, 56, 4, 4, 64, 6, 12);
        BlockPos[] outsideCorners = mirrored(flipX, flipZ, 4, 4, 58, 12, 6, 66);
        SurvivalPlay.fill(h, heldCorners[0], heldCorners[1], Blocks.AIR.defaultBlockState());
        SurvivalPlay.fill(h, outsideCorners[0], outsideCorners[1], Blocks.AIR.defaultBlockState());
        AABB held = h.absoluteAABB(new AABB(heldCorners[0].getX(), heldCorners[0].getY(), heldCorners[0].getZ(),
                heldCorners[1].getX() + 1, heldCorners[1].getY() + 1, heldCorners[1].getZ() + 1));
        AABB outside = h.absoluteAABB(new AABB(outsideCorners[0].getX(), outsideCorners[0].getY(), outsideCorners[0].getZ(),
                outsideCorners[1].getX() + 1, outsideCorners[1].getY() + 1, outsideCorners[1].getZ() + 1));
        SurvivalGod god = SurvivalGod.join(h, new BlockPos(36, 15, 36));
        BlockPos shrineAt = mirrored(flipX, flipZ, 60, 4, 8, 60, 4, 8)[0];
        ShrineBlockEntity hold = SurvivalPlay.shrineFor(h, god, shrineAt, false);
        h.assertTrue(hold.inSettlement(BlockPos.containing(held.getCenter())) && !hold.inSettlement(BlockPos.containing(outside.getCenter())),
                "the held hollow must be in the hold and the other out of it: shrine at " + shrineAt + ", held " + heldCorners[0] + ".." + heldCorners[1]
                        + ", outside " + outsideCorners[0] + ".." + outsideCorners[1]);
        Vec3 godAt = god.player().position();
        for (AABB box : List.of(held, outside)) {
            double toSpawn = Math.sqrt(box.distanceToSqr(spawnAt)), toGod = Math.sqrt(box.distanceToSqr(godAt));
            h.assertTrue(toSpawn > 24.5 && toGod > 25, "a hollow (" + h.relativeVec(box.getCenter()) + ") is within 24 of the world spawn or the god: nothing would spawn there"
                    + " whatever the hold: " + toSpawn + " from the spawn at " + spawnRel + " (absolute " + spawnAt + "), " + toGod + " from the god at "
                    + h.relativeVec(godAt) + " (absolute " + godAt + ")");
        }
        boolean[] seen = {false};
        h.onEachTick(() -> {
            if (god.hasLeft()) {
                return;
            }
            for (Mob m : level.getEntitiesOfClass(Mob.class, god.player().getBoundingBox().inflate(160), m -> m instanceof Enemy && m.isAlive())) {
                if (m.getBoundingBox().intersects(held)) {
                    h.fail("a " + m.getType().toShortString() + " spawned in the hall the hold holds, at " + h.relativeVec(m.position()));
                } else if (m.getBoundingBox().intersects(outside)) {
                    seen[0] = true;
                } else {
                    m.discard();
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(seen[0], "no monster in the hollow outside the hold yet (difficulty " + level.getDifficulty() + ")");
            h.assertTrue(h.getTick() >= 1200, "the held hall has been watched only " + h.getTick() + " ticks");
        });
    }

    // ------------------------------------------------------------------ khazuknightfight

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.addFood(200, Fare.COOKED, "the test");
        return heart;
    }

    /** A zombie as the night spawns one on Hard: its gear, baby or not, its reinforcement chance. */
    private static Zombie nightZombie(GameTestHelper h, BlockPos at) {
        return h.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(at), EntitySpawnReason.NATURAL);
    }

    private static Vindicator axeVindicator(GameTestHelper h, BlockPos at) {
        Vindicator v = h.spawn(EntityType.VINDICATOR, at);
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        return v;
    }

    /** The zombies standing within 12 of {@code near}: the ones spawned and any Hard called in. */
    private static List<Zombie> zombiesNear(GameTestHelper h, LivingEntity near) {
        return h.getLevel().getEntitiesOfClass(Zombie.class, near.getBoundingBox().inflate(12), Zombie::isAlive);
    }

    /** {@code me} alive, every one of {@code foes} dead, and no zombie Hard called in still up near him. */
    private static void beats(GameTestHelper h, Dawi me, List<? extends LivingEntity> foes, String what) {
        h.assertTrue(h.getLevel().getDifficulty() == Difficulty.HARD && h.getLevel().isDarkOutside(), "not a Hard night: " + h.getLevel().getDifficulty());
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the " + me.getName().getString() + " fell to " + what + " on a Hard night");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth() + "; the "
                                + me.getName().getString() + " at " + me.getHealth());
                    }
                    h.assertTrue(zombiesNear(h, me).isEmpty(), zombiesNear(h, me).size() + " zombies (Hard's reinforcements) still up by the "
                            + me.getName().getString() + ", at " + me.getHealth());
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor at night on Hard: the {} beat {} with {}/{} health left", me.getName().getString(), what,
                        me.getHealth(), me.getMaxHealth()))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazuknightfight")
    public static void aWarriorBeatsThreeHardZombiesAtNight(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior me = h.spawn(KRegistry.WARRIOR.get(), HEART.offset(-3, 0, 0));
        me.bindTo(heart);
        List<Zombie> z = List.of(nightZombie(h, HEART.offset(5, 0, -2)), nightZombie(h, HEART.offset(5, 0, 0)), nightZombie(h, HEART.offset(5, 0, 2)));
        beats(h, me, z, "three Hard zombies");
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazuknightfight")
    public static void aLongbeardBeatsAnAxeVindicatorAndTwoHardZombiesAtNight(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior me = h.spawn(KRegistry.LONGBEARD.get(), HEART.offset(-3, 0, 0));
        me.bindTo(heart);
        List<LivingEntity> foes = List.of(axeVindicator(h, HEART.offset(4, 0, 0)), nightZombie(h, HEART.offset(4, 0, -3)), nightZombie(h, HEART.offset(4, 0, 3)));
        beats(h, me, foes, "an axe vindicator and two Hard zombies");
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazuknightfight")
    public static void anIronbreakerBeatsAnAxeVindicatorAndThreeHardZombiesAtNight(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior me = h.spawn(KRegistry.IRONBREAKER.get(), HEART.offset(-3, 0, 0));
        me.bindTo(heart);
        List<LivingEntity> foes = List.of(axeVindicator(h, HEART.offset(4, 0, 0)), nightZombie(h, HEART.offset(4, 0, -3)),
                nightZombie(h, HEART.offset(4, 0, 3)), nightZombie(h, HEART.offset(6, 0, 0)));
        beats(h, me, foes, "an axe vindicator and three Hard zombies");
    }

    /**
     * A Hard night's zombie walks in on a hold whose round is up (two Miners and a Warrior) with two Warriors waiting in its deeps: its guards
     * fall on it, the alarm goes up, it dies by a Dwarf's hand, and the hold loses no one.
     */
    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazuknightfight")
    public static void aZombieThatWalksInOnTheHoldIsFoughtOffByItsGuards(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        // the muster watches only a hold with an owner (Muster#second: "h.owner() == null" stands any alarm down and runs none); the hold the
        // first run set down had none, so its Dawi fought the zombie on their own and the alarm never went up (HallGameTests does the same)
        @SuppressWarnings("removal")
        var owner = h.makeMockServerPlayerInLevel();
        heart.claim(owner, "Karak Test");
        heart.roundForTests(h.getLevel());
        heart.addToReserve(0, 2);
        AABB round = new AABB(h.absolutePos(HEART)).inflate(8);
        Zombie[] z = {null};
        boolean[] alarmed = {false};
        List<Dawi> people = new ArrayList<>();
        h.onEachTick(() -> alarmed[0] |= heart.muster().alarmed());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(Miner.class, round).size() == 2
                        && h.getLevel().getEntitiesOfClass(Warrior.class, round).size() == 1, "the round isn't up"))
                .thenExecute(() -> {
                    people.addAll(h.getLevel().getEntitiesOfClass(Dawi.class, round));
                    z[0] = nightZombie(h, HEART.offset(9, 0, 0));
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(z[0].isDeadOrDying(), "the zombie still stands at " + z[0].getHealth() + " at " + h.relativeVec(z[0].position())
                            + "; alarm " + heart.muster().alarmed() + ", defenders " + heart.muster().defenderIds().size());
                    h.assertTrue(zombiesNear(h, z[0]).isEmpty(), "Hard's reinforcements are still up by the hold");
                })
                .thenExecute(() -> {
                    h.assertTrue(z[0].getLastHurtByMob() instanceof Dawi, "the zombie's last attacker was " + z[0].getLastHurtByMob() + ", not a Dwarf");
                    h.assertTrue(alarmed[0], "the hold never raised its alarm for a zombie walking in");
                    h.assertTrue(heart.muster().lost() == 0 && people.stream().allMatch(LivingEntity::isAlive), "the hold lost "
                            + heart.muster().lost() + " to one Hard zombie");
                })
                .thenSucceed();
    }
}
