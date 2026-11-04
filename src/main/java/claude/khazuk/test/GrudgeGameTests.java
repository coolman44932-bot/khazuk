package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.BookOfGrudges;
import claude.khazuk.hold.Grudges;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * D4, the Book of Grudges (hold/Grudges, hold/GrudgeEvents, BookOfGrudges): a Dwarf slain writes a grudge against his slayer's kind and
 * another death adds to it; the hold's own kills settle it, paid in gold and ale, and from then on its Dwarfs bear it (they strike that
 * kind harder, and no other); a named foe is one grudge, settled by its own death; a creeper's blast on one of the hold's buildings is
 * written; the Dammaz Kron's pages show it; the Book comes back from a save.
 */
@GameTestHolder(Khazuk.MODID)
public class GrudgeGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    /** A still Warrior of the hold. */
    private static Warrior dwarf(GameTestHelper h, ShrineBlockEntity heart, BlockPos at) {
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), at);
        w.setNoAi(true);
        w.bindTo(heart);
        return w;
    }

    private static Zombie still(GameTestHelper h, BlockPos at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        return z;
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aDwarfSlainWritesAGrudgeAndAnotherDeathAddsToIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Zombie z = still(h, new BlockPos(4, 1, 4));
        for (int i = 0; i < 2; i++) {
            Warrior w = dwarf(h, heart, new BlockPos(6 + i * 3, 1, 4));
            w.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(z), 1000f);
            h.assertTrue(w.isDeadOrDying(), "the Warrior didn't fall");
        }
        Grudges.Grudge g = heart.grudges().named("Zombies");
        h.assertTrue(g != null && !g.settled() && g.need() == 2 * Grudges.PER_WRONG, "the grudge against the Zombies: "
                + (g == null ? "not written" : g.need() + " to fall, settled " + g.settled()));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theHoldsKillsSettleItAndItsDwarfsBearIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior w = dwarf(h, heart, new BlockPos(6, 1, 6));
        Zombie wrong = still(h, new BlockPos(4, 1, 4));
        Grudges.Grudge g = heart.grudges().wronged(h.getLevel(), heart, wrong, "slew a Miner");
        wrong.discard();
        // a blow before the grudge is settled, to measure the grudgebearer's by
        Zombie before = still(h, new BlockPos(8, 1, 8));
        float b0 = before.getHealth();
        w.doHurtTarget(h.getLevel(), before);
        float plain = b0 - before.getHealth();
        before.discard();
        int gold = heart.currency(), ale = heart.brew().ale();
        for (int i = 0; i < Grudges.PER_WRONG; i++) {
            h.assertTrue(!g.settled(), "settled after " + i + " of " + Grudges.PER_WRONG);
            Zombie z = still(h, new BlockPos(3 + i * 2, 1, 16));
            z.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(w), 1000f);
        }
        h.assertTrue(g.settled() && g.have() == Grudges.PER_WRONG, "the grudge: " + g.have() + " of " + g.need() + ", settled " + g.settled());
        h.assertTrue(heart.currency() - gold == Grudges.GOLD_BASE + Grudges.GOLD_PER * Grudges.PER_WRONG
                && heart.brew().ale() - ale == Grudges.ALE_REWARD, "paid " + (heart.currency() - gold) + " gold and " + (heart.brew().ale() - ale) + " ale");
        Zombie after = still(h, new BlockPos(8, 1, 8));
        Skeleton other = h.spawn(EntityType.SKELETON, new BlockPos(10, 1, 8));
        other.setNoAi(true);
        h.assertTrue(heart.grudges().bears(after) && !heart.grudges().bears(other), "the Dawi bear the grudge against: zombies "
                + heart.grudges().bears(after) + ", skeletons " + heart.grudges().bears(other));
        float a0 = after.getHealth();
        w.doHurtTarget(h.getLevel(), after);
        float bearing = a0 - after.getHealth();
        h.assertTrue(Math.abs(bearing - plain * (1 + Grudges.BEARER_PERCENT / 100f)) < 0.05f, "his blow on a zombie: " + plain + " before, "
                + bearing + " bearing the grudge");
        h.assertTrue(w.getAttributeValue(Attributes.ATTACK_DAMAGE) == Warrior.DAMAGE, "the grudge's bonus stayed on him: " + w.getAttributeValue(Attributes.ATTACK_DAMAGE));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aNamedFoeIsOneGrudgeSettledByItsDeath(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Zombie boss = still(h, new BlockPos(4, 1, 4));
        boss.setCustomName(Component.literal("Gutrot"));
        Warrior w = dwarf(h, heart, new BlockPos(7, 1, 4));
        w.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(boss), 1000f);
        Grudges.Grudge g = heart.grudges().named("Gutrot");
        h.assertTrue(g != null && g.need() == 1 && heart.grudges().named("Zombies") == null, "the grudge against Gutrot: "
                + (g == null ? "not written" : g.need() + " to fall"));
        Warrior avenger = dwarf(h, heart, new BlockPos(9, 1, 4));
        Zombie someOther = still(h, new BlockPos(11, 1, 4));
        someOther.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(avenger), 1000f);
        h.assertTrue(!g.settled(), "another zombie's death settled Gutrot's grudge");
        boss.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(avenger), 1000f);
        h.assertTrue(g.settled(), "Gutrot's death didn't settle the grudge against him");
        h.succeed();
    }

    /** A creeper's blast that takes stone out of one of the hold's buildings writes a grudge against the Creepers. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aBlastOnTheHoldsHallIsWritten(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        BlockPos lo = new BlockPos(3, 1, 3), hi = new BlockPos(7, 3, 7);
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
            h.setBlock(p, Blocks.STONE_BRICKS);
        }
        BlockPos a = h.absolutePos(lo), b = h.absolutePos(hi);
        heart.addBuilding(new Building("dawi_barracks", BoundingBox.fromCorners(a, b)), false);
        // a Shrine set down this tick is in the level's list of holds from its first load (the laptop's run of 6e74d54: a blast on the same
        // tick found no hold)
        h.runAfterDelay(2, () -> {
            Creeper c = h.spawn(EntityType.CREEPER, new BlockPos(5, 4, 5));
            c.setNoAi(true);
            h.getLevel().explode(c, c.getX(), c.getY(), c.getZ(), 3f, Level.ExplosionInteraction.MOB);
            Grudges.Grudge g = heart.grudges().named("Creepers");
            h.assertTrue(g != null && g.need() == Grudges.PER_WRONG && g.why().contains("Barracks"), "the blast's grudge: "
                    + (g == null ? "not written" : g.need() + ", " + g.why()));
            h.succeed();
        });
    }

    /** One foe at the hold again and again (a stranger's every pick-stroke) is one grudge, owing one death; kinds are named in English plurals. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void oneFoeAgainIsOneGrudgeAndKindsArePlural(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Zombie boss = still(h, new BlockPos(4, 1, 4));
        boss.setCustomName(Component.literal("Gutrot"));
        for (int i = 0; i < 5; i++) {
            heart.grudges().wronged(h.getLevel(), heart, boss, "broke the stone of the Barracks");
        }
        Grudges.Grudge g = heart.grudges().named("Gutrot");
        h.assertTrue(g != null && g.need() == 1 && heart.grudges().grudges().size() == 1, "five wrongs by Gutrot: " + heart.grudges().grudges().size()
                + " grudges, the first owing " + (g == null ? "?" : g.need()));
        h.assertTrue(heart.grudges().toldForTests == 1, "five wrongs by Gutrot in one tick told its god " + heart.grudges().toldForTests
                + " times, not once (the review of 8decc4d: the quiet window had no proof)");
        Zombie grunt = still(h, new BlockPos(6, 1, 4));
        heart.grudges().wronged(h.getLevel(), heart, grunt, "slew a Miner");
        heart.grudges().wronged(h.getLevel(), heart, grunt, "slew a Warrior");
        h.assertTrue(heart.grudges().toldForTests == 3, "two wrongs by the Zombies told its god " + (heart.grudges().toldForTests - 1)
                + " times: a kind's grudge is told every time");
        h.assertTrue(Grudges.plural("Witch").equals("Witches") && Grudges.plural("Wolf").equals("Wolves") && Grudges.plural("Silverfish").equals("Silverfish")
                && Grudges.plural("Zombie").equals("Zombies") && Grudges.plural("Breeze").equals("Breezes"), "plurals: " + Grudges.plural("Witch")
                + ", " + Grudges.plural("Wolf") + ", " + Grudges.plural("Silverfish"));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theDammazKronReadsTheBookAndItComesBackFromASave(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Zombie z = still(h, new BlockPos(4, 1, 4));
        heart.grudges().wronged(h.getLevel(), heart, z, "slew Thorin");
        ItemStack book = new ItemStack(KRegistry.DAMMAZ_KRON.get());
        BookOfGrudges.write(book, heart);
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        h.assertTrue(content != null && content.pages().size() >= 2, "the Dammaz Kron has " + (content == null ? "no" : content.pages().size()) + " pages");
        String text = content.pages().get(1).raw().getString();
        h.assertTrue(text.contains("Zombies") && text.contains("slew Thorin") && text.contains("0 of 5"), "its grudge page reads: " + text);
        CompoundTag tag = new CompoundTag();
        heart.grudges().save(tag);
        Grudges back = new Grudges();
        back.load(tag);
        Grudges.Grudge g = back.named("Zombies");
        h.assertTrue(g != null && g.need() == Grudges.PER_WRONG && g.why().equals("slew Thorin") && !g.settled(), "back from the save: " + g);
        h.succeed();
    }
}
