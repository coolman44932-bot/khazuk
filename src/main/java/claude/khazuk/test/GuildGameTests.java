package claude.khazuk.test;

import claude.khazuk.EngineerSlayer;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Slayer;
import claude.khazuk.SlayerMech;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * 0.14.2, the Engineers' Guild (the user's: Malakai-style Engineer Slayers whose guns sometimes blow up, and the Slayer mech whose boiler
 * runs too hot and whose pilot jumps out when it breaks):
 * <ul>
 * <li>the Guild raises four Engineer Slayers and builds one mech for food and gold, and nothing without it;</li>
 * <li>an Engineer's shot scatters onto the foes beside his mark; an overload bursts there, burning them, and singes him, never to death;</li>
 * <li>the mech runs hot past {@value SlayerMech#HOT} (a third harder) and scalds itself past the red line;</li>
 * <li>broken, the mech bursts and its pilot leaps out a Giant Slayer of the hold, on its roll;</li>
 * <li>the floors: an Engineer Slayer beats an axe vindicator and two zombies; the mech a ravager and two axe vindicators.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
public class GuildGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    private static void guild(GameTestHelper h, ShrineBlockEntity heart) {
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        heart.addBuilding(new Building("dawi_engineers_guild", new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 5, at.getY() + 4, at.getZ() + 5)), false);
    }

    private static Husk still(GameTestHelper h, BlockPos at) {
        Husk z = h.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        return z;
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theGuildRaisesFourEngineersAndBuildsOneMech(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        heart.addCurrency(100, "the test");
        h.assertTrue(!heart.ranks().raiseEngineer(h.getLevel(), heart) && !heart.ranks().buildMech(h.getLevel(), heart), "the Guild's came up with no Guild");
        guild(h, heart);
        int raised = 0;
        while (heart.ranks().raiseEngineer(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= Ranks.ENGINEER_EACH, "more than " + Ranks.ENGINEER_EACH + " Engineers came up");
        }
        h.assertTrue(raised == Ranks.ENGINEER_EACH, "the Guild raised " + raised + " Engineers");
        int gold = heart.currency();
        h.assertTrue(heart.ranks().buildMech(h.getLevel(), heart) && !heart.ranks().buildMech(h.getLevel(), heart), "not exactly one mech was built");
        h.assertTrue(gold - heart.currency() == Ranks.MECH_GOLD && Ranks.count(h.getLevel(), heart, KRegistry.SLAYER_MECH.get()) == 1,
                "the mech cost " + (gold - heart.currency()) + " gold; on the roll " + Ranks.count(h.getLevel(), heart, KRegistry.SLAYER_MECH.get()));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void anEngineersShotScattersAndHisGunBlowsUpNeverToHisDeath(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        EngineerSlayer e = h.spawn(KRegistry.ENGINEER_SLAYER.get(), new BlockPos(3, 1, 4));
        e.bindTo(heart);
        e.setNoAi(true);
        Husk mark = still(h, new BlockPos(14, 1, 4)), beside = still(h, new BlockPos(15, 1, 5)), far = still(h, new BlockPos(14, 1, 16));
        h.runAfterDelay(2, () -> {
            float m0 = mark.getHealth(), b0 = beside.getHealth(), f0 = far.getHealth();
            e.forceOverload = 1;
            e.shoot(h.getLevel(), mark);
            h.assertTrue(e.scatteredForTests >= 1 && e.overloadsForTests == 1, "scattered onto " + e.scatteredForTests + ", overloaded " + e.overloadsForTests);
            h.assertTrue(mark.getHealth() < m0 && beside.getHealth() < b0 && beside.isOnFire(), "the mark " + mark.getHealth() + "/" + m0
                    + ", beside it " + beside.getHealth() + "/" + b0 + (beside.isOnFire() ? " burning" : " not burning"));
            h.assertTrue(far.getHealth() == f0, "the burst reached a husk 12 blocks off");
            h.assertTrue(e.getHealth() < e.getMaxHealth(), "the backlash didn't singe him");
            e.setHealth(2);
            e.overload(h.getLevel(), mark);
            h.assertTrue(e.isAlive() && e.getHealth() >= 1, "the backlash killed him: " + e.getHealth());
            var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(8, 1, 12));
            golem.setNoAi(true);
            e.setHealth(e.getMaxHealth());
            h.assertTrue(e.shotDamageOn(golem) > EngineerSlayer.SHOT * 1.5f, "a Slayer's share on a large foe isn't in his shot: " + e.shotDamageOn(golem));
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theMechRunsHotAndScaldsItselfPastTheRedLine(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        SlayerMech m = h.spawn(KRegistry.SLAYER_MECH.get(), new BlockPos(6, 1, 6));
        m.bindTo(heart);
        m.setNoAi(true);
        h.assertTrue(m.isStubborn(), "the mech isn't stubborn");
        m.heatForTests(SlayerMech.HOT + 5);
        m.boil(h.getLevel());
        h.assertTrue(m.runningHot() && Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - SlayerMech.DAMAGE * (1 + SlayerMech.HOT_DAMAGE)) < 1e-6,
                "running hot: " + m.runningHot() + ", its blow " + m.getAttributeValue(Attributes.ATTACK_DAMAGE));
        float hp = m.getHealth();
        m.heatForTests(SlayerMech.RED_LINE + 10);
        m.boil(h.getLevel());
        h.assertTrue(m.getHealth() < hp, "past the red line the boiler didn't scald it: " + m.getHealth() + "/" + hp);
        m.heatForTests(0);
        m.boil(h.getLevel());
        h.assertTrue(!m.runningHot() && Math.abs(m.getAttributeValue(Attributes.ATTACK_DAMAGE) - SlayerMech.DAMAGE) < 1e-6, "cooled, it still runs hot");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theMechBurstsAndItsPilotFightsOn(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        heart.addCurrency(100, "the test");
        guild(h, heart);
        h.assertTrue(heart.ranks().buildMech(h.getLevel(), heart), "no mech built");
        SlayerMech m = h.getLevel().getEntitiesOfClass(SlayerMech.class, new net.minecraft.world.phys.AABB(h.absolutePos(HEART)).inflate(24)).get(0);
        h.runAfterDelay(20, () -> {                              // up out of the ground first
            m.kill(h.getLevel());
            Slayer pilot = m.pilotForTests;
            h.assertTrue(pilot != null && pilot.isAlive() && pilot.tier() == Slayer.Tier.GIANT && pilot.settlement() == heart, "the pilot: " + pilot);
            h.assertTrue(Ranks.count(h.getLevel(), heart, KRegistry.SLAYER_MECH.get()) == 0 && Ranks.count(h.getLevel(), heart, KRegistry.GIANT_SLAYER.get()) == 1,
                    "the roll: mechs " + Ranks.count(h.getLevel(), heart, KRegistry.SLAYER_MECH.get()) + ", Giant Slayers "
                            + Ranks.count(h.getLevel(), heart, KRegistry.GIANT_SLAYER.get()));
            h.assertTrue(heart.ranks().buildMech(h.getLevel(), heart), "the Guild didn't build the hold a new mech once the old one burst");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void anEngineerSlayerBeatsAVindicatorAndTwoZombies(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        EngineerSlayer me = h.spawn(KRegistry.ENGINEER_SLAYER.get(), HEART.offset(-6, 0, 0));
        me.bindTo(heart);
        Vindicator v = h.spawn(EntityType.VINDICATOR, HEART.offset(6, 0, 0));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        v.setPersistenceRequired();
        Zombie[] z = {h.spawn(EntityType.ZOMBIE, HEART.offset(6, 0, -3)), h.spawn(EntityType.ZOMBIE, HEART.offset(6, 0, 3))};
        for (Zombie each : z) {
            each.setPersistenceRequired();
        }
        List<LivingEntity> foes = List.of(v, z[0], z[1]);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the Engineer Slayer fell");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth());
                    }
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: the Engineer Slayer beat an axe vindicator and two zombies with {}/{} health left, {} overloads",
                        me.getHealth(), me.getMaxHealth(), me.overloadsForTests))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void theSlayerMechBeatsARavagerAndTwoVindicators(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        SlayerMech me = h.spawn(KRegistry.SLAYER_MECH.get(), HEART.offset(-5, 0, 0));
        me.bindTo(heart);
        var r = h.spawn(EntityType.RAVAGER, HEART.offset(5, 0, 0));
        r.setPersistenceRequired();
        Vindicator[] v = {h.spawn(EntityType.VINDICATOR, HEART.offset(5, 0, -4)), h.spawn(EntityType.VINDICATOR, HEART.offset(5, 0, 4))};
        for (Vindicator each : v) {
            each.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            each.setPersistenceRequired();
        }
        List<LivingEntity> foes = List.of(r, v[0], v[1]);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the Slayer mech burst");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth());
                    }
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: the Slayer mech beat a ravager and two axe vindicators with {}/{} health left, heat {}",
                        me.getHealth(), me.getMaxHealth(), me.heat()))
                .thenSucceed();
    }
}
