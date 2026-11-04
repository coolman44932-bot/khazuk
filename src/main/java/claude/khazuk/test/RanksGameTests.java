package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Marksman;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import claude.khazuk.hold.Ranks;
import claude.settlementcore.food.Fare;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * D6, the hold's missile troops (Marksman, Quarreller, Thunderer, hold/Ranks): the Barracks raises one of the fewer kind out of food until
 * the hold keeps four of each, and none without it; a Marksman shoots a foe he can see within range and not one too close or too far; a
 * Thunderer's shot bites through armour; and the floors: one Quarreller, one Thunderer, each beats three zombies.
 */
@GameTestHolder(Khazuk.MODID)
public class RanksGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "khazuk")
    public static void theBarracksRaisesFourOfEachAndNoneWithoutIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        for (int s = 0; s < Ranks.RAISE_SECONDS * 2; s++) {
            heart.ranks().second(h.getLevel(), heart);
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, Quarreller.class) + Ranks.count(h.getLevel(), heart, Thunderer.class) == 0,
                "missile troops came up with no Barracks");
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        heart.addBuilding(new Building("dawi_barracks", new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 3, at.getY() + 3, at.getZ() + 3)), false);
        int food = heart.food(), raised = 0;
        while (heart.ranks().raise(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= 2 * Ranks.EACH, "more than " + 2 * Ranks.EACH + " came up");
        }
        int q = Ranks.count(h.getLevel(), heart, Quarreller.class), t = Ranks.count(h.getLevel(), heart, Thunderer.class);
        h.assertTrue(q == Ranks.EACH && t == Ranks.EACH, "the Barracks keeps " + q + " Quarrellers and " + t + " Thunderers");
        h.assertTrue(food - heart.food() == raised * Ranks.COST, "they cost " + (food - heart.food()) + " food, not " + raised * Ranks.COST);
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aMarksmanShootsOnlyInRangeAndAThunderersShotBitesArmour(GameTestHelper h) {
        Quarreller q = h.spawn(KRegistry.QUARRELLER.get(), new BlockPos(2, 1, 12));
        q.setNoAi(true);
        Zombie near = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 12)), mid = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 12));
        near.setNoAi(true);
        mid.setNoAi(true);
        h.assertTrue(!q.canShoot(near), "he would shoot a foe at arm's length, not fight it");
        h.assertTrue(q.canShoot(mid), "he can't shoot a foe 12 blocks off in plain sight");
        Thunderer t = h.spawn(KRegistry.THUNDERER.get(), new BlockPos(2, 1, 4));
        t.setNoAi(true);
        Zombie plated = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 4));
        plated.setNoAi(true);
        plated.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        h.assertTrue(t.shotOn(plated) > Thunderer.SHOT && t.shotOn(mid) == Thunderer.SHOT, "the ball bites: " + t.shotOn(plated) + " on plate, "
                + t.shotOn(mid) + " on a bare zombie");
        float hp = mid.getHealth();
        q.shoot(h.getLevel(), mid);
        h.assertTrue(q.shotsForTests == 1 && mid.getHealth() < hp, "the bolt didn't land: zombie at " + mid.getHealth());
        h.succeed();
    }

    private static void beats(GameTestHelper h, Marksman m) {
        Zombie[] z = new Zombie[3];
        for (int i = 0; i < 3; i++) {
            z[i] = h.spawn(EntityType.ZOMBIE, new BlockPos(20, 1, 8 + i * 4));
            z[i].setPersistenceRequired();
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(m.isAlive(), "the " + m.getName().getString() + " fell to three zombies");
                    for (Zombie each : z) {
                        h.assertTrue(each.isDeadOrDying(), "a zombie still stands at " + each.getHealth() + "; the Dwarf at " + m.getHealth());
                    }
                })
                .thenExecute(() -> {
                    h.assertTrue(m.shotsForTests >= 1, "he never shot");
                    Khazuk.LOG.info("KHAZUK floor: a {} beat 3 zombies with {}/{} health left, {} shots", m.getName().getString(), m.getHealth(),
                            m.getMaxHealth(), m.shotsForTests);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneQuarrellerBeatsThreeZombies(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Quarreller q = h.spawn(KRegistry.QUARRELLER.get(), new BlockPos(4, 1, 12));
        q.bindTo(heart);
        beats(h, q);
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneThundererBeatsThreeZombies(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Thunderer t = h.spawn(KRegistry.THUNDERER.get(), new BlockPos(4, 1, 12));
        t.bindTo(heart);
        beats(h, t);
    }
}
