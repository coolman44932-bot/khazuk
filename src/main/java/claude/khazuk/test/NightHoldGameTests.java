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

    @GameTest(template = "field", timeoutTicks = 6000, batch = "khazuknight")
    public static void aHeldHallGetsNoMonsterWhereTheRockOutsideTheHoldDoes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        SurvivalPlay.fill(h, new BlockPos(0, 3, 0), new BlockPos(71, 14, 71), Blocks.STONE.defaultBlockState());
        SurvivalPlay.fill(h, new BlockPos(4, 4, 4), new BlockPos(12, 6, 12), Blocks.AIR.defaultBlockState());       // held
        SurvivalPlay.fill(h, new BlockPos(58, 4, 58), new BlockPos(66, 6, 66), Blocks.AIR.defaultBlockState());     // outside the hold
        AABB held = h.absoluteAABB(new AABB(4, 4, 4, 13, 7, 13));
        AABB outside = h.absoluteAABB(new AABB(58, 4, 58, 67, 7, 67));
        SurvivalGod god = SurvivalGod.join(h, new BlockPos(36, 15, 36));
        ShrineBlockEntity hold = SurvivalPlay.shrineFor(h, god, new BlockPos(8, 4, 8), false);
        h.assertTrue(hold.inSettlement(BlockPos.containing(held.getCenter())) && !hold.inSettlement(BlockPos.containing(outside.getCenter())),
                "the held hollow must be in the hold and the other out of it");
        Vec3 spawn = Vec3.atCenterOf(level.getRespawnData().pos());
        for (AABB box : List.of(held, outside)) {
            h.assertTrue(box.distanceToSqr(spawn) > 24.5 * 24.5 && box.distanceToSqr(god.player().position()) > 25 * 25,
                    "a hollow is within 24 of the world spawn or the god: nothing would spawn there whatever the hold");
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
