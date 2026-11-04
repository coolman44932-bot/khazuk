package claude.khazuk.test;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Warrior;
import claude.khazuk.hold.HoldRooms;
import claude.khazuk.hold.Runes;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.room.RoomTemplates;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.test.GameTest;
import claude.settlementcore.test.GameTestHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * D5, runes (hold/Runes, hold/RuneEvents, Runesmith): the Runic Forge's template and its anvil; a forge brings the Runesmith up at the
 * anvil; the hold's rune is paid in gold, struck, and worn by every Dwarf (and a price the hold can't pay strikes nothing); a weapon's Rune
 * of Might and armour's Rune of Stone go into the kit beside its own damage and armour, three at most; the Rune of Warding turns a blast;
 * the runes come back from a save; and the Runesmith's floor, four zombies.
 */
@GameTestHolder(Khazuk.MODID)
public class RuneGameTests {

    private static final BlockPos HEART = new BlockPos(20, 1, 20);

    private static ShrineBlockEntity hold(GameTestHelper h) {
        h.setBlock(HEART, KRegistry.SHRINE.get().defaultBlockState());
        if (!(h.getLevel().getBlockEntity(h.absolutePos(HEART)) instanceof ShrineBlockEntity heart)) {
            throw h.assertionException("no Shrine's block entity at " + h.absolutePos(HEART));
        }
        return heart;
    }

    /** The hold given a Runic Forge, as a finished dig gives it (its door and facing kept: the anvil is found from them). */
    private static BlockPos forge(GameTestHelper h, ShrineBlockEntity heart) {
        Rooms.Room r = HoldRooms.room("dawi_runic_forge");
        BlockPos door = h.absolutePos(new BlockPos(1, 1, 7));
        PlanPlacement pl = PlanPlacement.compute(r, door, Direction.EAST);
        heart.addBuilding(new Building(r.id(), pl.box(), door, Direction.EAST), false);
        h.assertTrue(heart.has(HoldRooms.RUNIC_FORGE), "the hold doesn't have its Runic Forge");
        BlockPos anvil = Runes.anvil(heart);
        h.assertTrue(anvil != null && anvil.equals(pl.world(new BlockPos(9, 1, 6))), "the forge's anvil is at " + anvil);
        return anvil;
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theForgesTemplateIsWholeWithItsAnvil(GameTestHelper h) {
        Rooms.Room r = HoldRooms.room("dawi_runic_forge");
        List<RoomTemplates.Entry> cells = RoomTemplates.get(h.getLevel(), r.id());
        h.assertTrue(cells.size() == r.sizeX() * r.sizeY() * r.sizeZ(), "the forge's template has " + cells.size() + " cells");
        h.assertTrue(cells.stream().anyMatch(e -> e.local().equals(new BlockPos(9, 1, 6)) && e.state().is(Blocks.ANVIL)), "no anvil at the forge's station");
        h.assertTrue(r.stations().stream().anyMatch(s -> s.x() == 9 && s.y() == 1 && s.z() == 6 && s.role().equals("anvil")), "the forge has no anvil station: " + r.stations());
        h.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "khazuk")
    public static void aForgeBringsTheRunesmithUpAndHisRuneIsWornByTheHold(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.runes().second(h.getLevel(), heart);
        h.assertTrue(h.getLevel().getEntitiesOfClass(Runesmith.class, new AABB(h.absolutePos(HEART)).inflate(40)).isEmpty(), "a Runesmith with no forge");
        BlockPos anvil = forge(h, heart);
        heart.runes().second(h.getLevel(), heart);
        Runesmith smith = Runes.runesmith(h.getLevel(), heart, anvil);
        h.assertTrue(smith != null, "no Runesmith came up at the forge");
        Warrior w = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(3, 1, 20));
        w.bindTo(heart);
        double armour = w.getAttributeValue(Attributes.ARMOR), blow = w.getAttributeValue(Attributes.ATTACK_DAMAGE);
        heart.addCurrency(10 - heart.currency(), "the test");
        h.assertTrue(heart.runes().begin(heart, ItemStack.EMPTY) != null && heart.runes().job() == null && heart.currency() == 10,
                "a rune began with 10 gold");
        heart.addCurrency(200, "the test");
        heart.runes().begin(heart, ItemStack.EMPTY);
        h.assertTrue(heart.runes().job() == Runes.Kind.IRON && heart.currency() == 210 - Runes.cost(Runes.Kind.IRON, 0),
                "the Rune of Iron: job " + heart.runes().job() + ", gold " + heart.currency());
        heart.runes().finishForTests(h.getLevel(), heart, anvil);
        heart.runes().cycle();
        heart.runes().begin(heart, ItemStack.EMPTY);
        heart.runes().finishForTests(h.getLevel(), heart, anvil);
        h.assertTrue(heart.runes().level(Runes.Kind.IRON) == 1 && heart.runes().level(Runes.Kind.CLEAVING) == 1, "the hold's runes: iron "
                + heart.runes().level(Runes.Kind.IRON) + ", cleaving " + heart.runes().level(Runes.Kind.CLEAVING));
        h.runAfterDelay(45, () -> {
            h.assertTrue(Math.abs(w.getAttributeValue(Attributes.ARMOR) - armour - Runes.IRON_ARMOR) < 1e-6
                    && Math.abs(w.getAttributeValue(Attributes.ATTACK_DAMAGE) - blow - Runes.CLEAVE_DAMAGE) < 1e-6,
                    "the Warrior wears the runes: armour " + w.getAttributeValue(Attributes.ARMOR) + " (was " + armour + "), blow "
                            + w.getAttributeValue(Attributes.ATTACK_DAMAGE) + " (was " + blow + ")");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theGodsKitTakesRunesOfMightAndStoneBesideItsOwn(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        BlockPos anvil = forge(h, heart);
        heart.addCurrency(1000, "the test");
        ItemStack held = new ItemStack(Items.IRON_SWORD);
        h.assertTrue(Runes.kitRune(held) == Runes.Kind.MIGHT && Runes.kitRune(new ItemStack(Items.IRON_CHESTPLATE)) == Runes.Kind.STONE
                && Runes.kitRune(new ItemStack(Items.BREAD)) == null, "the runes kit takes");
        heart.runes().begin(heart, held);
        h.assertTrue(held.isEmpty() && heart.runes().job() == Runes.Kind.MIGHT, "the sword wasn't taken to the anvil");
        heart.runes().finishForTests(h.getLevel(), heart, anvil);
        List<ItemEntity> back = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(anvil).inflate(2));
        h.assertTrue(back.size() == 1 && back.get(0).getItem().is(Items.IRON_SWORD), "the sword isn't back on the anvil: " + back);
        ItemStack sword = back.get(0).getItem();
        h.assertTrue(Runes.kitRunes(sword, Runes.Kind.MIGHT) == 1, "the sword carries " + Runes.kitRunes(sword, Runes.Kind.MIGHT) + " runes");
        ItemAttributeModifiers mods = sword.get(DataComponents.ATTRIBUTE_MODIFIERS);
        double dmg = mods.modifiers().stream().filter(e -> e.attribute().is(Attributes.ATTACK_DAMAGE)).mapToDouble(e -> e.modifier().amount()).sum();
        double plain = new ItemStack(Items.IRON_SWORD).get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .filter(e -> e.attribute().is(Attributes.ATTACK_DAMAGE)).mapToDouble(e -> e.modifier().amount()).sum();
        h.assertTrue(Math.abs(dmg - plain - Runes.MIGHT_DAMAGE) < 1e-6, "the sword's damage: " + dmg + ", plain " + plain);
        h.assertTrue(sword.get(DataComponents.LORE) != null && sword.get(DataComponents.LORE).lines().get(0).getString().contains("Rune of Might"),
                "the sword's lore: " + sword.get(DataComponents.LORE));
        ItemStack plate = new ItemStack(Items.IRON_CHESTPLATE);
        for (int i = 0; i < 4; i++) {
            Runes.runeKit(plate, Runes.Kind.STONE);
        }
        h.assertTrue(Runes.kitRunes(plate, Runes.Kind.STONE) == Runes.MAX, "the plate carries " + Runes.kitRunes(plate, Runes.Kind.STONE) + " runes of stone, past three");
        // two runed pieces worn together both count: their runes' ids differ by slot (the review of a3aecff)
        ItemStack legs = new ItemStack(Items.IRON_LEGGINGS);
        legs.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(net.minecraft.network.chat.Component.literal("Thorin's"))));
        Runes.runeKit(legs, Runes.Kind.STONE);
        Zombie wearer = h.spawn(EntityType.ZOMBIE, new BlockPos(14, 1, 14));
        wearer.setNoAi(true);
        double bare = wearer.getAttributeValue(Attributes.ARMOR);
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        Runes.runeKit(chest, Runes.Kind.STONE);
        wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
        wearer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, legs);
        double plainArmour = new ItemStack(Items.IRON_CHESTPLATE).get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .filter(e -> e.attribute().is(Attributes.ARMOR)).mapToDouble(e -> e.modifier().amount()).sum()
                + new ItemStack(Items.IRON_LEGGINGS).get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                .filter(e -> e.attribute().is(Attributes.ARMOR)).mapToDouble(e -> e.modifier().amount()).sum();
        h.runAfterDelay(2, () -> {
            double worn = wearer.getAttributeValue(Attributes.ARMOR) - bare;
            h.assertTrue(Math.abs(worn - plainArmour - 2 * Runes.STONE_ARMOR) < 1e-6, "a runed chestplate and leggings give " + worn
                    + " armour, the plain pair " + plainArmour + ": both runes must count");
            h.assertTrue(legs.get(DataComponents.LORE).lines().get(0).getString().equals("Thorin's"), "the leggings' own lore was lost: " + legs.get(DataComponents.LORE));
            h.succeed();
        });
    }

    /** The god's kit at the anvil when the Shrine is broken: dropped where the Shrine stood, never lost. */
    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theKitAtTheAnvilIsNeverLost(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        forge(h, heart);
        heart.addCurrency(100, "the test");
        int gold = heart.currency();
        heart.runes().begin(heart, new ItemStack(Items.DIAMOND_SWORD));
        h.assertTrue(heart.runes().job() == Runes.Kind.MIGHT && heart.currency() == gold - Runes.cost(Runes.Kind.MIGHT, 0), "the sword's rune wasn't paid for");
        h.setBlock(HEART, Blocks.AIR);
        List<ItemEntity> dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(HEART)).inflate(2));
        h.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().is(Items.DIAMOND_SWORD), "the sword wasn't dropped where the Shrine stood: " + dropped);
        h.succeed();
    }

    /** The Rune of Warding: a blast does a Warrior of a warded hold {@value Runes#WARD_CUT} less a rune (his armour off, so it's exact). */
    @GameTest(template = "arena", timeoutTicks = 80, batch = "khazuk")
    public static void theRuneOfWardingTurnsABlast(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        heart.runes().strike(Runes.Kind.WARDING);
        heart.runes().strike(Runes.Kind.WARDING);
        Warrior warded = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(4, 1, 4)), bare = h.spawn(KRegistry.WARRIOR.get(), new BlockPos(10, 1, 4));
        warded.bindTo(heart);
        for (Warrior w : List.of(warded, bare)) {
            w.setNoAi(true);
            w.getAttribute(Attributes.ARMOR).setBaseValue(0);
            w.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        }
        h.runAfterDelay(45, () -> {
            float a0 = warded.getHealth(), b0 = bare.getHealth();
            warded.hurtServer(h.getLevel(), h.getLevel().damageSources().explosion(null, null), 10f);
            bare.hurtServer(h.getLevel(), h.getLevel().damageSources().explosion(null, null), 10f);
            float a = a0 - warded.getHealth(), b = b0 - bare.getHealth();
            h.assertTrue(Math.abs(a - b * (1 - 2 * Runes.WARD_CUT)) < 0.05f, "the blast did the warded " + a + ", the bare " + b);
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "khazuk")
    public static void theRunesComeBackFromASave(GameTestHelper h) {
        Runes r = new Runes();
        r.strike(Runes.Kind.IRON);
        r.strike(Runes.Kind.IRON);
        r.strike(Runes.Kind.WARDING);
        r.cycle();
        CompoundTag tag = new CompoundTag();
        r.save(tag);
        Runes back = new Runes();
        back.load(tag);
        h.assertTrue(back.level(Runes.Kind.IRON) == 2 && back.level(Runes.Kind.WARDING) == 1 && back.level(Runes.Kind.CLEAVING) == 0
                && back.chosen() == Runes.Kind.CLEAVING, "back from the save: iron " + back.level(Runes.Kind.IRON) + ", warding "
                + back.level(Runes.Kind.WARDING) + ", chosen " + back.chosen());
        h.succeed();
    }

    /** The Runesmith's floor: alone, he beats four zombies, landing the blows himself. Normal: the batch sets it. */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "khazukhostile")
    public static void oneRunesmithBeatsFourZombies(GameTestHelper h) {
        ShrineBlockEntity heart = hold(h);
        Runesmith smith = h.spawn(KRegistry.RUNESMITH.get(), new BlockPos(12, 1, 12));
        smith.bindTo(heart);
        Zombie[] z = new Zombie[4];
        for (int i = 0; i < 4; i++) {
            z[i] = h.spawn(EntityType.ZOMBIE, new BlockPos(17, 1, 9 + i * 2));
            z[i].setPersistenceRequired();
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(smith.isAlive(), "the Runesmith fell to four zombies");
                    for (Zombie each : z) {
                        h.assertTrue(each.isDeadOrDying(), "a zombie still stands at " + each.getHealth() + "; the Runesmith at " + smith.getHealth());
                    }
                })
                .thenExecute(() -> {
                    h.assertTrue(smith.attacksForTests >= 4, "the Runesmith landed " + smith.attacksForTests + " blows");
                    Khazuk.LOG.info("KHAZUK floor: a Runesmith beat 4 zombies with {}/{} health left", smith.getHealth(), smith.getMaxHealth());
                })
                .thenSucceed();
    }
}
