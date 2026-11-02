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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;

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

        // a blow heavier than the kick (the mock god never ticks, so his swing is at a fifth: a base of 60 makes it about 12): without
        // clearing the blow's i-frames the kick would add nothing; without putting them back it would take 8 off his next blow
        IronGolem foe = h.spawn(EntityType.IRON_GOLEM, AT.offset(1, 0, 3));
        foe.setNoAi(true);
        god.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(60.0);
        god.attack(foe);                                         // the real path: the god's blow, then his bearers' kick
        float hit = foe.getHealth();
        h.assertTrue(foe.getMaxHealth() - hit >= 10f, "the god's own blow took only " + (foe.getMaxHealth() - hit) + ", not a heavy one");
        float[] kicked = new float[1];
        h.startSequence()
                .thenExecuteAfter(3, () -> h.assertTrue(foe.getHealth() == hit, "the kick landed before the clip's impact"))
                .thenExecuteAfter(4, () -> {
                    kicked[0] = foe.getHealth();
                    h.assertTrue(hit - kicked[0] >= 6f, "the kick took " + (hit - kicked[0]) + " health after the blow, not about "
                            + ThroneOfPower.KICK_DAMAGE);
                })
                .thenExecuteAfter(6, () -> {                     // the god's next swing, 13 ticks after his first (no kick this time)
                    foe.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(god), 12f);
                    h.assertTrue(kicked[0] - foe.getHealth() >= 11f, "the next blow took only " + (kicked[0] - foe.getHealth())
                            + " of 12: the kick cut into it");
                })
                .thenExecute(() -> {
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

    /** Broken (killed outright, the loot rule whatever it is), the throne goes back into its god's pack: one throne, not none, not two. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void aBrokenThroneGoesBackToItsGod(GameTestHelper h) {
        ThroneOfPower t = h.spawn(KRegistry.THRONE.get(), AT);
        var god = h.makeMockServerPlayerInLevel();
        t.setOwner(god.getUUID());
        t.kill(h.getLevel());
        h.runAfterDelay(2, () -> {
            int held = god.getInventory().countItem(KRegistry.THRONE_ITEM.get());
            int dropped = 0;
            for (var e : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(AT)).inflate(8))) {
                if (e.getItem().is(KRegistry.THRONE_ITEM.get())) {
                    dropped += e.getItem().getCount();
                }
            }
            h.assertTrue(t.isDeadOrDying() && held + dropped == 1, "the broken throne came back as " + held + " in the pack and " + dropped
                    + " dropped, not 1");
            h.succeed();
        });
    }
}
