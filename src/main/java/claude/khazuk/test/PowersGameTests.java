package claude.khazuk.test;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Miner;
import claude.khazuk.Quarreller;
import claude.khazuk.Runelord;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.god.AncestorPowers;
import claude.khazuk.god.Devotion;
import claude.khazuk.god.GrungniHammer;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * D7, the Ancestor Gods' powers (god/Devotion, god/AncestorPowers, god/GrungniHammer): the hold's prayers grow with its Dwarfs, half again
 * with ale, more with a Runesmith, and a settled grudge adds to them, up to the cap; the first devotion enough for each power hands the god
 * its token, once, and the devotion and the gifts come back from a save; Grungni's Hammer strikes, burns and flings the foes in its reach and
 * spares the god's own and the ground, and is refused without the devotion to pay; a Runesmith's and a Runelord's blessings, and the Throne's
 * price; Valaya's Ward heals and wards the god and his Dawi round him (fire and shots turned aside, other blows halved) and no one else;
 * Grimnir's Fury rages his Dawi (harder blows and shots, steadier, fearless) and the god, and lifts again.
 */
@GameTestHolder(Khazuk.MODID)
public class PowersGameTests {

    private static final BlockPos HEART = new BlockPos(20, 1, 20);

    /** A hold and its god, the god stood at {@code at}. */
    @SuppressWarnings("removal")
    private static ServerPlayer god(GameTestHelper h, BlockPos at) {
        ServerPlayer god = h.makeMockServerPlayerInLevel();
        Vec3 p = h.absoluteVec(Vec3.atBottomCenterOf(at));
        god.snapTo(p.x, p.y, p.z, 0f, 0f);
        return god;
    }

    private static ShrineBlockEntity hold(GameTestHelper h, ServerPlayer god) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.claim(god, "Karak Test");
        return heart;
    }

    private static <T extends Dawi> T dawi(GameTestHelper h, EntityType<T> type, ShrineBlockEntity heart, BlockPos at) {
        T d = h.spawn(type, at);
        d.bindTo(heart);
        heart.addPerson(d);
        d.setNoAi(true);
        return d;
    }

    private static Zombie still(GameTestHelper h, BlockPos at) {
        Zombie z = h.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));   // no sunburn
        return z;
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void prayersRiseWithTheDawiAleAndRunesmithsAndASettledGrudge(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h, god(h, new BlockPos(4, 1, 4)));
        Warrior w = dawi(h, KRegistry.WARRIOR.get(), heart, new BlockPos(14, 1, 14));
        dawi(h, KRegistry.MINER.get(), heart, new BlockPos(16, 1, 14));
        dawi(h, KRegistry.MINER.get(), heart, new BlockPos(18, 1, 14));
        Devotion d = heart.devotion();
        int people = heart.peopleCount();
        h.assertTrue(people >= 3, "the Dawi aren't on the hold's roll: " + people);
        float n = d.pray(h.getLevel(), heart);
        h.assertTrue(n == people && d.devotion() == people, "a dry hold's prayers: " + n + " (devotion " + d.devotion() + "), not " + people);
        heart.brew().addAle(20);
        n = d.pray(h.getLevel(), heart);
        h.assertTrue(Math.abs(n - people * Devotion.ALE_PRAYER) < 1e-4, "with ale in the barrels the prayers are " + n + ", not " + people * Devotion.ALE_PRAYER);
        Runesmith smith = dawi(h, KRegistry.RUNESMITH.get(), heart, new BlockPos(14, 1, 18));
        float withSmith = d.pray(h.getLevel(), heart);
        int peopleNow = heart.peopleCount();
        h.assertTrue(Math.abs(withSmith - (peopleNow * Devotion.ALE_PRAYER + Devotion.SMITH_PRAYER)) < 1e-4, "a Runesmith's prayers: "
                + withSmith + " with " + peopleNow + " up and " + smith.getName().getString());
        // a named foe is a grudge of its own, settled by its own death: the Ancestors are pleased
        Zombie gutrot = still(h, new BlockPos(10, 1, 10));
        gutrot.setCustomName(Component.literal("Gutrot"));
        heart.grudges().wronged(h.getLevel(), heart, gutrot, "slew a Miner");
        int before = d.devotion();
        gutrot.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(w), 1000f);
        h.assertTrue(d.devotion() == Math.min(Devotion.MAX, before + Devotion.GRUDGE_SETTLED), "a settled grudge left devotion at " + d.devotion()
                + ", from " + before);
        d.add(10_000);
        h.assertTrue(d.devotion() == Devotion.MAX, "devotion over its cap: " + d.devotion());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theFirstDevotionForEachPowerHandsTheGodItsTokenOnce(GameTestHelper h) {
        ServerPlayer god = god(h, new BlockPos(4, 1, 4));
        ShrineBlockEntity heart = hold(h, god);
        Devotion d = heart.devotion();
        d.add(AncestorPowers.HAMMER_COST);
        heart.second(h.getLevel());
        h.assertTrue(d.gifted(AncestorPowers.Power.HAMMER) && !d.gifted(AncestorPowers.Power.WARD), "the gifts at " + d.devotion() + ": hammer "
                + d.gifted(AncestorPowers.Power.HAMMER) + ", ward " + d.gifted(AncestorPowers.Power.WARD));
        d.add(AncestorPowers.WARD_COST - AncestorPowers.HAMMER_COST);
        heart.second(h.getLevel());
        heart.second(h.getLevel());
        for (AncestorPowers.Power p : AncestorPowers.Power.values()) {
            int have = held(h, god, heart, p.token().getItem());
            h.assertTrue(have == 1, "the god has " + have + " of " + p.title + " (in his pack or at the Shrine), not one");
        }
        CompoundTag tag = new CompoundTag();
        d.save(tag);
        Devotion back = new Devotion();
        back.load(tag);
        h.assertTrue(back.devotion() == d.devotion() && back.gifted(AncestorPowers.Power.FURY), "from a save: devotion " + back.devotion()
                + " (was " + d.devotion() + "), the Fury handed " + back.gifted(AncestorPowers.Power.FURY));
        h.succeed();
    }

    private static int held(GameTestHelper h, ServerPlayer god, ShrineBlockEntity heart, Item item) {
        int n = 0;
        for (int i = 0; i < god.getInventory().getContainerSize(); i++) {
            if (god.getInventory().getItem(i).is(item)) {
                n += god.getInventory().getItem(i).getCount();
            }
        }
        for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(heart.getBlockPos()).inflate(3))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    /** Hostile (Normal) batch: the zombies must live till the Hammer lands, a second later. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "khazukhostile")
    public static void grungnisHammerStrikesTheFoesAndSparesTheGodsOwnAndTheGround(GameTestHelper h) {
        ServerPlayer god = god(h, new BlockPos(2, 1, 2));
        ShrineBlockEntity heart = hold(h, god);
        BlockPos at = new BlockPos(10, 1, 10);
        Zombie near = still(h, at.offset(1, 0, 0)), edge = still(h, at.offset(0, 0, 4)), far = still(h, at.offset(0, 0, 9));
        Warrior mine = dawi(h, KRegistry.WARRIOR.get(), heart, at.offset(-1, 0, 0));
        Vec3 where = h.absoluteVec(Vec3.atBottomCenterOf(at));
        BlockState ground = h.getLevel().getBlockState(h.absolutePos(at.below()));
        h.assertTrue(AncestorPowers.hammer(heart, god, h.getLevel(), where) != null, "the Hammer fell with no devotion to pay for it");
        heart.devotion().add(AncestorPowers.HAMMER_COST + 5);
        h.assertTrue(AncestorPowers.hammer(heart, god, h.getLevel(), where) == null, "the Hammer was refused with "
                + heart.devotion().devotion() + " devotion");
        h.assertTrue(heart.devotion().devotion() == 5, "the Hammer cost " + (AncestorPowers.HAMMER_COST + 5 - heart.devotion().devotion()));
        List<GrungniHammer> hs = h.getLevel().getEntitiesOfClass(GrungniHammer.class, new AABB(where, where).inflate(1));
        h.assertTrue(hs.size() == 1, hs.size() + " Hammers falling");
        GrungniHammer hammer = hs.get(0);
        float nearHp = near.getHealth(), edgeHp = edge.getHealth();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(hammer.hitForTests >= 0, "the Hammer hasn't struck"))
                .thenExecute(() -> {
                    h.assertTrue(hammer.hitForTests == 2, "the Hammer struck " + hammer.hitForTests + ", not the two zombies in its reach");
                    h.assertTrue(near.isDeadOrDying() || near.getHealth() < nearHp, "the zombie beside the blow is untouched");
                    h.assertTrue(near.isDeadOrDying() || near.isOnFire(), "the zombie beside the blow isn't burning");
                    h.assertTrue(edge.isDeadOrDying() || edge.getHealth() < edgeHp, "the zombie at the edge is untouched");
                    h.assertTrue(far.getHealth() == far.getMaxHealth(), "the zombie out of reach was struck");
                    h.assertTrue(mine.getHealth() == mine.getMaxHealth() && !mine.isOnFire(), "the god's own Warrior was struck: " + mine.getHealth());
                    h.assertTrue(god.getHealth() == god.getMaxHealth(), "the god was struck");
                    h.assertTrue(h.getLevel().getBlockState(h.absolutePos(at.below())) == ground, "the Hammer broke the ground");
                })
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void theRunePriestsBlessThePowersAndTheThroneLowersTheirPrice(GameTestHelper h) {
        ServerPlayer god = god(h, new BlockPos(4, 1, 4));
        ShrineBlockEntity heart = hold(h, god);
        Vec3 here = god.position();
        h.assertTrue(AncestorPowers.blessing(h.getLevel(), god.getUUID(), here, null) == 1f, "a blessing with no rune-priest near");
        Runesmith smith = dawi(h, KRegistry.RUNESMITH.get(), heart, new BlockPos(6, 1, 4));
        float s = AncestorPowers.blessing(h.getLevel(), god.getUUID(), here, null);
        h.assertTrue(s == AncestorPowers.SMITH_BLESSING, "a Runesmith's blessing is " + s);
        Runelord lord = dawi(h, KRegistry.RUNELORD.get(), heart, new BlockPos(21, 1, 4));
        Vec3 there = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(21, 1, 5)));
        float l = AncestorPowers.blessing(h.getLevel(), god.getUUID(), here, there);
        h.assertTrue(l == AncestorPowers.LORD_BLESSING, "a Runelord by where the Hammer lands blesses it " + l);
        GrungniHammer blessed = GrungniHammer.of(h.getLevel(), god, there, 0f, l);
        h.assertTrue(blessed.radius() > GrungniHammer.RADIUS, "a blessed Hammer reaches " + blessed.radius());
        smith.discard();
        lord.discard();
        var throne = h.spawn(KRegistry.THRONE.get(), new BlockPos(4, 1, 8));
        throne.setOwner(god.getUUID());
        throne.interact(god, net.minecraft.world.InteractionHand.MAIN_HAND, throne.position());
        int c = AncestorPowers.cost(AncestorPowers.Power.HAMMER, god);
        h.assertTrue(god.getVehicle() == throne && c < AncestorPowers.HAMMER_COST, "from the Throne the Hammer costs " + c + " (riding "
                + god.getVehicle() + ")");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void valayasWardHealsAndWardsTheGodAndHisDawiAndNoOneElse(GameTestHelper h) {
        ServerPlayer god = god(h, new BlockPos(10, 1, 10));
        ShrineBlockEntity heart = hold(h, god);
        Miner mine = dawi(h, KRegistry.MINER.get(), heart, new BlockPos(12, 1, 10));
        Miner stranger = h.spawn(KRegistry.MINER.get(), new BlockPos(13, 1, 10));
        stranger.setNoAi(true);
        Zombie foe = still(h, new BlockPos(14, 1, 14));
        mine.setHealth(10);
        heart.devotion().add(AncestorPowers.WARD_COST);
        h.assertTrue(AncestorPowers.ward(heart, god, h.getLevel()) == null, "the Ward was refused");
        h.assertTrue(heart.devotion().devotion() == 0, "the Ward cost " + (AncestorPowers.WARD_COST - heart.devotion().devotion()));
        h.assertTrue(mine.getHealth() >= 10 + AncestorPowers.WARD_HEAL - 0.01f, "the Ward healed the god's Miner to " + mine.getHealth());
        h.assertTrue(AncestorPowers.warded(mine) && AncestorPowers.warded(god), "warded: the Miner " + AncestorPowers.warded(mine) + ", the god "
                + AncestorPowers.warded(god));
        h.assertTrue(!AncestorPowers.warded(stranger) && !AncestorPowers.warded(foe), "the Ward fell on a Miner of no hold or a zombie");
        mine.setHealth(30);
        stranger.setHealth(30);
        mine.hurtServer(h.getLevel(), h.getLevel().damageSources().mobProjectile(foe, foe), 6f);
        mine.hurtServer(h.getLevel(), h.getLevel().damageSources().inFire(), 6f);
        h.assertTrue(mine.getHealth() == 30, "a shot or the fire got through the Ward: " + mine.getHealth());
        mine.hurtServer(h.getLevel(), h.getLevel().damageSources().magic(), 10f);
        stranger.hurtServer(h.getLevel(), h.getLevel().damageSources().magic(), 10f);
        h.assertTrue(Math.abs(mine.getHealth() - (30 - 10 * AncestorPowers.WARD_TAKEN)) < 0.01f && stranger.getHealth() == 20,
                "a blow of 10: the warded Miner at " + mine.getHealth() + ", the stranger at " + stranger.getHealth());
        AncestorPowers.lift(mine);
        h.assertTrue(!AncestorPowers.warded(mine), "the Ward didn't lift");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40, batch = "khazuk")
    public static void grimnirsFuryRagesTheGodsDawiAndTheGodAndLifts(GameTestHelper h) {
        ServerPlayer god = god(h, new BlockPos(10, 1, 10));
        ShrineBlockEntity heart = hold(h, god);
        Warrior mine = dawi(h, KRegistry.WARRIOR.get(), heart, new BlockPos(12, 1, 10));
        Quarreller shooter = dawi(h, KRegistry.QUARRELLER.get(), heart, new BlockPos(12, 1, 12));
        Warrior stranger = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(12, 1, 14));
        stranger.setNoAi(true);
        Quarreller other = h.spawn(KRegistry.QUARRELLER.get(), new BlockPos(8, 1, 12));
        other.setNoAi(true);
        double blow = mine.getAttributeValue(Attributes.ATTACK_DAMAGE);
        heart.devotion().add(AncestorPowers.FURY_COST);
        h.assertTrue(AncestorPowers.fury(heart, god, h.getLevel()) == null, "the Fury was refused");
        h.assertTrue(mine.furyUp() && shooter.furyUp() && !stranger.furyUp() && !other.furyUp(), "raging: the god's Warrior " + mine.furyUp()
                + ", his Quarreller " + shooter.furyUp() + ", a stranger " + stranger.furyUp() + ", another's Quarreller " + other.furyUp());
        double raged = mine.getAttributeValue(Attributes.ATTACK_DAMAGE);
        h.assertTrue(raged >= blow * (1 + AncestorPowers.FURY_DAMAGE) - 1e-3, "the raging Warrior's blow is " + raged + ", from " + blow);
        h.assertTrue(mine.isStubborn() && !stranger.isStubborn(), "fearless: the raging Warrior stubborn at full health " + mine.isStubborn());
        h.assertTrue(mine.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1, "the raging Warrior can be knocked back");
        h.assertTrue(god.hasEffect(MobEffects.STRENGTH), "the god doesn't burn with Grimnir's Fury");
        Zombie a = still(h, new BlockPos(20, 1, 12)), b = still(h, new BlockPos(20, 1, 6));
        float a0 = a.getHealth(), b0 = b.getHealth();
        shooter.shoot(h.getLevel(), a);
        other.shoot(h.getLevel(), b);
        float raging = a0 - a.getHealth(), plain = b0 - b.getHealth();
        h.assertTrue(raging > plain * 1.3f, "the raging Quarreller's bolt did " + raging + ", a calm one's " + plain);
        AncestorPowers.lift(mine);
        h.assertTrue(!mine.furyUp() && Math.abs(mine.getAttributeValue(Attributes.ATTACK_DAMAGE) - blow) < 1e-3,
                "the Fury lifted, the Warrior still hits for " + mine.getAttributeValue(Attributes.ATTACK_DAMAGE) + " (was " + blow + ")");
        h.succeed();
    }
}
