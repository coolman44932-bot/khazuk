package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.throne.ThroneOfPower;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Thorgrim's Throne of Power, the god's mount: its owner gets on and a stranger doesn't; the bearers kick what the rider strikes, landing on
 * the clip's tick 5; a wrong to the rider goes in the Book and the wrongdoer's death settles it (healed, strengthened); sneak-used, it folds
 * back into its item. On the arena's floor (smooth stone at y 0).
 */
@GameTestHolder(Khazuk.MODID)
public class ThroneGameTests {

    private static final BlockPos AT = new BlockPos(12, 1, 12);

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazuk")
    public static void theThroneCarriesItsGodAndItsBearersKick(GameTestHelper h) {
        ThroneOfPower t = h.spawn(KRegistry.THRONE.get(), AT);
        t.snapTo(t.getX(), t.getY(), t.getZ(), 0f, 0f);         // facing south (+z)
        var god = h.makeMockServerPlayerInLevel();
        var stranger = h.makeMockServerPlayerInLevel();
        t.setOwner(god.getUUID());
        t.interact(stranger, InteractionHand.MAIN_HAND, t.position());
        h.assertTrue(!stranger.isPassenger(), "a stranger sat in the god's throne");
        t.interact(god, InteractionHand.MAIN_HAND, t.position());
        h.assertTrue(god.getVehicle() == t && t.getControllingPassenger() == god, "the god didn't get on");

        // a blow far stronger than the kick (Strength V): without the kick clearing its i-frames, the kick would add nothing
        IronGolem foe = h.spawn(EntityType.IRON_GOLEM, AT.offset(1, 0, 3));
        foe.setNoAi(true);
        god.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 4));
        god.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        god.attack(foe);                                         // the real path: the god's blow, then his bearers' kick through its i-frames
        float hit = foe.getHealth();
        h.assertTrue(hit < foe.getMaxHealth(), "the god's own blow didn't land");
        h.startSequence()
                .thenExecuteAfter(3, () -> h.assertTrue(foe.getHealth() == hit, "the kick landed before the clip's impact"))
                .thenExecuteAfter(4, () -> h.assertTrue(hit - foe.getHealth() >= 6f, "the kick took " + (hit - foe.getHealth())
                        + " health after the blow, not about " + ThroneOfPower.KICK_DAMAGE))
                .thenExecute(() -> {
                    god.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    god.removeEffect(MobEffects.STRENGTH);
                    t.wrong(foe);
                    h.assertTrue(foe.getUUID().equals(t.grudge()), "the wrong wasn't written in the Book");
                    foe.kill(h.getLevel());
                })
                .thenExecuteAfter(1, () -> {
                    h.assertTrue(t.grudge() == null, "the wrongdoer's death didn't settle the grudge");
                    h.assertTrue(god.hasEffect(MobEffects.STRENGTH), "a settled grudge didn't strengthen the rider");
                    god.stopRiding();
                    god.setShiftKeyDown(true);
                    t.interact(god, InteractionHand.MAIN_HAND, t.position());
                    god.setShiftKeyDown(false);
                    h.assertTrue(t.isRemoved() && god.getInventory().contains(KRegistry.THRONE_ITEM.get().getDefaultInstance()),
                            "sneak-used, the throne didn't fold back into its item");
                })
                .thenSucceed();
    }
}
