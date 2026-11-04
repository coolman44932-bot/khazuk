package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Longbeard;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.Ranks;
import claude.settlementcore.food.Fare;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 0.11, the Longbeards (Longbeard, hold/Ranks): a Great Hall raises two of each loadout out of food, and none without it; Old Grumblers
 * steadies the hold's Dawi round a Longbeard (stubborn at full health) and no others, and he is stubborn himself; a Warrior who has felled
 * eight foes grows into a Longbeard where he stands, off the roll; the great axe bites armour and the shield Longbeard's axe doesn't; a
 * Longbeard bears an open grudge a Warrior doesn't yet; and the floors: each loadout beats an axe vindicator and two zombies at once.
 */
@GameTestHolder(Khazuk.MODID)
public class LongbeardGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    private static <T extends Warrior> T bound(GameTestHelper h, EntityType<T> type, ShrineBlockEntity heart, BlockPos at) {
        T w = h.spawn(type, at);
        w.bindTo(heart);
        return w;
    }

    private static Zombie still(GameTestHelper h, BlockPos at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        return z;
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theGreatHallRaisesTwoOfEachLongbeardAndNoneWithoutIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        for (int s = 0; s < Ranks.LONGBEARD_SECONDS * 2; s++) {
            heart.ranks().second(h.getLevel(), heart);
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, KRegistry.LONGBEARD.get()) + Ranks.count(h.getLevel(), heart, KRegistry.LONGBEARD_GREAT.get()) == 0,
                "Longbeards came up with no Great Hall");
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        heart.addBuilding(new Building("dawi_great_hall", new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 3, at.getY() + 3, at.getZ() + 3)), false);
        int food = heart.food(), raised = 0;
        while (heart.ranks().raiseLongbeard(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= 2 * Ranks.LONGBEARD_EACH, "more than " + 2 * Ranks.LONGBEARD_EACH + " came up");
        }
        int s = Ranks.count(h.getLevel(), heart, KRegistry.LONGBEARD.get()), g = Ranks.count(h.getLevel(), heart, KRegistry.LONGBEARD_GREAT.get());
        h.assertTrue(s == Ranks.LONGBEARD_EACH && g == Ranks.LONGBEARD_EACH, "the Great Hall keeps " + s + " with shields and " + g + " with great axes");
        h.assertTrue(food - heart.food() == raised * Ranks.LONGBEARD_COST, "they cost " + (food - heart.food()) + " food");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void oldGrumblersSteadyTheHoldsDawiRoundHimAndNoOthers(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Longbeard lb = bound(h, KRegistry.LONGBEARD.get(), heart, new BlockPos(4, 1, 4));
        Warrior near = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(8, 1, 4));
        Warrior far = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(20, 1, 20));
        Warrior stranger = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(6, 1, 6));
        for (var w : List.of(lb, near, far, stranger)) {
            w.setNoAi(true);
        }
        h.assertTrue(lb.isStubborn(), "a Longbeard at full health isn't stubborn");
        h.assertTrue(!near.isStubborn(), "a Warrior at full health was stubborn before the grumbling");
        int n = lb.grumble(h.getLevel());
        h.assertTrue(near.isStubborn() && near.steadied(), "the Warrior beside the Longbeard wasn't steadied");
        h.assertTrue(!far.isStubborn() && !stranger.isStubborn(), "steadied: a Warrior 16 blocks off " + far.isStubborn() + ", a Warrior of no hold "
                + stranger.isStubborn());
        h.assertTrue(n == 1, "he steadied " + n + ", not the one Warrior beside him");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "khazuk")
    public static void aWarriorWithEightKillsGrowsIntoALongbeard(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior w = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(6, 1, 6));
        heart.addPerson(w);
        int people = heart.peopleCount();
        for (int i = 0; i < Warrior.VETERAN_KILLS; i++) {
            Zombie z = still(h, new BlockPos(2 + i * 2, 1, 18));
            z.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(w), 1000f);
        }
        h.assertTrue(w.kills() == Warrior.VETERAN_KILLS, "the Warrior counts " + w.kills() + " kills, not " + Warrior.VETERAN_KILLS);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(w.isRemoved(), "the Warrior hasn't grown into a Longbeard"))
                .thenExecute(() -> {
                    List<Longbeard> lbs = h.getLevel().getEntitiesOfClass(Longbeard.class, new AABB(w.blockPosition()).inflate(2));
                    h.assertTrue(lbs.size() == 1 && lbs.get(0).getType() == KRegistry.LONGBEARD.get() && lbs.get(0).settlement() == heart,
                            "where the Warrior stood: " + lbs);
                    h.assertTrue(lbs.get(0).kills() == Warrior.VETERAN_KILLS, "the Longbeard forgot his " + Warrior.VETERAN_KILLS + " kills");
                    h.assertTrue(heart.peopleCount() == people - 1, "the roll: " + heart.peopleCount() + " (was " + people + ")");
                })
                .thenSucceed();
    }

    /** Hostile (Normal) batch: the zombie must live past this tick for its chestplate's armour to count. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void theGreatAxeBitesArmourAndTheOldOnesBearEveryGrudge(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Longbeard great = bound(h, KRegistry.LONGBEARD_GREAT.get(), heart, new BlockPos(4, 1, 4));
        Longbeard shield = bound(h, KRegistry.LONGBEARD.get(), heart, new BlockPos(4, 1, 8));
        Warrior young = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(4, 1, 12));
        for (var w : List.of(great, shield, young)) {
            w.setNoAi(true);
        }
        Zombie plated = still(h, new BlockPos(18, 1, 4));
        plated.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        heart.grudges().wronged(h.getLevel(), heart, plated, "slew a Miner");
        h.assertTrue(great.bearsGrudgeAgainst(plated) && shield.bearsGrudgeAgainst(plated), "a Longbeard doesn't bear the open grudge against zombies");
        h.assertTrue(!young.bearsGrudgeAgainst(plated), "a Warrior bears a grudge that isn't settled yet");
        h.runAfterDelay(2, () -> {
            double bite = great.greatBite(plated);
            h.assertTrue(Math.abs(bite - Math.min(Longbeard.GREAT_BITE_MAX, plated.getArmorValue() * Longbeard.GREAT_BITE)) < 1e-6 && bite > 0,
                    "the great axe bites " + bite + " into armour " + plated.getArmorValue());
            h.assertTrue(shield.greatBite(plated) == 0, "the shield Longbeard's axe bites armour");
            h.succeed();
        });
    }

    private static void beatsAVindicatorAndTwoZombies(GameTestHelper h, EntityType<Longbeard> type) {
        ShrineBlockEntity heart = hold(h);
        Longbeard me = bound(h, type, heart, HEART.offset(-3, 0, 0));
        Vindicator v = h.spawn(EntityType.VINDICATOR, HEART.offset(4, 0, 0));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        v.setPersistenceRequired();
        Zombie[] z = {h.spawn(EntityType.ZOMBIE, HEART.offset(4, 0, -3)), h.spawn(EntityType.ZOMBIE, HEART.offset(4, 0, 3))};
        for (Zombie each : z) {
            each.setPersistenceRequired();
        }
        List<LivingEntity> foes = List.of(v, z[0], z[1]);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the " + me.getName().getString() + " fell to a vindicator and two zombies");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth() + "; the Longbeard at " + me.getHealth());
                    }
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: a {} beat an axe vindicator and two zombies with {}/{} health left, {} blows turned",
                        me.getName().getString(), me.getHealth(), me.getMaxHealth(), me.blowsTurnedForTests))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void aShieldLongbeardBeatsAVindicatorAndTwoZombies(GameTestHelper h) {
        beatsAVindicatorAndTwoZombies(h, KRegistry.LONGBEARD.get());
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void aGreatAxeLongbeardBeatsAVindicatorAndTwoZombies(GameTestHelper h) {
        beatsAVindicatorAndTwoZombies(h, KRegistry.LONGBEARD_GREAT.get());
    }
}
