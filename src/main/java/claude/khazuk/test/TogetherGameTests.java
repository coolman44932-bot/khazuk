package claude.khazuk.test;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.food.Fare;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Khazuk! and the Waaagh! loaded together (the user keeps all his 26.1 mods loadable at once), registered only in an all-mods run
 * ({@code requires = "waaagh"}); Khazuk! never names a Waaagh! class: the Idol is found by its registry id, its camp read as the engine's
 * Settlement, its Orks as the engine's SettlerMob of the waaagh namespace.
 *
 * <p>A Dwarf hold and an Ork camp 44 blocks apart on the field, each fed, run for three in-game minutes: nothing throws, both raise their
 * people out of their food and keep them up, and they leave each other alone. That last is the design today: the engine's foe is a monster
 * or a raider and never another race's settler (Muster#foe), and a war between the races is only an idea on the plan (docs/plan.md section
 * 8, the user's call). If that war is made, this test's last check turns round: each must fight the other.
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazuktogether", time = 6000, daylightCycle = BatchWorld.Rule.OFF, weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
public class TogetherGameTests {

    private static final BlockPos SHRINE = new BlockPos(14, 3, 36), IDOL = new BlockPos(58, 3, 36);
    public static final int MINUTES = 3 * 1200;

    @GameTest(template = "field", timeoutTicks = MINUTES + 600, batch = "khazuktogether", requires = {"waaagh"})
    public static void aDwarfHoldAndAnOrkCampSideBySideBothKeepWorking(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Block idolBlock = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("waaagh", "idol"));
        h.assertTrue(idolBlock != Blocks.AIR, "no waaagh:idol block in an all-mods run");
        h.setBlock(SHRINE, KRegistry.SHRINE.get().defaultBlockState());
        h.setBlock(IDOL, idolBlock.defaultBlockState());
        if (!(level.getBlockEntity(h.absolutePos(SHRINE)) instanceof ShrineBlockEntity hold)) {
            throw h.assertionException("no Shrine's block entity");
        }
        if (!(level.getBlockEntity(h.absolutePos(IDOL)) instanceof Settlement camp)) {
            throw h.assertionException("the waaagh:idol's block entity isn't a Settlement Core settlement: " + level.getBlockEntity(h.absolutePos(IDOL)));
        }
        hold.addFood(400, Fare.COOKED, "the test");
        camp.addFood(400, Fare.COOKED, "the test");
        AABB field = h.getBounds();
        List<String> wrongs = new ArrayList<>();
        int[] most = {0, 0};
        h.onEachTick(() -> {
            for (SettlerMob m : level.getEntitiesOfClass(SettlerMob.class, field, LivingEntity::isAlive)) {
                LivingEntity by = m.getLastHurtByMob();
                if (by != null && ork(by) != ork(m) && wrongs.size() < 10) {
                    wrongs.add(m.getName().getString() + " hurt by " + by.getName().getString() + " at tick " + h.getTick());
                }
                if (m.getTarget() != null && ork(m.getTarget()) != ork(m) && m.getTarget() instanceof SettlerMob && wrongs.size() < 10) {
                    wrongs.add(m.getName().getString() + " went for " + m.getTarget().getName().getString() + " at tick " + h.getTick());
                }
            }
            most[0] = Math.max(most[0], hold.peopleCount());
            most[1] = Math.max(most[1], camp.peopleCount());
            if (h.getTick() % 1200 == 0) {
                Khazuk.LOG.info("KHAZUK together: tick {}: the hold {} up (food {}), the camp {} up (food {})", h.getTick(), hold.peopleCount(), hold.food(),
                        camp.peopleCount(), camp.food());
            }
        });
        h.runAfterDelay(MINUTES, () -> {
            h.assertTrue(wrongs.isEmpty(), "the Dwarfs and the Orks fought, which the design doesn't have them do: " + wrongs);
            h.assertTrue(most[0] >= 3 && hold.peopleCount() >= 3, "the hold raised " + most[0] + " Dawi at most (" + hold.peopleCount() + " up now) beside the camp");
            h.assertTrue(most[1] >= 3 && camp.peopleCount() >= 3, "the camp raised " + most[1] + " Orks at most (" + camp.peopleCount() + " up now) beside the hold");
            long dawi = level.getEntitiesOfClass(Dawi.class, field, LivingEntity::isAlive).size();
            long orks = level.getEntitiesOfClass(SettlerMob.class, field, m -> m.isAlive() && ork(m)).size();
            h.assertTrue(dawi > 0 && orks > 0, "on the field: " + dawi + " Dawi and " + orks + " Orks");
            h.succeed();
        });
    }

    /** Whether {@code e} is one of the Waaagh!'s (by its type's namespace: no Waaagh! class is named here). */
    private static boolean ork(LivingEntity e) {
        return "waaagh".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
    }
}
