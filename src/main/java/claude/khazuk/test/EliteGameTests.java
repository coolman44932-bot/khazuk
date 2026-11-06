package claude.khazuk.test;

import claude.khazuk.Cinderblast;
import claude.khazuk.Hammerer;
import claude.khazuk.Ironbreaker;
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
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * 0.14, the Ironbreakers and the Hammerers (Ironbreaker, Hammerer, Cinderblast, hold/Ranks):
 * <ul>
 * <li>a Mine raises four Ironbreakers posted on guard in it, a King's Throne Room four Hammerers at its post, and neither comes without its
 * room;</li>
 * <li>gromril turns more of a blow from the front than a Warrior's shield, and fire bites an Ironbreaker half;</li>
 * <li>a Cinderblast burns and blasts the hold's foes round it and spares the hold's Dawi; an Ironbreaker throws one at a foe in range and not
 * at one on top of him;</li>
 * <li>a Hammerer's blow rings out on the foes beside its mark and not on one further off;</li>
 * <li>the King's Guard: Hammerers come to their god's side in the hold, go for what struck him, and guard him (a stranger's don't);</li>
 * <li>the floors, above the Longbeards': each beats an axe vindicator and three zombies at once.</li>
 * </ul>
 */
@GameTestHolder(Khazuk.MODID)
public class EliteGameTests {

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

    private static Husk still(GameTestHelper h, BlockPos at) {
        Husk z = h.spawn(EntityType.HUSK, at);                    // a husk: the sun doesn't burn it, so any fire on it is the bomb's
        z.setNoAi(true);
        z.setPersistenceRequired();
        return z;
    }

    private static Building room(GameTestHelper h, String id, BlockPos rel, int size) {
        BlockPos at = h.absolutePos(rel);
        return new Building(id, new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + size, at.getY() + 3, at.getZ() + size));
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theMineRaisesFourIronbreakersPostedInItAndNoneWithoutIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        for (int s = 0; s < Ranks.ELITE_SECONDS * 2; s++) {
            heart.ranks().second(h.getLevel(), heart);
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, Ironbreaker.class) == 0, "Ironbreakers came up with no Mine");
        Building mine = room(h, "dawi_mine", new BlockPos(2, 1, 2), 4);
        heart.addBuilding(mine, false);
        int food = heart.food(), raised = 0;
        while (heart.ranks().raiseIronbreaker(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= Ranks.IRONBREAKER_EACH, "more than " + Ranks.IRONBREAKER_EACH + " came up");
        }
        h.assertTrue(raised == Ranks.IRONBREAKER_EACH && Ranks.count(h.getLevel(), heart, Ironbreaker.class) == Ranks.IRONBREAKER_EACH,
                "the Mine keeps " + Ranks.count(h.getLevel(), heart, Ironbreaker.class));
        h.assertTrue(food - heart.food() == raised * Ranks.ELITE_COST, "they cost " + (food - heart.food()) + " food");
        BlockPos post = Ranks.middleOf(mine);
        for (Ironbreaker ib : h.getLevel().getEntitiesOfClass(Ironbreaker.class, new net.minecraft.world.phys.AABB(h.absolutePos(HEART)).inflate(4))) {
            h.assertTrue(ib.getMode() == SettlerMob.Mode.GUARD && post.equals(ib.guardPos()), "an Ironbreaker's orders: " + ib.getMode() + " at "
                    + ib.guardPos() + ", not on guard in the Mine at " + post);
        }
        h.assertTrue(Ranks.count(h.getLevel(), heart, Hammerer.class) == 0, "a Mine raised Hammerers");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theThroneRoomRaisesFourHammerersAtItsPostAndNoneWithoutIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        h.assertTrue(!heart.ranks().raiseHammerer(h.getLevel(), heart), "a Hammerer came up with no Throne Room");
        Building throne = room(h, "dawi_throne_room", new BlockPos(2, 1, 16), 4);
        heart.addBuilding(throne, false);
        int raised = 0;
        while (heart.ranks().raiseHammerer(h.getLevel(), heart)) {
            raised++;
            h.assertTrue(raised <= Ranks.HAMMERER_EACH, "more than " + Ranks.HAMMERER_EACH + " came up");
        }
        h.assertTrue(raised == Ranks.HAMMERER_EACH, "the Throne Room raised " + raised);
        BlockPos post = Ranks.middleOf(throne);
        for (Hammerer hm : h.getLevel().getEntitiesOfClass(Hammerer.class, new net.minecraft.world.phys.AABB(h.absolutePos(HEART)).inflate(4))) {
            h.assertTrue(post.equals(hm.post()) && hm.getMode() == SettlerMob.Mode.WORK, "a Hammerer's post " + hm.post() + " (" + hm.getMode()
                    + "), not the Throne Room's " + post);
            h.assertTrue(hm.isStubborn(), "a Hammerer at full health isn't stubborn");
        }
        h.succeed();
    }

    /** The Shrine's own clock raises them: a Hammerer within a minute of a Throne Room standing, an Ironbreaker within a minute of a Mine. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theShrinesClockRaisesBothWithinAMinute(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(500, Fare.COOKED, "the test");
        heart.addBuilding(room(h, "dawi_throne_room", new BlockPos(2, 1, 16), 4), false);
        heart.addBuilding(room(h, "dawi_mine", new BlockPos(2, 1, 2), 4), false);
        for (int s = 0; s < Ranks.ELITE_SECONDS; s++) {
            heart.ranks().second(h.getLevel(), heart);
        }
        int hm = Ranks.count(h.getLevel(), heart, Hammerer.class), ib = Ranks.count(h.getLevel(), heart, Ironbreaker.class);
        h.assertTrue(hm == 1 && ib == 1, "a minute of the clock raised " + hm + " Hammerers and " + ib + " Ironbreakers, not one each");
        h.succeed();
    }

    /** Hostile (Normal) batch: the husk's blow, and the fire, must land. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void gromrilTurnsMoreThanAWarriorsShieldAndFireBitesHalf(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Ironbreaker ib = bound(h, KRegistry.IRONBREAKER.get(), heart, new BlockPos(4, 1, 4));
        Warrior w = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(10, 1, 4));
        Husk[] foes = {still(h, new BlockPos(4, 1, 6)), still(h, new BlockPos(10, 1, 6))};
        for (Warrior each : List.of(ib, w)) {
            each.setNoAi(true);
            each.setYRot(0);                                     // facing +Z, the husk in front
            each.setYBodyRot(0);
            each.setYHeadRot(0);
        }
        h.runAfterDelay(2, () -> {
            h.assertTrue(ib.shieldFaces(foes[0].position()) && w.shieldFaces(foes[1].position()), "the husks aren't in front of the shields");
            float ib0 = ib.getHealth(), w0 = w.getHealth();
            ib.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(foes[0]), 20f);
            w.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(foes[1]), 20f);
            float ibLost = ib0 - ib.getHealth(), wLost = w0 - w.getHealth();
            h.assertTrue(ib.blowsTurnedForTests == 1 && ibLost > 0 && ibLost < wLost, "a 20 blow: the Ironbreaker lost " + ibLost + ", the Warrior " + wLost);
            float f0 = ib.getHealth();
            ib.invulnerableTime = 0;
            w.invulnerableTime = 0;
            float wf0 = w.getHealth();
            ib.hurtServer(h.getLevel(), h.getLevel().damageSources().onFire(), 6f);
            w.hurtServer(h.getLevel(), h.getLevel().damageSources().onFire(), 6f);
            h.assertTrue(f0 - ib.getHealth() < (wf0 - w.getHealth()) * 0.75f, "fire: the Ironbreaker lost " + (f0 - ib.getHealth()) + ", the Warrior "
                    + (wf0 - w.getHealth()));
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void aCinderblastBurnsTheHoldsFoesAndSparesItsOwn(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Ironbreaker ib = bound(h, KRegistry.IRONBREAKER.get(), heart, new BlockPos(4, 1, 4));
        ib.setNoAi(true);
        Husk foe = still(h, new BlockPos(14, 1, 4));
        Husk far = still(h, new BlockPos(14, 1, 12));
        Warrior own = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(15, 1, 4));
        own.setNoAi(true);
        h.runAfterDelay(2, () -> {
            Cinderblast b = new Cinderblast(h.getLevel(), ib);
            b.setPos(foe.getX() + 0.5, foe.getY() + 0.2, foe.getZ());
            float hp = foe.getHealth(), ownHp = own.getHealth(), farHp = far.getHealth();
            int n = b.burst(h.getLevel());
            h.assertTrue(n == 1 && foe.getHealth() < hp && foe.isOnFire(), "the bomb struck " + n + "; the husk beside it " + foe.getHealth() + "/" + hp
                    + (foe.isOnFire() ? ", burning" : ", not burning"));
            h.assertTrue(own.getHealth() == ownHp && !own.isOnFire(), "the bomb hurt the hold's own Warrior beside it: " + own.getHealth() + "/" + ownHp);
            h.assertTrue(far.getHealth() == farHp && !far.isOnFire(), "the bomb reached a husk 8 blocks off");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "khazukhostile")
    public static void anIronbreakerBombsAFoeInRangeAndNotOneOnTopOfHim(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Ironbreaker ib = bound(h, KRegistry.IRONBREAKER.get(), heart, new BlockPos(4, 1, 12));
        ib.setNoAi(true);                                        // he throws from aiStep, which runs without his goals: he stays where he is
        Husk close = still(h, new BlockPos(6, 1, 12));
        Husk mark = still(h, new BlockPos(11, 1, 12));
        h.assertTrue(!ib.canBomb(close), "he would bomb a husk 2 blocks off, on top of himself");
        h.assertTrue(ib.canBomb(mark), "he wouldn't bomb a husk 7 blocks off in plain sight");
        close.discard();
        ib.setTarget(mark);
        float hp = mark.getHealth();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(ib.bombsForTests > 0 && (mark.getHealth() < hp || mark.isOnFire()),
                        "thrown " + ib.bombsForTests + ", the husk at " + mark.getHealth() + "/" + hp))
                .thenExecute(() -> h.assertTrue(ib.getHealth() == ib.getMaxHealth(), "his own bomb hurt him"))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazukhostile")
    public static void theHammersBlowRingsOutOnTheFoesBesideItsMark(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Hammerer hm = bound(h, KRegistry.HAMMERER.get(), heart, new BlockPos(4, 1, 4));
        hm.setNoAi(true);
        Husk mark = still(h, new BlockPos(6, 1, 4)), beside = still(h, new BlockPos(7, 1, 5)), far = still(h, new BlockPos(6, 1, 12));
        h.runAfterDelay(2, () -> {
            float b0 = beside.getHealth(), f0 = far.getHealth();
            h.assertTrue(hm.doHurtTarget(h.getLevel(), mark), "the Hammerer's blow didn't land");
            h.assertTrue(hm.rungForTests == 1 && beside.getHealth() < b0, "it rang out on " + hm.rungForTests + "; the husk beside at " + beside.getHealth() + "/" + b0);
            h.assertTrue(far.getHealth() == f0, "it rang out on a husk 8 blocks off");
            h.succeed();
        });
    }

    /**
     * In a batch of its own: in the shared peaceful batch a foe in a neighbouring test's arena (a zombie spawned there) could hold the
     * Hammerer's eye, and with a target he doesn't guard (the laptop's run of cb5d663: once in eight, he never took a step).
     */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "khazukguard")
    public static void theKingsGuardComesToTheGodsSideAndAnswersForHim(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        god.snapTo(h.absolutePos(new BlockPos(18, 1, 12)).getBottomCenter());
        Hammerer hm = bound(h, KRegistry.HAMMERER.get(), heart, new BlockPos(4, 1, 4));
        Hammerer strangers = h.spawn(KRegistry.HAMMERER.get(), new BlockPos(18, 1, 14));   // a Hammerer of no hold
        strangers.setNoAi(true);
        h.assertTrue(!Hammerer.Guard.guarded(god), "a Hammerer of no hold guards the god");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(hm.distanceToSqr(god) <= 9, "the Hammerer is " + Math.sqrt(hm.distanceToSqr(god)) + " from his god; target "
                        + hm.getTarget() + ", goals " + hm.runningGoals() + ", his god in the hold " + (hm.kingInHold() != null) + " (the very god " + (hm.kingInHold() == god)
                        + "), entity id " + hm.getId() + " tickCount " + hm.tickCount + ", nav done " + hm.getNavigation().isDone() + ", onGround " + hm.onGround()))
                .thenExecute(() -> {
                    h.assertTrue(Hammerer.Guard.guarded(god), "the god isn't guarded with his Hammerer beside him");
                    // a cow, no foe of the hold's (a zombie would be one, and gone in this peaceful batch): only the god's word makes it a mark
                    var cow = h.spawn(EntityType.COW, new BlockPos(18, 1, 20));
                    cow.setNoAi(true);
                    god.setLastHurtByMob(cow);                   // it struck the god (a mock god in creative takes no blow, so the strike is told)
                })
                .thenWaitUntil(() -> h.assertTrue(hm.getTarget() instanceof net.minecraft.world.entity.animal.cow.Cow,
                        "the guard goes for " + hm.getTarget() + ", not the god's attacker"))
                .thenExecute(() -> {
                    // one the god struck himself, no foe of the hold's: his own business, not the guard's (the review of 2298b06)
                    hm.setTarget(null);
                    god.setLastHurtByMob(null);
                    var villager = h.spawn(EntityType.VILLAGER, new BlockPos(16, 1, 20));
                    villager.setNoAi(true);
                    god.setLastHurtMob(villager);
                })
                .thenIdle(40)
                .thenExecute(() -> h.assertTrue(!(hm.getTarget() instanceof net.minecraft.world.entity.npc.villager.Villager),
                        "the guard went for a villager the god struck"))
                .thenSucceed();
    }

    /**
     * Followers of either entity-id parity follow (the laptop's flake, diagnosed: a goal ticks only every other tick, on the ticks its id's
     * parity picks, so a move keyed to {@code tickCount % 10} never came for half of them; FollowOwner, KingsGuard and Homeward had it).
     * Two Warriors on follow and two Hammerers on guard, each pair spawned one after the other: ids of both parities.
     */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "khazukguard")
    public static void followersOfEitherIdParityComeToTheirGod(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        var god = h.makeMockServerPlayerInLevel();
        heart.claim(god, "Karak Test");
        god.snapTo(h.absolutePos(new BlockPos(18, 1, 12)).getBottomCenter());
        Warrior w1 = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(3, 1, 3));
        Warrior w2 = bound(h, KRegistry.WARRIOR.get(), heart, new BlockPos(3, 1, 5));
        Hammerer g1 = bound(h, KRegistry.HAMMERER.get(), heart, new BlockPos(3, 1, 19));
        Hammerer g2 = bound(h, KRegistry.HAMMERER.get(), heart, new BlockPos(3, 1, 21));
        w1.setMode(SettlerMob.Mode.FOLLOW);
        w2.setMode(SettlerMob.Mode.FOLLOW);
        h.assertTrue((w1.getId() + w2.getId()) % 2 == 1 && (g1.getId() + g2.getId()) % 2 == 1, "the pairs' ids aren't of both parities: "
                + w1.getId() + "/" + w2.getId() + ", " + g1.getId() + "/" + g2.getId());
        List<Warrior> all = List.of(w1, w2, g1, g2);
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (Warrior w : all) {
                        h.assertTrue(w.distanceToSqr(god) <= 16, "the " + w.getName().getString() + " of id " + w.getId() + " is "
                                + Math.sqrt(w.distanceToSqr(god)) + " from his god; goals " + w.runningGoals());
                    }
                })
                .thenSucceed();
    }

    private static void beatsAVindicatorAndThreeZombies(GameTestHelper h, EntityType<? extends Warrior> type) {
        ShrineBlockEntity heart = hold(h);
        Warrior me = bound(h, type, heart, HEART.offset(-3, 0, 0));
        Vindicator v = h.spawn(EntityType.VINDICATOR, HEART.offset(4, 0, 0));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        v.setPersistenceRequired();
        Zombie[] z = {h.spawn(EntityType.ZOMBIE, HEART.offset(4, 0, -3)), h.spawn(EntityType.ZOMBIE, HEART.offset(4, 0, 3)),
                h.spawn(EntityType.ZOMBIE, HEART.offset(6, 0, 0))};
        for (Zombie each : z) {
            each.setPersistenceRequired();
        }
        List<LivingEntity> foes = List.of(v, z[0], z[1], z[2]);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(me.isAlive(), "the " + me.getName().getString() + " fell to a vindicator and three zombies");
                    for (LivingEntity f : foes) {
                        h.assertTrue(f.isDeadOrDying(), "a " + f.getName().getString() + " still stands at " + f.getHealth() + "; the "
                                + me.getName().getString() + " at " + me.getHealth());
                    }
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: the {} beat an axe vindicator and three zombies with {}/{} health left",
                        me.getName().getString(), me.getHealth(), me.getMaxHealth()))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void anIronbreakerBeatsAVindicatorAndThreeZombies(GameTestHelper h) {
        beatsAVindicatorAndThreeZombies(h, KRegistry.IRONBREAKER.get());
    }

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukhostile")
    public static void aHammererBeatsAVindicatorAndThreeZombies(GameTestHelper h) {
        beatsAVindicatorAndThreeZombies(h, KRegistry.HAMMERER.get());
    }

    /** Both above the Longbeards in what the army book and TWW3 give them most: the Ironbreaker's armour, the Hammerer's blow. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "khazuk")
    public static void bothStandAboveTheLongbeards(GameTestHelper h) {
        h.assertTrue(Ironbreaker.ARMOR > Longbeard.ARMOR && Ironbreaker.TOUGHNESS > Longbeard.TOUGHNESS && Ironbreaker.MAX_HEALTH > Longbeard.MAX_HEALTH,
                "an Ironbreaker's gromril isn't above a Longbeard's plate");
        h.assertTrue(Hammerer.DAMAGE > Longbeard.GREAT_DAMAGE && Hammerer.MAX_HEALTH > Longbeard.MAX_HEALTH && Hammerer.ARMOR > Longbeard.GREAT_ARMOR,
                "a Hammerer isn't above a great-axe Longbeard");
        h.succeed();
    }
}
