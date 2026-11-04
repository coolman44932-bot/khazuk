package claude.khazuk.test;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.Brewhouse;
import claude.khazuk.hold.HoldRooms;
import claude.settlementcore.food.Fare;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * D3, gold and ale (hold/Brewhouse): wheat is malt, a Brewery brews malt into ale and a hold without one brews none, the hold drinks its
 * ale down, ale in the barrels makes its Dwarfs hit harder, walk and work faster (and dry, they're as they were), a Treasury makes every
 * gold ore pay more and the hoard grow, the malt and ale come back from a save, and both halls' templates are whole. The halls are given
 * to the hold as buildings (as a dig finishing gives them), far off in the test's corner: what counts is that the hold has them.
 */
@GameTestHolder(Khazuk.MODID)
public class AleGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    /** The hold given a hall of this id, as a finished dig gives it. */
    private static void give(GameTestHelper h, ShrineBlockEntity heart, String room, int x) {
        BlockPos at = h.absolutePos(new BlockPos(x, 1, 1));
        heart.addBuilding(new Building(room, new BoundingBox(at.getX(), at.getY(), at.getZ(), at.getX() + 2, at.getY() + 2, at.getZ() + 2)), false);
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void bothHallsTemplatesAreWhole(GameTestHelper h) {
        for (String id : List.of("dawi_brewery", "dawi_treasury")) {
            Rooms.Room r = HoldRooms.room(id);
            h.assertTrue(r != null, "no room " + id);
            List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), r.id());
            h.assertTrue(cells.size() == r.sizeX() * r.sizeY() * r.sizeZ(), id + "'s template has " + cells.size() + " cells, its box "
                    + r.sizeX() * r.sizeY() * r.sizeZ());
            long solid = cells.stream().filter(e -> !e.state().isAir()).count();
            h.assertTrue(solid > cells.size() / 3, id + " is " + solid + " solid of " + cells.size() + ": did its blocks load?");
        }
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void wheatIsMaltAndOnlyABreweryBrewsIt(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Brewhouse b = heart.brew();
        h.assertTrue(b.addMalt(3 * Brewhouse.MALT_PER_WHEAT + Brewhouse.MALT_PER_HAY) == 12 && b.malt() == 12, "the malt is " + b.malt());
        b.secondsForTests(h.getLevel(), heart, 60);
        h.assertTrue(b.ale() == 0 && b.malt() == 12, "with no Brewery the hold brewed: ale " + b.ale() + ", malt " + b.malt());
        give(h, heart, "dawi_brewery", 2);
        h.assertTrue(heart.has(HoldRooms.BREWERY), "the hold doesn't have its Brewery");
        b.secondsForTests(h.getLevel(), heart, Brewhouse.BREW_SECONDS * 4);
        h.assertTrue(b.malt() == 8 && b.ale() == 4 * Brewhouse.ALE_PER_MALT, "after four brews: malt " + b.malt() + ", ale " + b.ale());
        h.assertTrue(b.addMalt(Brewhouse.MALT_CAP) == Brewhouse.MALT_CAP - 8 && b.malt() == Brewhouse.MALT_CAP, "the malt store isn't capped: " + b.malt());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theHoldDrinksItsAle(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.addFood(200, Fare.COOKED, "the test");
        heart.roundForTests(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(heart.peopleCount() == 3, "the round isn't up: " + heart.peopleCount()))
                .thenExecute(() -> {
                    heart.brew().addAle(5);
                    heart.brew().secondsForTests(h.getLevel(), heart, Brewhouse.DRINK_SECONDS);
                    h.assertTrue(heart.brew().ale() == 4, "three Dwarfs up drank " + (5 - heart.brew().ale()) + " in a minute, not 1");
                })
                .thenSucceed();
    }

    /** Ale in the barrels: his blow, his pace and his work's tempo up; the barrels dry: back as he was. */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "khazuk")
    public static void aleMakesTheDwarfsBetterAndDryTheyAreAsTheyWere(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(14, 1, 12));
        w.bindTo(heart);
        double dmg = w.getAttributeValue(Attributes.ATTACK_DAMAGE), spd = w.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float tempo = w.tempo();
        heart.brew().addAle(10);
        h.runAfterDelay(50, () -> {
            h.assertTrue(w.aleUp(), "no ale in him with ale in the barrels");
            h.assertTrue(Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - dmg - Dawi.ALE_DAMAGE) < 1e-6,
                    "his blow with ale is " + w.getAttributeValue(Attributes.ATTACK_DAMAGE) + ", dry " + dmg);
            h.assertTrue(w.getAttributeValue(Attributes.MOVEMENT_SPEED) > spd * 1.09, "his pace with ale is " + w.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", dry " + spd);
            h.assertTrue(Math.abs(w.tempo() - tempo * Dawi.ALE_TEMPO) < 1e-4, "his tempo with ale is " + w.tempo() + ", dry " + tempo);
            heart.brew().addAle(-10);
        });
        h.runAfterDelay(100, () -> {
            h.assertTrue(!w.aleUp() && w.getAttributeValue(Attributes.ATTACK_DAMAGE) == dmg && w.getAttributeValue(Attributes.MOVEMENT_SPEED) == spd,
                    "dry again he is at blow " + w.getAttributeValue(Attributes.ATTACK_DAMAGE) + ", pace " + w.getAttributeValue(Attributes.MOVEMENT_SPEED));
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void aTreasuryMakesTheOrePayMoreAndTheHoardGrow(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        BlockPos ore = h.absolutePos(new BlockPos(3, 1, 3));
        int g0 = heart.currency();
        heart.takeSpoils(h.getLevel(), ore, net.minecraft.world.level.block.Blocks.GOLD_ORE.defaultBlockState(), null);
        h.assertTrue(heart.currency() - g0 == ShrineBlockEntity.GOLD_PER_ORE, "an ore paid " + (heart.currency() - g0) + " with no Treasury");
        give(h, heart, "dawi_treasury", 6);
        int g1 = heart.currency();
        heart.takeSpoils(h.getLevel(), ore, net.minecraft.world.level.block.Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState(), null);
        h.assertTrue(heart.currency() - g1 == ShrineBlockEntity.GOLD_PER_ORE + ShrineBlockEntity.TREASURY_BONUS,
                "an ore paid " + (heart.currency() - g1) + " with a Treasury");
        heart.addCurrency(200 - heart.currency(), "the test");
        heart.brew().secondsForTests(h.getLevel(), heart, Brewhouse.INTEREST_SECONDS);
        h.assertTrue(heart.currency() == 200 + 200 / Brewhouse.INTEREST_PER, "the hoard of 200 grew to " + heart.currency());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void maltAndAleComeBackFromASave(GameTestHelper h) {
        Brewhouse b = new Brewhouse();
        b.addMalt(17);
        b.addAle(33);
        CompoundTag tag = new CompoundTag();
        b.save(tag);
        Brewhouse back = new Brewhouse();
        back.load(tag);
        h.assertTrue(back.malt() == 17 && back.ale() == 33, "back from the save: malt " + back.malt() + ", ale " + back.ale());
        h.succeed();
    }
}
