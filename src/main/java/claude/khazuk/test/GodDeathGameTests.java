package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.engine.AncestorEngine;
import claude.khazuk.god.AncestorPowers;
import claude.khazuk.god.Waking;
import claude.khazuk.throne.ThroneOfPower;
import claude.settlementcore.test.BatchWorld;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import claude.settlementcore.test.SurvivalGod;
import claude.settlementcore.unit.SettlerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The god dies and comes back (survival, keepInventory off: everything he carries falls where he died). Respawn makes a NEW ServerPlayer
 * with the same id (PlayerList#respawn, through the death screen's Respawn packet), so everything that knew the old object must know him by
 * his id:
 * <ul>
 * <li>his hold still calls him its god (its owner, ShrineBlockEntity#ownedAt), the Dawi he woke still know him (Waking's attachment is
 * copied on death), a Warrior he ordered to follow him follows the new him, and takes his next order;</li>
 * <li>his devotion is his hold's, so it is all there, and a power's token he dropped, picked up again, still calls its power on it;</li>
 * <li>dying in the Throne of Power or the Ancestor Engine leaves the mount standing where it stood, unhurt and empty, still his: he climbs
 * back in.</li>
 * </ul>
 * Every order, pick-up and mount is his hands (the kit's packets); the hold itself is set down the server's way (SurvivalPlay#shrineFor).
 */
@GameTestHolder(Khazuk.MODID)
@BatchWorld(batch = "khazukdeath", time = 6000, daylightCycle = BatchWorld.Rule.OFF, keepInventory = BatchWorld.Rule.OFF,
        weather = BatchWorld.Weather.CLEAR, difficulty = Difficulty.NORMAL)
public class GodDeathGameTests {

    private static final BlockPos HEART = new BlockPos(12, 1, 12), DIES_AT = new BlockPos(4, 1, 4), LATER = new BlockPos(20, 1, 20);

    @GameTest(template = "arena", timeoutTicks = 1600, batch = "khazukdeath")
    public static void aGodWhoDiesAndRespawnsIsStillHisHoldsGod(GameTestHelper h) {
        SurvivalGod god = SurvivalGod.join(h, HEART.offset(-2, 0, 0));
        UUID id = god.player().getUUID();
        ShrineBlockEntity hold = SurvivalPlay.shrineFor(h, god, HEART, true);
        String name = Waking.god(god.player()).holdName();
        god.give(Items.BREAD, 32);
        SurvivalPlay.handAll(h, god, HEART, Items.BREAD);
        hold.roundForTests(h.getLevel());
        // a hold that has prayed a while: the Ancestors' gifts (Devotion#gifts, the hold's next second) put each power's token in his pack ONCE,
        // Grungni's Hammer, Valaya's Ward and Grimnir's Fury, so the test gives him none of its own (a second Ward would be a duplicate no
        // real game makes; the first run of this test did, and had two Wards fall where he died)
        hold.devotion().add(100);
        AABB round = new AABB(h.absolutePos(HEART)).inflate(8);
        Warrior[] w = {null};
        ServerPlayer[] before = {null};
        SurvivalGod.Job[] pick = {null};
        int[] devotion = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(!h.getLevel().getEntitiesOfClass(Warrior.class, round).isEmpty(), "the round's Warrior isn't up");
                    h.assertTrue(god.count(KRegistry.VALAYAS_WARD.get()) == 1 && god.count(KRegistry.GRUNGNIS_HAMMER.get()) == 1
                            && god.count(KRegistry.GRIMNIRS_FURY.get()) == 1, "the Ancestors' gifts haven't reached his pack: " + god.contents()
                            + ", gifted " + hold.devotion().gifted(AncestorPowers.Power.WARD) + ", told " + SurvivalPlay.last(god, 3));
                })
                .thenExecute(() -> {
                    w[0] = h.getLevel().getEntitiesOfClass(Warrior.class, round).get(0);
                    god.moveTo(BlockPos.containing(h.relativeVec(w[0].position())).offset(-1, 0, 0));
                    god.useOnEntity(w[0]);                       // his order: work, then follow
                    h.assertTrue(w[0].getMode() == SettlerMob.Mode.FOLLOW, "his order left the Warrior at " + w[0].getMode() + ": told "
                            + SurvivalPlay.last(god, 2));
                    god.moveTo(DIES_AT);
                })
                .thenWaitUntil(() -> h.assertTrue(w[0].distanceTo(god.player()) < 3.5, "the Warrior doesn't follow him: " + w[0].distanceTo(god.player())
                        + " off, goals " + w[0].runningGoals()))
                .thenExecute(() -> {
                    before[0] = god.player();
                    god.die();
                    ServerPlayer now = god.respawn();
                    h.assertTrue(now != before[0] && now.getUUID().equals(id), "the respawned god isn't a new player with his id");
                    Waking.God g = Waking.god(now);
                    h.assertTrue(g.woke() && g.holdName().equals(name), "the Dawi forgot him when he died: " + g);
                    h.assertTrue(id.equals(hold.owner()) && ShrineBlockEntity.ownedAt(now, h.absolutePos(HEART)) == hold,
                            "the hold doesn't call the respawned god its own: owner " + hold.owner());
                    h.assertTrue(w[0].isGod(now) && w[0].god() == now, "the Warrior doesn't know the respawned god: its god " + w[0].god());
                    h.assertTrue(god.count(KRegistry.VALAYAS_WARD.get()) == 0, "he kept his things with keepInventory off: " + god.contents());
                    god.moveTo(DIES_AT);                          // back from the world's spawn, where he died
                    pick[0] = god.pickUp(6);
                })
                .thenWaitUntil(() -> pick[0].assertDone())
                .thenExecute(() -> {
                    h.assertTrue(god.count(KRegistry.VALAYAS_WARD.get()) == 1 && god.count(KRegistry.GRUNGNIS_HAMMER.get()) == 1
                            && god.count(KRegistry.GRIMNIRS_FURY.get()) == 1, "his three tokens weren't picked up again, one each: " + god.contents());
                    god.moveTo(LATER);
                })
                .thenWaitUntil(() -> h.assertTrue(w[0].distanceTo(god.player()) < 3.5, "the Warrior doesn't follow the respawned god: "
                        + w[0].distanceTo(god.player()) + " off, goals " + w[0].runningGoals()))
                .thenExecute(() -> {
                    devotion[0] = hold.devotion().devotion();
                    god.hold(KRegistry.VALAYAS_WARD.get()).useItem();
                    // (its "Valaya's Ward is over" line goes to his action bar, which SurvivalGod's chat() doesn't keep: the devotion paid is the proof)
                    h.assertTrue(hold.devotion().devotion() < devotion[0] && AncestorPowers.warded(w[0]), "the Ward's token, called after his death,"
                            + " did nothing: devotion " + devotion[0] + " -> " + hold.devotion().devotion() + ", the Warrior warded " + AncestorPowers.warded(w[0])
                            + ", told " + SurvivalPlay.last(god, 2));
                    god.moveTo(BlockPos.containing(h.relativeVec(w[0].position())).offset(-1, 0, 0));
                    god.useOnEntity(w[0]);                       // follow, then hold
                    h.assertTrue(w[0].getMode() == SettlerMob.Mode.GUARD, "the Warrior didn't take the respawned god's order: " + w[0].getMode());
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "khazukdeath")
    public static void dyingOnTheThroneLeavesItStandingAndHisToRideAgain(GameTestHelper h) {
        diesRiding(h, KRegistry.THRONE_ITEM.get(), ThroneOfPower.class);
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "khazukdeath")
    public static void dyingInTheAncestorEngineLeavesItStandingAndHisToRideAgain(GameTestHelper h) {
        diesRiding(h, KRegistry.ANCESTOR_ENGINE_ITEM.get(), AncestorEngine.class);
    }

    /** He sets the mount down from his pack, climbs in, dies in it and respawns: it stands where it stood, unhurt, empty and his. */
    private static <T extends LivingEntity> void diesRiding(GameTestHelper h, Item item, Class<T> kind) {
        BlockPos at = new BlockPos(12, 1, 12);
        SurvivalGod god = SurvivalGod.join(h, at.offset(-4, 0, 0));
        god.give(item, 1).hold(item).useOn(at.below(), Direction.UP);
        List<T> found = h.getLevel().getEntitiesOfClass(kind, new AABB(h.absolutePos(at)).inflate(3));
        h.assertTrue(found.size() == 1, "setting the " + item + " down made " + found.size() + " mounts: told " + SurvivalPlay.last(god, 2));
        T mount = found.get(0);
        h.assertTrue(god.count(item) == 0, "the item wasn't used up setting it down");
        god.moveTo(at.offset(-3, 0, 0));
        god.useOnEntity(mount);
        h.assertTrue(god.player().getVehicle() == mount, "he didn't climb in: riding " + god.player().getVehicle() + ", told " + SurvivalPlay.last(god, 2));
        Vec3 stood = mount.position();
        float health = mount.getHealth();
        UUID id = god.player().getUUID();
        god.die();
        ServerPlayer now = god.respawn();
        h.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(mount.isAlive() && !mount.isRemoved(), "the mount is gone after its rider died: " + mount.getRemovalReason());
                    h.assertTrue(mount.getPassengers().isEmpty() && now.getVehicle() == null, "the dead god still rides it: " + mount.getPassengers());
                    h.assertTrue(mount.position().distanceTo(stood) < 1.0, "the mount moved " + mount.position().distanceTo(stood) + " as he died");
                    h.assertTrue(mount.getHealth() >= health, "the mount was hurt as he died: " + health + " -> " + mount.getHealth());
                    UUID owner = mount instanceof ThroneOfPower t ? t.owner() : ((AncestorEngine) mount).owner();
                    h.assertTrue(id.equals(owner), "the mount isn't his after his death: owner " + owner);
                    god.moveTo(at.offset(-3, 0, 0));
                    god.useOnEntity(mount);
                    h.assertTrue(god.player().getVehicle() == mount, "the respawned god couldn't climb back in: told " + SurvivalPlay.last(god, 2));
                })
                .thenSucceed();
    }
}
