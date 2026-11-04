package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Runelord;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.AnvilOfDoom;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Runes;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * D5b, the Runelord and the Anvil of Doom (hold/Runes, Runelord, hold/AnvilOfDoom): five runes struck raise the Runesmith to Runelord where he
 * stands and bring the Anvil of Doom up beside the rune anvil, once; the Anvil's Rune of Wrath and Ruin strikes six foes at most, Hearth and
 * Home heals and wards the god's Dwarfs, Oath and Steel strengthens them; a rune of power cools for a minute, half that with the Runelord by;
 * a cooling rune passes to the next that's ready; the Anvil takes no harm; the hold's rune count comes back from a save; and the Runelord's
 * floor, an iron golem.
 */
@GameTestHolder(Khazuk.MODID)
public class AnvilGameTests {

    private static final BlockPos HEART = new BlockPos(20, 1, 20);

    @SuppressWarnings("removal")
    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        heart.claim(h.makeMockServerPlayerInLevel(), "Karak Test");
        return heart;
    }

    private static BlockPos forge(GameTestHelper h, ShrineBlockEntity heart) {
        Rooms.Room r = HoldRooms.room("dawi_runic_forge");
        BlockPos door = h.absolutePos(new BlockPos(1, 1, 7));
        PlanPlacement pl = PlanPlacement.compute(r, door, Direction.EAST);
        heart.addBuilding(new Building(r.id(), pl.box(), door, Direction.EAST), false);
        return Runes.anvil(heart);
    }

    private static AnvilOfDoom anvil(GameTestHelper h, ShrineBlockEntity heart, BlockPos at) {
        AnvilOfDoom a = h.spawn(KRegistry.ANVIL_OF_DOOM.get(), at);
        a.setOwner(heart.owner());
        return a;
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "khazuk")
    public static void fiveRunesRaiseTheRunelordAndBringTheAnvil(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        BlockPos at = forge(h, heart);
        heart.runes().second(h.getLevel(), heart);
        h.assertTrue(Runes.runesmith(h.getLevel(), heart, at) != null && !(Runes.runesmith(h.getLevel(), heart, at) instanceof Runelord), "no Runesmith");
        heart.addCurrency(5000, "the test");
        for (int i = 0; i < Runes.TO_RUNELORD; i++) {
            h.assertTrue(!heart.runes().lord(), "raised after " + i + " runes");
            heart.runes().begin(heart, ItemStack.EMPTY);
            heart.runes().finishForTests(h.getLevel(), heart, at);
            if (heart.runes().level(heart.runes().chosen()) >= Runes.MAX) {
                heart.runes().cycle();
            }
        }
        h.assertTrue(heart.runes().lord() && heart.runes().struck() == Runes.TO_RUNELORD, "after five runes: lord " + heart.runes().lord());
        heart.runes().second(h.getLevel(), heart);
        Runesmith now = Runes.runesmith(h.getLevel(), heart, at);
        h.assertTrue(now instanceof Runelord, "the smith wasn't raised: " + now);
        h.assertTrue(h.getLevel().getEntitiesOfClass(Runesmith.class, new AABB(at).inflate(48), e -> !(e instanceof Runelord)).isEmpty(),
                "the Runesmith is still about beside his Runelord");
        List<ItemEntity> gift = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3), e -> e.getItem().is(KRegistry.ANVIL_OF_DOOM_ITEM.get()));
        h.assertTrue(gift.size() == 1, "the Anvil of Doom didn't come up by the rune anvil: " + gift.size());
        heart.runes().second(h.getLevel(), heart);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3), e -> e.getItem().is(KRegistry.ANVIL_OF_DOOM_ITEM.get())).size() == 1,
                "a second Anvil of Doom came up");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void wrathAndRuinStrikesSixFoesAtMost(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        AnvilOfDoom a = anvil(h, heart, new BlockPos(6, 1, 6));
        Zombie[] z = new Zombie[8];
        for (int i = 0; i < z.length; i++) {
            z[i] = h.spawn(EntityType.ZOMBIE, new BlockPos(3 + i * 2, 1, 14));
            z[i].setNoAi(true);
        }
        a.strike(h.getLevel(), null, AnvilOfDoom.Power.WRATH);
        int struck = 0;
        for (Zombie each : z) {
            struck += each.getHealth() < each.getMaxHealth() ? 1 : 0;
        }
        h.assertTrue(struck == AnvilOfDoom.WRATH_TARGETS, "Wrath and Ruin struck " + struck + " of 8 zombies, not " + AnvilOfDoom.WRATH_TARGETS);
        h.assertTrue(a.cooling(AnvilOfDoom.Power.WRATH) == AnvilOfDoom.COOLDOWN_SECONDS * 20, "it cools for " + a.cooling(AnvilOfDoom.Power.WRATH) + " ticks");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void hearthAndOathBlessTheGodsDwarfsAndTheRunelordHastensThem(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        AnvilOfDoom a = anvil(h, heart, new BlockPos(6, 1, 6));
        Warrior mine = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(10, 1, 6)), stranger = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(12, 1, 6));
        mine.bindTo(heart);
        mine.setHealth(10);
        stranger.setHealth(10);
        a.strike(h.getLevel(), null, AnvilOfDoom.Power.HEARTH);
        h.assertTrue(mine.getHealth() >= 10 + AnvilOfDoom.HEARTH_HEAL - 0.01f && mine.hasEffect(MobEffects.RESISTANCE), "the god's Warrior: health "
                + mine.getHealth() + ", warded " + mine.hasEffect(MobEffects.RESISTANCE));
        h.assertTrue(stranger.getHealth() == 10 && !stranger.hasEffect(MobEffects.RESISTANCE), "a Warrior of no hold was blessed");
        Runelord lord = h.spawn(KRegistry.RUNELORD.get(), new BlockPos(8, 1, 8));
        lord.bindTo(heart);
        h.assertTrue(a.lordNear(), "the Runelord by the Anvil isn't seen");
        a.strike(h.getLevel(), null, AnvilOfDoom.Power.OATH);
        h.assertTrue(mine.hasEffect(MobEffects.STRENGTH) && mine.hasEffect(MobEffects.SPEED), "Oath and Steel didn't strengthen the god's Warrior");
        h.assertTrue(a.cooling(AnvilOfDoom.Power.OATH) == AnvilOfDoom.COOLDOWN_SECONDS * 10, "with the Runelord by it cools for "
                + a.cooling(AnvilOfDoom.Power.OATH) + " ticks, not half a minute");
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theAnvilTakesNoHarmAndItsRunesComeBackFromASave(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        AnvilOfDoom a = anvil(h, heart, new BlockPos(6, 1, 6));
        Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(9, 1, 6));
        z.setNoAi(true);
        float hp = a.getHealth();
        a.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(z), 50f);
        h.assertTrue(a.getHealth() == hp && a.isAlive(), "the Anvil took harm: " + a.getHealth());
        Runes r = new Runes();
        CompoundTag tag = new CompoundTag();
        r.save(tag);
        Runes back = new Runes();
        back.load(tag);
        h.assertTrue(!back.lord() && back.struck() == 0, "a fresh hold's runes came back changed");
        h.succeed();
    }

    /** The Runelord's floor: alone, he beats an iron golem that has turned on him. Normal: the batch sets it. */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneRunelordBeatsAnIronGolem(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Runelord lord = h.spawn(KRegistry.RUNELORD.get(), new BlockPos(10, 1, 10));
        lord.bindTo(heart);
        IronGolem golem = h.spawn(EntityType.IRON_GOLEM, new BlockPos(15, 1, 10));
        golem.setPersistenceRequired();
        golem.setTarget(lord);
        lord.setTarget(golem);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(lord.isAlive(), "the Runelord fell to an iron golem (it's at " + golem.getHealth() + ")");
                    h.assertTrue(golem.isDeadOrDying(), "the golem still stands at " + golem.getHealth() + "; the Runelord at " + lord.getHealth());
                })
                .thenExecute(() -> Khazuk.LOG.info("KHAZUK floor: a Runelord beat an iron golem with {}/{} health left", lord.getHealth(), lord.getMaxHealth()))
                .thenSucceed();
    }
}
