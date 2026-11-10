package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.engine.AncestorEngine;
import claude.khazuk.engine.EngineKeys;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.loading.definition.animation.ActorAnimations;
import com.geckolib.loading.definition.geometry.Geometry;
import com.geckolib.loading.definition.geometry.object.ModelFormatVersion;
import com.geckolib.loading.math.MathParser;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The Ancestor Engine, the god's own walking steam engine (engine/AncestorEngine): its god mounts it and a stranger doesn't, and only
 * its rider's keys are heard; the chop lands on its clip's tick on everything in front, heats the boiler, and hits harder hot; the claw
 * takes one mark and throws it; the stomp spares the rider and his Dawi; the vent empties the gauge and scalds what is near; the axe
 * digs stone and not bedrock, a chest or a hold's hall; broken, its god is thrown clear unhurt and the wreck leaves its item; the item
 * sets it down and takes it back up; the heat survives a save, cools, and the hull mends in its god's hold; and every clip its
 * controllers ask for is in its file. On the arena's floor (smooth stone at y 0), the engine facing south (+z). The tests that need a
 * foe to stand run in the khazukhostile batch (Normal: a husk is gone at once on Peaceful).
 */
@GameTestHolder(Khazuk.MODID)
public class AncestorEngineGameTests {

    private static final BlockPos AT = new BlockPos(12, 1, 12);
    private static final BlockPos HEART = new BlockPos(4, 1, 4);
    private static final float FOE_HEALTH = 400f;

    /** An engine at AT facing south, {@code god}'s, with him in it. */
    private static AncestorEngine engine(GameTestHelper h, ServerPlayer god) {
        AncestorEngine e = parked(h);
        e.setOwner(god.getUUID());
        e.interact(god, InteractionHand.MAIN_HAND, e.position());
        if (god.getVehicle() != e || e.getControllingPassenger() != god) {
            throw h.assertionException("the god didn't get on his engine");
        }
        return e;
    }

    private static AncestorEngine parked(GameTestHelper h) {
        AncestorEngine e = h.spawn(KRegistry.ANCESTOR_ENGINE.get(), AT);
        e.snapTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
        e.setYBodyRot(0f);
        e.setYHeadRot(0f);
        return e;
    }

    /** A husk that stands where it is put and lives through any blow (the sun doesn't burn it). */
    private static Husk foe(GameTestHelper h, BlockPos at) {
        Husk z = h.spawn(EntityType.HUSK, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(FOE_HEALTH);
        z.setHealth(FOE_HEALTH);
        return z;
    }

    private static ShrineBlockEntity hold(GameTestHelper h, ServerPlayer god) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.claim(god, "Karak Test");
        return heart;
    }

    private static EngineKeys keys(boolean attack, boolean use, boolean vent) {
        return new EngineKeys(attack, use, vent);
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void itsGodMountsAStrangerCantAndOnlyItsRiderIsHeard(GameTestHelper h) {
        AncestorEngine e = parked(h);
        var god = h.makeMockServerPlayerInLevel();
        var stranger = h.makeMockServerPlayerInLevel();
        e.setOwner(god.getUUID());
        h.assertTrue(e.getBbWidth() == 2.0f && e.getBbHeight() == 3.0f, "the engine is " + e.getBbWidth() + " x " + e.getBbHeight() + ", not 2 x 3");
        h.assertTrue(e.getAttributeValue(Attributes.STEP_HEIGHT) >= 1.0 && e.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) == 0.0
                && e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1.0, "it doesn't step up a block, or a fall or a blow moves it");
        h.assertTrue(e.getMaxHealth() >= claude.khazuk.SlayerMech.MAX_HEALTH * 2.5 && AncestorEngine.CHOP > claude.khazuk.SlayerMech.DAMAGE
                        * (1 + claude.khazuk.SlayerMech.HOT_DAMAGE) && AncestorEngine.CRUSH >= AncestorEngine.CHOP * 2,
                "a god's engine isn't far above the Slayer mech: " + e.getMaxHealth() + " health, a chop of " + AncestorEngine.CHOP);
        e.interact(stranger, InteractionHand.MAIN_HAND, e.position());
        h.assertTrue(!stranger.isPassenger(), "a stranger climbed into the god's engine");
        h.assertTrue(!AncestorEngine.keysFrom(god, keys(true, false, false)), "the engine heard a god who isn't in it");
        e.interact(god, InteractionHand.MAIN_HAND, e.position());
        h.assertTrue(god.getVehicle() == e && e.getControllingPassenger() == god, "the god didn't get on");
        Vec3 seat = e.getPassengerRidingPosition(god);
        h.assertTrue(Math.abs(seat.y - (e.getY() + 1.475)) < 1e-3 && Math.abs(seat.z - (e.getZ() - 0.25)) < 1e-3 && Math.abs(seat.x - e.getX()) < 1e-3,
                "the seat is at " + seat.subtract(e.position()) + ", not (0, 1.475, -0.25)");
        h.assertTrue(!AncestorEngine.keysFrom(stranger, keys(true, true, true)), "the engine heard a stranger's keys");
        h.assertTrue(AncestorEngine.keysFrom(god, keys(false, false, false)), "the engine didn't hear its own rider");
        boolean hurt = e.causeFallDamage(30.0, 1f, h.getLevel().damageSources().fall());   // a 30-block fall, its rider in it
        h.assertTrue(!hurt && e.getHealth() == e.getMaxHealth() && god.getHealth() == god.getMaxHealth(), "a fall hurt the engine or its rider");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void theChopLandsOnItsHitTickHeatsTheBoilerAndHitsHarderHot(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        AncestorEngine e = engine(h, god);
        Husk front = foe(h, AT.offset(0, 0, 2)), beside = foe(h, AT.offset(1, 0, 2)), behind = foe(h, AT.offset(0, 0, -3));
        float[] cold = new float[1];
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> h.assertTrue(AncestorEngine.keysFrom(god, keys(true, false, false)), "the rider's attack key wasn't heard"))
                .thenExecuteAfter(3, () -> h.assertTrue(e.blow() == AncestorEngine.Blow.CHOP && front.getHealth() == FOE_HEALTH && e.heat() == 0f,
                        "3 ticks on: the blow is " + e.blow() + ", the husk at " + front.getHealth() + " (the chop lands on tick "
                                + AncestorEngine.Blow.CHOP.lands + ")"))
                .thenExecuteAfter(4, () -> {
                    cold[0] = FOE_HEALTH - front.getHealth();
                    h.assertTrue(cold[0] >= AncestorEngine.CHOP * 0.9f, "the chop took " + cold[0] + " off the husk in front, not about " + AncestorEngine.CHOP);
                    h.assertTrue(beside.getHealth() < FOE_HEALTH, "the chop missed the husk beside its mark: a wide blow hits several");
                    h.assertTrue(behind.getHealth() == FOE_HEALTH, "the chop hit a husk behind the engine");
                    h.assertTrue(e.heat() == AncestorEngine.HEAT_CHOP, "a chop that landed left the boiler at " + e.heat());
                    h.assertTrue(god.getHealth() == god.getMaxHealth() && e.getHealth() == e.getMaxHealth(), "the chop hurt the rider or the engine");
                    AncestorEngine.keysFrom(god, keys(false, false, false));
                })
                .thenExecuteAfter(8, () -> {                     // the chop is over: the boiler to the red line, and the same blow again
                    h.assertTrue(e.blow() == null, "the key up, the engine still swings: " + e.blow());
                    e.setHeat(AncestorEngine.MAX_HEAT);
                    h.assertTrue(e.overheated() && Math.abs(e.power() - (1f + AncestorEngine.HOT_BONUS)) < 1e-6, "at the red line its power is " + e.power());
                    front.setHealth(FOE_HEALTH);
                    AncestorEngine.keysFrom(god, keys(true, false, false));
                })
                .thenExecuteAfter(8, () -> {
                    float hot = FOE_HEALTH - front.getHealth();
                    h.assertTrue(hot >= cold[0] * 1.4f, "at the red line the chop took " + hot + ", cold " + cold[0] + ": not half as much again");
                    h.assertTrue(e.isAlive() && e.getHealth() == e.getMaxHealth() && god.getHealth() == god.getMaxHealth(),
                            "the heat hurt the engine or its rider");
                    AncestorEngine.keysFrom(god, keys(false, false, false));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void theClawTakesOneMarkAndThrowsIt(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        AncestorEngine e = engine(h, god);
        Husk mark = foe(h, AT.offset(0, 0, 2)), other = foe(h, AT.offset(1, 0, 3));
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> AncestorEngine.keysFrom(god, new EngineKeys(false, false, false, false, true)))   // a click shorter than a tick
                .thenExecuteAfter(4, () -> h.assertTrue(e.blow() == AncestorEngine.Blow.CRUSH && mark.getHealth() == FOE_HEALTH,
                        "4 ticks on: the blow is " + e.blow() + ", the mark at " + mark.getHealth() + " (the claw shuts on tick "
                                + AncestorEngine.Blow.CRUSH.lands + ")"))
                .thenExecuteAfter(4, () -> {
                    float lost = FOE_HEALTH - mark.getHealth();
                    h.assertTrue(lost >= AncestorEngine.CRUSH * 0.9f, "the claw took " + lost + " off its mark, not about " + AncestorEngine.CRUSH);
                    h.assertTrue(other.getHealth() == FOE_HEALTH, "the claw hurt a second husk: it takes one");
                    h.assertTrue(mark.getDeltaMovement().z > 0.5, "the mark wasn't thrown the way the engine faces: " + mark.getDeltaMovement());
                    h.assertTrue(e.heat() == AncestorEngine.HEAT_CRUSH, "the claw left the boiler at " + e.heat());
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void theStompSparesItsRiderAndHisDawi(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        ShrineBlockEntity heart = hold(h, god);
        AncestorEngine e = engine(h, god);
        Husk near = foe(h, AT.offset(2, 0, 1)), far = foe(h, AT.offset(0, 0, 7));
        Miner miner = h.spawn(KRegistry.MINER.get(), AT.offset(-2, 0, 0));
        miner.bindTo(heart);
        miner.setNoAi(true);
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> god.setLastClientInput(new Input(false, false, false, false, true, false, false)))   // the jump key, standing
                .thenExecuteAfter(6, () -> h.assertTrue(e.blow() == AncestorEngine.Blow.STOMP && e.rooted() && near.getHealth() == FOE_HEALTH,
                        "6 ticks on: the blow is " + e.blow() + ", rooted " + e.rooted() + ", the husk at " + near.getHealth()))
                .thenExecuteAfter(5, () -> {
                    god.setLastClientInput(Input.EMPTY);
                    float lost = FOE_HEALTH - near.getHealth();
                    h.assertTrue(lost >= AncestorEngine.STOMP * 0.8f, "the stomp took " + lost + " off a husk 2 blocks off, not about " + AncestorEngine.STOMP);
                    h.assertTrue(far.getHealth() == FOE_HEALTH, "the stomp reached a husk 7 blocks off");
                    h.assertTrue(miner.getHealth() == miner.getMaxHealth(), "the stomp hurt the god's own Miner: " + miner.getHealth());
                    h.assertTrue(god.getHealth() == god.getMaxHealth() && e.getHealth() == e.getMaxHealth(), "the stomp hurt its rider or itself");
                    h.assertTrue(e.heat() == AncestorEngine.HEAT_STOMP, "the stomp left the boiler at " + e.heat());
                })
                .thenExecuteAfter(12, () -> h.assertTrue(e.blow() == null && !e.rooted(), "the key up, it still stomps: " + e.blow()))
                .thenExecute(() -> {                             // moving, the jump key is no stomp
                    god.setLastClientInput(new Input(true, false, false, false, true, false, false));
                })
                .thenExecuteAfter(3, () -> {
                    h.assertTrue(e.blow() == null, "it stomped while it walked: " + e.blow());
                    god.setLastClientInput(Input.EMPTY);
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void theVentEmptiesTheGaugeAndScaldsWhatIsNear(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        AncestorEngine e = engine(h, god);
        Husk near = foe(h, AT.offset(2, 0, -1)), far = foe(h, AT.offset(0, 0, 8));
        float heat = 80f, scald = AncestorEngine.VENT_LEAST + (AncestorEngine.VENT_FULL - AncestorEngine.VENT_LEAST) * heat / AncestorEngine.MAX_HEAT;
        float[] after = new float[1];
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> {
                    e.setHeat(heat);
                    AncestorEngine.keysFrom(god, keys(false, false, true));
                })
                .thenExecuteAfter(6, () -> {
                    after[0] = near.getHealth();
                    h.assertTrue(e.heat() == 0f, "vented, the gauge reads " + e.heat());
                    h.assertTrue(FOE_HEALTH - after[0] >= scald * 0.9f, "the vent took " + (FOE_HEALTH - after[0]) + " off a husk 2 blocks off, not about " + scald);
                    h.assertTrue(far.getHealth() == FOE_HEALTH, "the vent reached a husk 8 blocks off");
                    h.assertTrue(god.getHealth() == god.getMaxHealth() && e.getHealth() == e.getMaxHealth(), "the vent hurt its rider or itself");
                })
                .thenExecuteAfter(16, () -> {                    // a cold boiler has nothing to vent
                    h.assertTrue(e.blow() == null, "the vent isn't over: " + e.blow());
                    AncestorEngine.keysFrom(god, keys(false, false, true));
                })
                .thenExecuteAfter(6, () -> h.assertTrue(e.blow() == null && near.getHealth() == after[0], "a cold boiler vented: " + e.blow()
                        + ", the husk at " + near.getHealth()))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 140, batch = "khazuk")
    public static void theAxeDigsStoneAndNotBedrockAChestOrAHoldsHall(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var god = h.makeMockServerPlayerInLevel();
        ShrineBlockEntity heart = hold(h, god);
        BlockPos hall = new BlockPos(20, 1, 4), open = new BlockPos(20, 1, 12);
        h.setBlock(hall, Blocks.STONE);
        h.setBlock(open, Blocks.STONE);
        h.assertTrue(AncestorEngine.digs(level, h.absolutePos(hall), Blocks.STONE.defaultBlockState()), "stone on open ground isn't dug");
        BlockPos corner = h.absolutePos(hall.offset(-2, 0, -2));
        heart.addBuilding(new Building("dawi_barracks", new BoundingBox(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + 4, corner.getY() + 4,
                corner.getZ() + 4)), false);
        h.assertTrue(!AncestorEngine.digs(level, h.absolutePos(hall), Blocks.STONE.defaultBlockState()), "the axe would dig a hold's own hall");
        h.assertTrue(AncestorEngine.digs(level, h.absolutePos(open), Blocks.STONE.defaultBlockState()), "stone outside the hall isn't dug");
        h.assertTrue(!AncestorEngine.digs(level, h.absolutePos(HEART), level.getBlockState(h.absolutePos(HEART))), "the axe would dig the Shrine");
        for (var none : new net.minecraft.world.level.block.Block[]{Blocks.BEDROCK, Blocks.CHEST, Blocks.AIR, Blocks.WATER}) {
            h.setBlock(open, none);
            h.assertTrue(!AncestorEngine.digs(level, h.absolutePos(open), level.getBlockState(h.absolutePos(open))), "the axe would dig " + none);
        }
        for (var some : new net.minecraft.world.level.block.Block[]{Blocks.DEEPSLATE_GOLD_ORE, Blocks.DIRT, Blocks.OAK_LOG, Blocks.GRAVEL, Blocks.OBSIDIAN}) {
            h.setBlock(open, some);
            h.assertTrue(AncestorEngine.digs(level, h.absolutePos(open), level.getBlockState(h.absolutePos(open))), "the axe won't dig " + some);
        }
        h.setBlock(open, Blocks.AIR);

        AncestorEngine e = engine(h, god);
        BlockPos face = AT.offset(0, 1, 3);                      // ahead of it and a block up: where the work clip bites
        BlockPos abs = h.absolutePos(face);
        h.setBlock(face, Blocks.STONE);
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> {
                    e.positionRider(god);
                    god.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(abs));
                    AncestorEngine.keysFrom(god, keys(true, false, false));
                })
                .thenExecuteAfter(8, () -> h.assertTrue(e.working() && e.blow() == null && level.getBlockState(abs).is(Blocks.STONE),
                        "8 ticks on: working " + e.working() + ", the blow " + e.blow() + ", the face " + level.getBlockState(abs)
                                + " (the bite is " + AncestorEngine.BITE_AT + " ticks into the loop)"))
                .thenExecuteAfter(8, () -> {
                    h.assertTrue(level.getBlockState(abs).isAir(), "the axe's bite left " + level.getBlockState(abs));
                    int cobble = 0;
                    for (ItemEntity i : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3))) {
                        if (i.getItem().is(Items.COBBLESTONE)) {
                            cobble += i.getItem().getCount();
                        }
                    }
                    h.assertTrue(cobble == 1, "the dug stone dropped " + cobble + " cobblestone, not its one");
                    h.setBlock(face, Blocks.BEDROCK);
                })
                .thenExecuteAfter(20, () -> {
                    h.assertTrue(level.getBlockState(abs).is(Blocks.BEDROCK) && !e.working(), "the axe took bedrock, or works at it: " + level.getBlockState(abs));
                    h.setBlock(face, Blocks.CHEST);
                })
                .thenExecuteAfter(20, () -> {
                    h.assertTrue(level.getBlockState(abs).is(Blocks.CHEST) && !e.working(), "the axe took a chest, or works at it: " + level.getBlockState(abs));
                    h.setBlock(face, Blocks.AIR);
                    AncestorEngine.keysFrom(god, keys(false, false, false));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void brokenItsGodIsThrownClearUnhurtAndTheWreckLeavesItsItem(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        AncestorEngine e = engine(h, god);
        Husk near = foe(h, AT.offset(3, 0, 0));
        h.startSequence()
                .thenIdle(AncestorEngine.RISE_TICKS + 2)         // it heaves up first
                .thenExecute(() -> e.kill(h.getLevel()))
                .thenExecuteAfter(4, () -> h.assertTrue(e.isDeadOrDying() && god.getVehicle() == e && near.getHealth() == FOE_HEALTH,
                        "4 ticks into the burst: its god is " + (god.getVehicle() == e ? "in his seat" : "out already") + ", the husk at " + near.getHealth()))
                .thenExecuteAfter(AncestorEngine.RIDER_OUT_AT, () -> {
                    h.assertTrue(e.deathTime >= AncestorEngine.RIDER_OUT_AT && !god.isPassenger(), "at death tick " + e.deathTime + " its god is still in the wreck");
                    h.assertTrue(god.getHealth() == god.getMaxHealth() && god.isAlive(), "the burst hurt its god: " + god.getHealth());
                    h.assertTrue(AncestorEngine.landsUnhurt(god), "thrown clear, its god would be hurt by the fall");
                    h.assertTrue(god.getY() > e.getY() + 0.5, "its god left the wreck at its feet, not from his seat");
                    h.assertTrue(FOE_HEALTH - near.getHealth() >= AncestorEngine.BURST * 0.8f && near.isOnFire(), "the burst took "
                            + (FOE_HEALTH - near.getHealth()) + " off a husk 3 blocks off" + (near.isOnFire() ? "" : " and didn't set it burning"));
                })
                .thenExecuteAfter(8, () -> {
                    int held = god.getInventory().countItem(KRegistry.ANCESTOR_ENGINE_ITEM.get()), dropped = 0;
                    for (ItemEntity i : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(AT)).inflate(8))) {
                        if (i.getItem().is(KRegistry.ANCESTOR_ENGINE_ITEM.get())) {
                            dropped += i.getItem().getCount();
                        }
                    }
                    h.assertTrue(e.isRemoved() && held + dropped == 1, "the wreck left " + held + " in the pack and " + dropped + " dropped, not 1"
                            + (e.isRemoved() ? "" : "; it still stands"));
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theItemSetsItDownAndItsGodPacksItUp(GameTestHelper h) {
        var god = h.makeMockServerPlayerInLevel();
        var stranger = h.makeMockServerPlayerInLevel();
        god.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KRegistry.ANCESTOR_ENGINE_ITEM.get()));
        BlockPos floor = h.absolutePos(AT.below());
        KRegistry.ANCESTOR_ENGINE_ITEM.get().useOn(new UseOnContext(god, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        var set = h.getLevel().getEntitiesOfClass(AncestorEngine.class, new AABB(h.absolutePos(AT)).inflate(4));
        h.assertTrue(set.size() == 1, "the item set down " + set.size() + " engines");
        AncestorEngine e = set.get(0);
        h.assertTrue(god.getUUID().equals(e.owner()) && !e.isVehicle() && e.blockPosition().equals(h.absolutePos(AT)),
                "set down, it is " + e.owner() + "'s at " + e.blockPosition());
        god.getInventory().clearContent();
        stranger.setShiftKeyDown(true);
        e.interact(stranger, InteractionHand.MAIN_HAND, e.position());
        h.assertTrue(!e.isRemoved() && stranger.getInventory().countItem(KRegistry.ANCESTOR_ENGINE_ITEM.get()) == 0, "a stranger packed up the god's engine");
        god.setShiftKeyDown(true);
        e.interact(god, InteractionHand.MAIN_HAND, e.position());
        god.setShiftKeyDown(false);
        h.assertTrue(e.isRemoved() && god.getInventory().countItem(KRegistry.ANCESTOR_ENGINE_ITEM.get()) == 1,
                "sneak-used by its god, it left " + god.getInventory().countItem(KRegistry.ANCESTOR_ENGINE_ITEM.get()) + " items"
                        + (e.isRemoved() ? "" : " and still stands"));
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "khazuk")
    public static void theHeatSurvivesASaveCoolsAndTheHullMendsInItsGodsHold(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var god = h.makeMockServerPlayerInLevel();
        hold(h, god);
        AncestorEngine e = parked(h);
        UUID id = UUID.randomUUID();
        e.setOwner(id);
        e.setHeat(42f);
        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        e.saveWithoutId(out);
        AncestorEngine back = KRegistry.ANCESTOR_ENGINE.get().create(level, EntitySpawnReason.LOAD);
        back.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), out.buildResult()));
        h.assertTrue(back.heat() == 42f && id.equals(back.owner()), "saved at 42 heat, " + id + "'s, it loads at " + back.heat() + ", " + back.owner() + "'s");
        back.discard();

        e.setOwner(god.getUUID());
        e.setHeat(50f);
        e.setHealth(100f);
        h.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    h.assertTrue(e.heat() == 50f, "the boiler cooled " + (50f - e.heat()) + " in its first seconds, not after " + AncestorEngine.COOL_AFTER + " ticks");
                    h.assertTrue(e.getHealth() > 100f && e.getHealth() <= 100f + 3 * AncestorEngine.MEND, "parked in its god's hold for 2 seconds it mended "
                            + (e.getHealth() - 100f));
                })
                .thenIdle(100)
                .thenExecute(() -> h.assertTrue(e.heat() < 50f && e.heat() >= 50f - 3 * AncestorEngine.COOL, "left alone 145 ticks, the boiler is at " + e.heat()))
                .thenSucceed();
    }

    /** GeckoLib 5.5 crashes on a clip that isn't there: the engine's geo bakes, and every clip its controllers ask for is in its file. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void itsModelBakesWithEveryClipItsControllersAskFor(GameTestHelper h) throws Exception {
        Identifier id = Identifier.fromNamespaceAndPath(Khazuk.MODID, "entity/ancestor_engine");
        var jar = ModList.get().getModFileById(Khazuk.MODID).getFile().getContents();
        String geoPath = GeckoLibResources.MODELS_PATH.getPath() + "/" + id.getPath() + ".geo.json";
        String clipPath = GeckoLibResources.ANIMATIONS_PATH.getPath() + "/" + id.getPath() + ".animation.json";
        for (String file : new String[]{geoPath, clipPath}) {
            Identifier filed = GeckoLibResources.stripPrefixAndSuffix(Identifier.fromNamespaceAndPath(Khazuk.MODID, file));
            h.assertTrue(filed.equals(id), file + " is filed by GeckoLib as " + filed + ", not " + id);
        }
        for (String path : new String[]{"assets/khazuk/" + geoPath, "assets/khazuk/" + clipPath, "assets/khazuk/textures/entity/ancestor_engine.png",
                "assets/khazuk/textures/entity/ancestor_engine_glowmask.png", "assets/khazuk/textures/item/ancestor_engine.png",
                "assets/khazuk/items/ancestor_engine.json", "assets/khazuk/models/item/ancestor_engine.json"}) {
            h.assertTrue(jar.containsFile(path), "the Dwarfs' jar has no " + path);
        }
        Geometry geo;
        try (InputStreamReader r = new InputStreamReader(jar.openFile("assets/khazuk/" + geoPath), StandardCharsets.UTF_8)) {
            geo = Geometry.GSON.fromJson(r, Geometry.class);
        }
        h.assertTrue(ModelFormatVersion.match(geo.formatVersion()) != null, "the engine's geo format " + geo.formatVersion() + " is none GeckoLib knows");
        h.assertTrue(geo.bake(id).getBone("seat").isPresent(), "the engine's model has no seat bone");
        BakedAnimations baked;
        try (InputStreamReader r = new InputStreamReader(jar.openFile("assets/khazuk/" + clipPath), StandardCharsets.UTF_8)) {
            baked = ActorAnimations.GSON.fromJson(r, ActorAnimations.class).bake(id, MathParser.create());
        }
        h.assertTrue(AncestorEngine.CLIPS.size() == 14, "the engine names " + AncestorEngine.CLIPS.size() + " clips, not its 14");
        for (String clip : AncestorEngine.CLIPS) {
            h.assertTrue(baked.getAnimation("animation.khazuk." + clip) != null, "the engine has no clip " + clip + ": " + baked.animations().keySet());
        }
        h.succeed();
    }
}
