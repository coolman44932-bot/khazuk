package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Slayer;
import claude.khazuk.Warrior;
import claude.khazuk.god.AncestorPowers;
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
 * 0.14.2, the Slayers (Slayer, hold/Ranks, the Shrine of Grimnir):
 * <ul>
 * <li>the Shrine of Grimnir raises four Troll Slayers out of food, and none without it, on the Shrine's own clock;</li>
 * <li>a Slayer's blow on a large foe lands harder by his tier's share, and the nearer to death he is the harder it lands;</li>
 * <li>a Troll Slayer who slays a large foe is a Giant Slayer; a Giant Slayer who slays a monster is the hold's Dragon Slayer, and there is only
 * one;</li>
 * <li>Grimnir's Fury burns twice as long on a Slayer as on a Warrior;</li>
 * <li>the floors: a Troll Slayer beats an axe vindicator and two zombies, a Giant Slayer an axe vindicator and three, the Dragon Slayer a
 * ravager.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
public class SlayerGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    private static void shrineOfGrimnir(GameTestHelper h, ShrineBlockEntity heart) {
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        heart.addBuilding(new Building("dawi_slayer_shrine", new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 4, at.getY() + 3, at.getZ() + 4)), false);
    }

    private static Slayer slayer(GameTestHelper h, EntityType<Slayer> type, ShrineBlockEntity heart, BlockPos at) {
        Slayer s = h.spawn(type, at);
        s.bindTo(heart);
        heart.ranks().enlist(s);
        return s;
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theShrineOfGrimnirRaisesFourTrollSlayersAndNoneWithoutIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        h.assertTrue(!heart.ranks().raiseSlayer(h.getLevel(), heart), "a Troll Slayer came up with no Shrine of Grimnir");
        shrineOfGrimnir(h, heart);
        for (int s = 0; s < Ranks.ELITE_SECONDS; s++) {
            heart.ranks().second(h.getLevel(), heart);
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, KRegistry.TROLL_SLAYER.get()) == 1, "a minute of the clock raised "
                + Ranks.count(h.getLevel(), heart, KRegistry.TROLL_SLAYER.get()) + " Troll Slayers, not one");
        int food = heart.food(), raised = 1;
        while (heart.ranks().raiseSlayer(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= Ranks.SLAYER_EACH, "more than " + Ranks.SLAYER_EACH + " came up");
        }
        h.assertTrue(raised == Ranks.SLAYER_EACH && food - heart.food() == (raised - 1) * Ranks.SLAYER_COST, "raised " + raised + " for "
                + (food - heart.food()) + " food");
        for (Slayer s : h.getLevel().getEntitiesOfClass(Slayer.class, new AABB(h.absolutePos(HEART)).inflate(20))) {
            h.assertTrue(s.isStubborn() && s.tier() == Slayer.Tier.TROLL, "a raised Slayer: " + s.tier() + ", stubborn " + s.isStubborn());
        }
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void aSlayersBlowOnALargeFoeLandsHarderAndHarderNearDeath(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Slayer troll = slayer(h, KRegistry.TROLL_SLAYER.get(), heart, new BlockPos(4, 1, 4));
        Slayer dragon = slayer(h, KRegistry.DRAGON_SLAYER.get(), heart, new BlockPos(4, 1, 8));
        troll.setNoAi(true);
        dragon.setNoAi(true);
        Zombie small = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 4));
        var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(14, 1, 10));
        small.setNoAi(true);
        golem.setNoAi(true);
        h.assertTrue(!Slayer.large(small) && Slayer.large(golem), "large: a zombie " + Slayer.large(small) + ", an iron golem " + Slayer.large(golem));
        h.assertTrue(Math.abs(troll.slaying(small) - 1) < 1e-6, "a whole Troll Slayer's blow on a zombie is x" + troll.slaying(small));
        h.assertTrue(Math.abs(troll.slaying(golem) - (1 + Slayer.TROLL_LARGE)) < 1e-6 && Math.abs(dragon.slaying(golem) - (1 + Slayer.DRAGON_LARGE)) < 1e-6,
                "on an iron golem: the Troll Slayer x" + troll.slaying(golem) + ", the Dragon Slayer x" + dragon.slaying(golem));
        troll.setHealth(troll.getMaxHealth() / 2);
        h.assertTrue(Math.abs(troll.slaying(small) - (1 + Slayer.DEATHWISH / 2)) < 1e-6, "at half his health the death-wish is x" + troll.slaying(small));
        float hp = golem.getHealth();
        h.assertTrue(troll.doHurtTarget(h.getLevel(), golem) && troll.largeBlowsForTests == 1 && golem.getHealth() < hp, "the blow on the golem: "
                + troll.largeBlowsForTests + " large blows, the golem at " + golem.getHealth() + "/" + hp);
        h.assertTrue(Math.abs(troll.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - Slayer.TROLL_DAMAGE) < 1e-6,
                "the slaying stayed on his blow");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aTrollSlayerWhoSlaysALargeFoeIsAGiantSlayer(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Slayer troll = slayer(h, KRegistry.TROLL_SLAYER.get(), heart, new BlockPos(6, 1, 6));
        troll.setNoAi(true);
        var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(9, 1, 6));
        golem.setNoAi(true);
        golem.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(troll), 1000f);
        h.assertTrue(troll.earned() == Slayer.Tier.GIANT, "slaying an iron golem earned " + troll.earned());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(troll.isRemoved(), "the Troll Slayer hasn't risen"))
                .thenExecute(() -> {
                    List<Slayer> s = h.getLevel().getEntitiesOfClass(Slayer.class, new AABB(troll.blockPosition()).inflate(2));
                    h.assertTrue(s.size() == 1 && s.get(0).tier() == Slayer.Tier.GIANT && s.get(0).settlement() == heart, "where he stood: " + s);
                    h.assertTrue(Ranks.count(h.getLevel(), heart, KRegistry.TROLL_SLAYER.get()) == 0
                            && Ranks.count(h.getLevel(), heart, KRegistry.GIANT_SLAYER.get()) == 1, "the roll: Troll Slayers "
                            + Ranks.count(h.getLevel(), heart, KRegistry.TROLL_SLAYER.get()) + ", Giant Slayers " + Ranks.count(h.getLevel(), heart, KRegistry.GIANT_SLAYER.get()));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aGiantSlayerWhoSlaysAMonsterIsTheHoldsOneDragonSlayer(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Slayer a = slayer(h, KRegistry.GIANT_SLAYER.get(), heart, new BlockPos(5, 1, 5));
        Slayer b = slayer(h, KRegistry.GIANT_SLAYER.get(), heart, new BlockPos(5, 1, 15));
        a.setNoAi(true);
        b.setNoAi(true);
        a.earnForTests(Slayer.Tier.DRAGON);                    // a monster slain (a ravager would be gone in this peaceful batch: the slaying is told)
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(a.isRemoved(), "the first Giant Slayer didn't rise"))
                .thenExecute(() -> {
                    h.assertTrue(Ranks.count(h.getLevel(), heart, KRegistry.DRAGON_SLAYER.get()) == 1, "no Dragon Slayer on the roll");
                    b.earnForTests(Slayer.Tier.DRAGON);
                })
                .thenIdle(10)
                .thenExecute(() -> h.assertTrue(!b.isRemoved() && b.tier() == Slayer.Tier.GIANT
                        && Ranks.count(h.getLevel(), heart, KRegistry.DRAGON_SLAYER.get()) == 1, "a second Dragon Slayer rose: the roll has "
                        + Ranks.count(h.getLevel(), heart, KRegistry.DRAGON_SLAYER.get())))
                .thenSucceed();
    }

    /** What he earned waits for his hold's Shrine (a Slayer far off, its chunk unloaded); risen, Grimnir's Fury goes on burning in him. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aSlayersEarningWaitsForHisHoldAndHisFuryRisesWithHim(GameTestHelper h) {
        Slayer lone = h.spawn(KRegistry.TROLL_SLAYER.get(), new BlockPos(18, 1, 18));   // of no hold in reach
        lone.setNoAi(true);
        lone.earnForTests(Slayer.Tier.GIANT);
        ShrineBlockEntity heart = hold(h);
        Slayer troll = slayer(h, KRegistry.TROLL_SLAYER.get(), heart, new BlockPos(6, 1, 6));
        troll.setNoAi(true);
        AncestorPowers.rage(troll, h.getLevel().getGameTime() + 400);
        troll.earnForTests(Slayer.Tier.GIANT);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(troll.isRemoved(), "the Troll Slayer hasn't risen"))
                .thenExecute(() -> {
                    h.assertTrue(!lone.isRemoved() && lone.earned() == Slayer.Tier.GIANT, "a Slayer with no hold in reach lost what he earned: " + lone.earned());
                    List<Slayer> s = h.getLevel().getEntitiesOfClass(Slayer.class, new AABB(troll.blockPosition()).inflate(2), x -> x.tier() == Slayer.Tier.GIANT);
                    h.assertTrue(s.size() == 1 && AncestorPowers.furious(s.get(0)) && s.get(0).furyUp(), "risen, the Fury: " + s);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void aMonsterIsWhatMakesADragonSlayer(GameTestHelper h) {
        var golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(4, 1, 4));   // 100 health: a monster by its might
        var cow = h.spawn(EntityType.COW, new BlockPos(8, 1, 4));
        h.assertTrue(Slayer.monster(golem) && !Slayer.monster(cow), "a monster: an iron golem " + Slayer.monster(golem) + ", a cow " + Slayer.monster(cow));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 900, batch = "khazuk")
    public static void grimnirsFuryBurnsTwiceAsLongOnASlayer(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        god.snapTo(h.absolutePos(new BlockPos(8, 1, 8)).getBottomCenter());
        heart.devotion().add(200);
        Slayer s = slayer(h, KRegistry.TROLL_SLAYER.get(), heart, new BlockPos(7, 1, 8));
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(9, 1, 8));
        w.bindTo(heart);
        s.setNoAi(true);
        w.setNoAi(true);
        String no = AncestorPowers.fury(heart, god, h.getLevel());
        h.assertTrue(no == null && AncestorPowers.furious(s) && AncestorPowers.furious(w), "the Fury: " + no + ", Slayer " + AncestorPowers.furious(s)
                + ", Warrior " + AncestorPowers.furious(w));
        h.runAfterDelay(AncestorPowers.FURY_TICKS * 5 / 4, () -> {
            h.assertTrue(!AncestorPowers.furious(w) && AncestorPowers.furious(s), "past the Fury's time: the Warrior furious " + AncestorPowers.furious(w)
                    + ", the Slayer " + AncestorPowers.furious(s));
            h.succeed();
        });
    }

    private static void beats(GameTestHelper h, EntityType<Slayer> type, int zombies) {
        ShrineBlockEntity heart = hold(h);
        Slayer me = slayer(h, type, heart, HEART.offset(-3, 0, 0));
        Vindicator v = h.spawn(EntityType.VINDICATOR, HEART.offset(4, 0, 0));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        v.setPersistenceRequired();
        List<LivingEntity> foes = new java.util.ArrayList<>(List.of(v));
        for (int i = 0; i < zombies; i++) {
            Zombie z = h.spawn(EntityType.ZOMBIE, HEART.offset(4, 0, i % 2 == 0 ? -3 - i : 3 + i));
            z.setPersistenceRequired();
            foes.add(z);
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the " + me.getName().getString() + " fell to a vindicator and " + zombies + " zombies");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth());
                    }
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: the {} beat an axe vindicator and {} zombies with {}/{} health left",
                        me.getName().getString(), zombies, me.getHealth(), me.getMaxHealth()))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void aTrollSlayerBeatsAVindicatorAndTwoZombies(GameTestHelper h) {
        beats(h, KRegistry.TROLL_SLAYER.get(), 2);
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void aGiantSlayerBeatsAVindicatorAndThreeZombies(GameTestHelper h) {
        beats(h, KRegistry.GIANT_SLAYER.get(), 3);
    }

    /** The Dragon Slayer's floor: a ravager, a monster, alone. */
    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void theDragonSlayerBeatsARavager(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Slayer me = slayer(h, KRegistry.DRAGON_SLAYER.get(), heart, HEART.offset(-4, 0, 0));
        var r = h.spawn(EntityType.RAVAGER, HEART.offset(4, 0, 0));
        r.setPersistenceRequired();
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the Dragon Slayer fell to a ravager");
                    h.assertTrue(r.isDeadOrDying(), "the ravager still stands at " + r.getHealth() + "; the Dragon Slayer at " + me.getHealth());
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: the Dragon Slayer beat a ravager with {}/{} health left", me.getHealth(), me.getMaxHealth()))
                .thenSucceed();
    }
}
