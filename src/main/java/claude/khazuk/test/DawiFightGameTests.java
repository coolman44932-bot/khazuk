package claude.khazuk.test;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.settlementcore.food.Fare;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * D2, the Dwarfs' fighting (Warrior, Miner, Dawi): the floors (one Warrior beats three zombies and an axe vindicator, one Miner three
 * zombies, each landing the blows himself), and what makes them: the Warrior's shield turning blows from the front and not from behind,
 * a stubborn Dwarf taking less when he's low, the Miner's pick biting through armour for that blow alone, and a Dwarf mending out of the
 * fight. The floors fight on Normal, in the khazukhostile batch (KhazukGameTests sets it); the rest need no foe to come.
 */
@GameTestHolder(Khazuk.MODID)
public class DawiFightGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.addFood(200, Fare.COOKED, "the test");
        heart.roundForTests(h.getLevel());
        return heart;
    }

    private static <T extends Dawi> List<T> near(GameTestHelper h, Class<T> kind) {
        return h.getLevel().getEntitiesOfClass(kind, new AABB(h.absolutePos(HEART)).inflate(10));
    }

    /** The round's three up, then all but {@code keep} sent off the field (discarded): it fights alone. */
    private static <T extends Dawi> T alone(GameTestHelper h, Class<T> keep) {
        T one = near(h, keep).get(0);
        for (Dawi d : near(h, Dawi.class)) {
            if (d != one) {
                d.discard();
            }
        }
        return one;
    }

    private static Zombie[] zombies(GameTestHelper h, int n) {
        Zombie[] z = new Zombie[n];
        for (int i = 0; i < n; i++) {
            z[i] = h.spawn(EntityType.ZOMBIE, HEART.offset(5, 0, i * 2 - 2));
            z[i].setPersistenceRequired();
        }
        return z;
    }

    private static void beats(GameTestHelper h, Class<? extends Dawi> kind, int count) {
        hold(h);
        Dawi[] me = new Dawi[1];
        Zombie[][] z = new Zombie[1][];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(near(h, Miner.class).size() == 2 && near(h, Warrior.class).size() == 1, "the round isn't up"))
                .thenExecute(() -> {
                    me[0] = alone(h, kind);
                    z[0] = zombies(h, count);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(me[0].isAlive(), "the " + me[0].getName().getString() + " fell to " + count + " zombies");
                    for (Zombie each : z[0]) {
                        h.assertTrue(each.isDeadOrDying(), "a zombie still stands at " + each.getHealth() + "; the Dwarf at " + me[0].getHealth());
                    }
                })
                .thenExecute(() -> {
                    h.assertTrue(me[0].attacksForTests >= count, "the Dwarf landed " + me[0].attacksForTests + " blows");
                    for (Zombie each : z[0]) {
                        h.assertTrue(each.getLastHurtByMob() == me[0], "a zombie's last attacker was " + each.getLastHurtByMob() + ", not the Dwarf");
                    }
                    Khazuk.LOG.info("KHAZUK floor: a {} beat {} zombies with {}/{} health left", me[0].getName().getString(), count,
                            me[0].getHealth(), me[0].getMaxHealth());
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneWarriorBeatsThreeZombies(GameTestHelper h) {
        beats(h, Warrior.class, 3);
    }

    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneMinerBeatsThreeZombies(GameTestHelper h) {
        beats(h, Miner.class, 3);
    }

    /** A raid's hardest foot: one Warrior alone beats a vindicator with an iron axe, landing the blows himself. */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneWarriorBeatsAVindicator(GameTestHelper h) {
        hold(h);
        Warrior[] me = new Warrior[1];
        Vindicator[] v = new Vindicator[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(near(h, Warrior.class).size() == 1, "the round's Warrior isn't up"))
                .thenExecute(() -> {
                    me[0] = alone(h, Warrior.class);
                    v[0] = h.spawn(EntityType.VINDICATOR, HEART.offset(5, 0, 0));
                    v[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                    v[0].setPersistenceRequired();
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(me[0].isAlive(), "the Warrior fell to a vindicator (it's at " + v[0].getHealth() + ")");
                    h.assertTrue(v[0].isDeadOrDying(), "the vindicator still stands at " + v[0].getHealth() + "; the Warrior at " + me[0].getHealth());
                })
                .thenExecute(() -> {
                    h.assertTrue(v[0].getLastHurtByMob() == me[0], "the vindicator's last attacker was " + v[0].getLastHurtByMob());
                    Khazuk.LOG.info("KHAZUK floor: a Warrior beat a vindicator with {}/{} health left, {} blows turned by his shield",
                            me[0].getHealth(), me[0].getMaxHealth(), me[0].blowsTurnedForTests);
                })
                .thenSucceed();
    }

    /**
     * A still Warrior facing his own way, and a still zombie {@code ahead} (or behind) him to strike from. The blows are indirect magic: a
     * blow from a place that armour doesn't touch (armour's cut isn't linear in the blow: the review of 08527b2), so what he takes is
     * exactly what the shield and his grit let through.
     */
    private static Warrior standing(GameTestHelper h, BlockPos at) {
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), at);
        w.setNoAi(true);
        w.setYRot(0);
        w.yBodyRot = 0;
        w.setYHeadRot(0);
        return w;
    }

    private static Zombie striker(GameTestHelper h, Warrior w, boolean ahead) {
        Vec3 f = Vec3.directionFromRotation(0, w.yBodyRot).scale(ahead ? 2 : -2);
        Zombie z = h.spawn(EntityType.ZOMBIE, BlockPos.containing(w.position().add(f)).subtract(h.absolutePos(BlockPos.ZERO)));
        z.setNoAi(true);
        return z;
    }

    /** The same blow from in front does {@value Warrior#SHIELD_MELEE} of what it does from behind; a shot from behind is never stopped. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theShieldTurnsBlowsFromTheFrontOnly(GameTestHelper h) {
        Warrior front = standing(h, new BlockPos(6, 1, 6)), back = standing(h, new BlockPos(16, 1, 16));
        Zombie a = striker(h, front, true), b = striker(h, back, false);
        h.assertTrue(front.shieldFaces(a.position()) && !back.shieldFaces(b.position()), "the strikers aren't where the test put them");
        float fullFront = front.getHealth(), fullBack = back.getHealth();
        front.hurtServer(h.getLevel(), h.getLevel().damageSources().indirectMagic(a, a), 10);
        back.hurtServer(h.getLevel(), h.getLevel().damageSources().indirectMagic(b, b), 10);
        float tookFront = fullFront - front.getHealth(), tookBack = fullBack - back.getHealth();
        h.assertTrue(tookBack > 0 && Math.abs(tookFront - tookBack * Warrior.SHIELD_MELEE) < 0.05,
                "from the front he took " + tookFront + ", from behind " + tookBack + ": not " + Warrior.SHIELD_MELEE + " of it");
        h.assertTrue(front.blowsTurnedForTests == 1 && back.blowsTurnedForTests == 0, "the blows turned: " + front.blowsTurnedForTests + ", " + back.blowsTurnedForTests);
        h.succeed();
    }

    /** Low on health a Dwarf is stubborn: the same blow does {@value Dawi#STUBBORN_TAKEN} of what it does to him whole. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aStubbornDwarfTakesLess(GameTestHelper h) {
        Warrior whole = standing(h, new BlockPos(6, 1, 6)), low = standing(h, new BlockPos(16, 1, 16));
        Zombie a = striker(h, whole, false), b = striker(h, low, false);
        low.setHealth((float) Warrior.MAX_HEALTH * 0.25f);
        h.assertTrue(low.isStubborn() && !whole.isStubborn(), "stubborn: " + low.isStubborn() + ", " + whole.isStubborn());
        float w0 = whole.getHealth(), l0 = low.getHealth();
        whole.hurtServer(h.getLevel(), h.getLevel().damageSources().indirectMagic(a, a), 6);
        low.hurtServer(h.getLevel(), h.getLevel().damageSources().indirectMagic(b, b), 6);
        float tookWhole = w0 - whole.getHealth(), tookLow = l0 - low.getHealth();
        h.assertTrue(tookWhole > 0 && Math.abs(tookLow - tookWhole * Dawi.STUBBORN_TAKEN) < 0.05,
                "whole he took " + tookWhole + ", low " + tookLow + ": not " + Dawi.STUBBORN_TAKEN + " of it");
        h.succeed();
    }

    /** The Miner's pick bites through armour: an iron-clad zombie takes more from his blow than from the same blow without the bite. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void thePickBitesThroughArmourForThatBlowAlone(GameTestHelper h) {
        Miner m = h.spawn(KRegistry.MINER.get(), new BlockPos(12, 1, 12));
        m.setNoAi(true);
        Zombie[] z = new Zombie[2];
        for (int i = 0; i < 2; i++) {
            z[i] = h.spawn(EntityType.ZOMBIE, new BlockPos(10 + i * 4, 1, 14));
            z[i].setNoAi(true);
            z[i].setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            z[i].setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        }
        double bite = Miner.pickBite(z[0]);
        h.assertTrue(bite == Math.min(Miner.PICK_BITE_MAX, z[0].getArmorValue() * Miner.PICK_BITE) && bite > 0,
                "the bite against armour " + z[0].getArmorValue() + " is " + bite);
        float a0 = z[0].getHealth(), b0 = z[1].getHealth();
        m.doHurtTarget(h.getLevel(), z[0]);
        z[1].hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(m), (float) Miner.DAMAGE);
        float bitten = a0 - z[0].getHealth(), plain = b0 - z[1].getHealth();
        h.assertTrue(bitten > plain + 0.5f, "his blow took " + bitten + ", the same blow without the bite " + plain);
        h.assertTrue(m.getAttributeValue(Attributes.ATTACK_DAMAGE) == Miner.DAMAGE, "the bite stayed on him: " + m.getAttributeValue(Attributes.ATTACK_DAMAGE));
        h.succeed();
    }

    /** Out of the fight a hurt Dwarf mends, a health a second. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "khazuk")
    public static void aDwarfMendsOutOfTheFight(GameTestHelper h) {
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(12, 1, 12));
        w.setHealth(20);
        h.runAfterDelay(100, () -> {
            h.assertTrue(w.getHealth() >= 23, "he mended to " + w.getHealth() + " in 5 s, from 20");
            h.succeed();
        });
    }
}
