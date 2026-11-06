package claude.khazuk.hold;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.BuildingBook;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * D5, runes (docs/plan.md section 3): what the hold's Runesmith strikes at the rune anvil of its Runic Forge. Kept on the Shrine, saved with it.
 * <ul>
 * <li><b>The Runesmith</b> comes up out of the stone at the anvil once the forge is dug, and again {@value #RESPAWN_SECONDS} s after he falls.
 * He stands his forge (guard).</li>
 * <li><b>The hold's runes,</b> each to three, as in the lore, on every Dwarf of the hold: the Rune of Iron (+{@value #IRON_ARMOR} armour a
 * rune), the Rune of Cleaving (+{@value #CLEAVE_DAMAGE} a blow a rune), the Rune of Warding (blasts, fire and magic do {@value #WARD_CUT}
 * less a rune). The god picks which at the anvil and pays in gold ({@value #HOLD_COST} for the first rune, twice that for the second,
 * three times for the third).</li>
 * <li><b>The god's own kit:</b> a weapon handed over at the anvil gets a Rune of Might (+{@value #MIGHT_DAMAGE} a blow), a piece of armour
 * a Rune of Stone (+{@value #STONE_ARMOR} armour and toughness), each to three ({@value #KIT_COST} gold a rune, times its number). The
 * Runesmith keeps it while he works and puts it back on the anvil when the rune is struck.</li>
 * <li>A rune takes him {@value #STRIKE_SECONDS} s of hammering at the anvil; with no Runesmith there the work waits, and the kit with it.</li>
 * </ul>
 */
public final class Runes {

    public enum Kind {
        IRON("Rune of Iron", "every Dwarf of the hold is armoured the better"),
        CLEAVING("Rune of Cleaving", "every Dwarf of the hold strikes the harder"),
        WARDING("Rune of Warding", "blasts, fire and magic do the hold's Dwarfs less harm"),
        MIGHT("Rune of Might", "your weapon strikes the harder"),
        STONE("Rune of Stone", "your armour turns blows the better");

        final String title, what;

        Kind(String title, String what) {
            this.title = title;
            this.what = what;
        }

        public String title() {
            return title;
        }

        boolean onKit() {
            return this == MIGHT || this == STONE;
        }
    }

    public static final int MAX = 3;
    public static final double IRON_ARMOR = 2, CLEAVE_DAMAGE = 1.5, WARD_CUT = 0.2, MIGHT_DAMAGE = 2, STONE_ARMOR = 1;
    public static final int HOLD_COST = 40, KIT_COST = 30, STRIKE_SECONDS = 10, RESPAWN_SECONDS = 120;
    /** D5b: runes struck before the Runesmith is raised to Runelord, and the Anvil of Doom comes up with him. */
    public static final int TO_RUNELORD = 5;
    /** How near the anvil the Runesmith has to be to work at it. */
    public static final double AT_ANVIL = 3.5;

    private int iron, cleaving, warding;
    private Kind chosen = Kind.IRON;
    /** The rune being struck now, the kit it goes into (empty for the hold's), the seconds of hammering left. */
    private @Nullable Kind job;
    /** What the job in hand was paid (saved): given back if the forge is gone before it's struck (the bug hunt of 2d0b04c). */
    private int paid;
    private ItemStack kit = ItemStack.EMPTY;
    private int left;
    private int respawnIn;
    /** Runes struck in all; whether the hold's smith is a Runelord now, and whether its Anvil of Doom has come up. */
    private int struck;
    private boolean lord, anvilGiven;
    private boolean dirty;

    public int level(Kind k) {
        return switch (k) {
            case IRON -> iron;
            case CLEAVING -> cleaving;
            case WARDING -> warding;
            default -> 0;
        };
    }

    public Kind chosen() {
        return chosen;
    }

    public int struck() {
        return struck;
    }

    /** Whether the hold's Runesmith has been raised to Runelord. */
    public boolean lord() {
        return lord;
    }

    public @Nullable Kind job() {
        return job;
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    /** The hold's rune {@code k} struck once more (tests, and the end of a strike). */
    public void strike(Kind k) {
        switch (k) {
            case IRON -> iron = Math.min(MAX, iron + 1);
            case CLEAVING -> cleaving = Math.min(MAX, cleaving + 1);
            case WARDING -> warding = Math.min(MAX, warding + 1);
            default -> {
            }
        }
        dirty = true;
    }

    /** Sneak-use at the anvil: the next of the hold's runes to strike. */
    public Kind cycle() {
        chosen = switch (chosen) {
            case IRON -> Kind.CLEAVING;
            case CLEAVING -> Kind.WARDING;
            default -> Kind.IRON;
        };
        dirty = true;
        return chosen;
    }

    /** What the next rune of {@code k} costs, on the hold or on a piece of kit with {@code has} runes already. */
    public static int cost(Kind k, int has) {
        return (k.onKit() ? KIT_COST : HOLD_COST) * (has + 1);
    }

    // ------------------------------------------------------------------ the god's kit

    /**
     * A kit rune's modifier id: its kind, the slot its piece is worn in, and its number. The slot is in it so two runed pieces never share
     * an id: an equipped item's modifiers are added and taken off the wearer by id (the review of a3aecff: two Stone-runed pieces replaced
     * each other, and taking one off stripped the other).
     */
    static Identifier runeId(Kind k, String slot, int n) {
        return Identifier.fromNamespaceAndPath(Khazuk.MODID, "rune_" + k.name().toLowerCase(java.util.Locale.ROOT) + "_" + slot + "_" + n);
    }

    /** The slot a piece of kit is worn in, by name: its armour slot, else the main hand. */
    static String slotOf(ItemStack stack) {
        var eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().isArmor() ? eq.slot().getSerializedName() : "mainhand";
    }

    /** The rune a piece of kit takes: Might for a weapon, Stone for armour, or none. */
    public static @Nullable Kind kitRune(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq != null && eq.slot().isArmor()) {
            return Kind.STONE;
        }
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var e : mods.modifiers()) {
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                return Kind.MIGHT;
            }
        }
        return null;
    }

    /** How many runes of {@code k} a piece of kit carries. */
    public static int kitRunes(ItemStack stack, Kind k) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        int n = 0;
        String slot = slotOf(stack);
        for (int i = 1; i <= MAX; i++) {
            Identifier id = runeId(k, slot, i);
            if (mods.modifiers().stream().anyMatch(e -> e.modifier().id().equals(id))) {
                n = i;
            }
        }
        return n;
    }

    /** One more rune of {@code k} into a piece of kit: its attribute modifiers, and a gold line of lore naming its runes. */
    public static void runeKit(ItemStack stack, Kind k) {
        int n = kitRunes(stack, k) + 1;
        if (n > MAX) {
            return;
        }
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        String where = slotOf(stack);
        if (k == Kind.MIGHT) {
            mods = mods.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(runeId(k, where, n), MIGHT_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        } else {
            var eq = stack.get(DataComponents.EQUIPPABLE);
            EquipmentSlotGroup slot = eq == null ? EquipmentSlotGroup.ARMOR : EquipmentSlotGroup.bySlot(eq.slot());
            mods = mods.withModifierAdded(Attributes.ARMOR, new AttributeModifier(runeId(k, where, n), STONE_ARMOR, AttributeModifier.Operation.ADD_VALUE), slot);
            mods = mods.withModifierAdded(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(runeId(k, where, n).withSuffix("_tough"), STONE_ARMOR,
                    AttributeModifier.Operation.ADD_VALUE), slot);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
        List<Component> lore = new ArrayList<>();
        ItemLore had = stack.get(DataComponents.LORE);
        if (had != null) {                                       // the item's own lore kept (the review of a3aecff), its old rune lines dropped
            for (Component line : had.lines()) {
                String s = line.getString();
                if (!s.startsWith(Kind.MIGHT.title) && !s.startsWith(Kind.STONE.title)) {
                    lore.add(line);
                }
            }
        }
        for (Kind each : List.of(Kind.MIGHT, Kind.STONE)) {
            int has = each == k ? n : kitRunes(stack, each);
            if (has > 0) {
                lore.add(Component.literal(each.title + " " + "I".repeat(has)).withStyle(ChatFormatting.GOLD));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    // ------------------------------------------------------------------ work at the anvil

    /**
     * The god at the anvil: {@code held} a piece of kit (its rune), else the hold's chosen rune. Paid from the hold's gold, the work starts
     * (the kit taken from {@code held}). The answer for the god's chat, and whether it began.
     */
    public Component begin(ShrineBlockEntity hold, ItemStack held) {
        if (job != null) {
            return Component.literal("The Runesmith is still at work on a " + job.title + ".").withStyle(ChatFormatting.YELLOW);
        }
        Kind k = kitRune(held);
        int has;
        if (k != null) {
            has = kitRunes(held, k);
        } else if (held.isEmpty()) {
            k = chosen;
            has = level(k);
        } else {
            return Component.literal("Runes go into a weapon or a piece of armour; or bring nothing, and the Runesmith strikes the hold's "
                    + chosen.title + ". Sneak to choose another.").withStyle(ChatFormatting.GRAY);
        }
        if (has >= MAX) {
            return Component.literal("Three is the most a " + k.title + " will hold: more and the runes would break each other.").withStyle(ChatFormatting.YELLOW);
        }
        int price = cost(k, has);
        if (hold.currency() < price) {
            return Component.literal("A " + k.title + " " + "I".repeat(has + 1) + " costs " + price + " gold; the hold has " + hold.currency() + ".")
                    .withStyle(ChatFormatting.YELLOW);
        }
        if (!hold.spendCurrency(price, "A rune struck")) {
            return Component.literal("The hold's gold fell short of " + price + ".").withStyle(ChatFormatting.YELLOW);
        }
        job = k;
        paid = price;
        left = STRIKE_SECONDS;
        if (k.onKit()) {
            kit = held.split(1);
        }
        dirty = true;
        return Component.literal("The Runesmith takes up his hammer: a " + k.title + " " + "I".repeat(has + 1) + " for " + price + " gold. "
                + capital(k.what) + ".").withStyle(ChatFormatting.GOLD);
    }

    /**
     * The god's own kit laid down for him at {@code x, y, z}: it waits however long he is away, and only he picks it up (the bug hunt of
     * 2d0b04c: it lay there five minutes and was gone, or anyone took it).
     */
    private static void keep(ServerLevel level, ShrineBlockEntity hold, double x, double y, double z, ItemStack stack) {
        net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level, x, y, z, stack, 0, 0.1, 0);
        e.setUnlimitedLifetime();
        if (hold.owner() != null) {
            e.setTarget(hold.owner());
        }
        e.setDefaultPickUpDelay();
        level.addFreshEntity(e);
    }

    private static String capital(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The forge's anvil, if the hold has a Runic Forge whose placement is known. */
    public static @Nullable BlockPos anvil(ShrineBlockEntity hold) {
        for (Building b : hold.buildings()) {
            if (!"dawi_runic_forge".equals(b.room())) {
                continue;
            }
            PlanPlacement pl = BuildingBook.placementOf(b);
            if (pl != null) {
                return pl.world(new BlockPos(9, 1, 6));
            }
        }
        return null;
    }

    /** The forge's local cell the Runesmith works from: a slab a step before the anvil (gen_halls.py), his blow square on its face. */
    static final BlockPos WORK_SPOT = new BlockPos(8, 1, 6);

    /** The work spot's cell in the world, or null without a forge whose placement is known. */
    public static @Nullable BlockPos workCell(ShrineBlockEntity hold) {
        for (Building b : hold.buildings()) {
            if (!"dawi_runic_forge".equals(b.room())) {
                continue;
            }
            PlanPlacement pl = BuildingBook.placementOf(b);
            if (pl != null) {
                return pl.world(WORK_SPOT);
            }
        }
        return null;
    }

    /**
     * Where he stands to work: on the slab's top, in the middle of its cell; null without a forge whose placement is known. A forge dug
     * before the slab was in its template (0.8.2) has air there: he lays the slab himself, so he never stands half a block over the
     * floor (the review of 0638f33: he bobbed there every second).
     */
    public static @Nullable Vec3 workSpot(ServerLevel level, ShrineBlockEntity hold) {
        BlockPos c = workCell(hold);
        if (c == null) {
            return null;
        }
        if (level.isLoaded(c) && level.getBlockState(c).isAir()) {
            level.setBlockAndUpdate(c, net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
        }
        boolean slab = level.getBlockState(c).getBlock() instanceof net.minecraft.world.level.block.SlabBlock;
        return new Vec3(c.getX() + 0.5, c.getY() + (slab ? 0.5 : 0), c.getZ() + 0.5);
    }

    /** A foe on him, now or a moment ago: he fights, and the hammering waits. */
    static boolean fighting(Runesmith smith) {
        return smith.getTarget() != null && smith.getTarget().isAlive()
                || smith.getLastHurtByMob() != null && smith.tickCount - smith.getLastHurtByMobTimestamp() < 100;
    }

    /** The hold's Runesmith near its anvil, if he's up. */
    public static @Nullable Runesmith runesmith(ServerLevel level, ShrineBlockEntity hold, BlockPos anvil) {
        for (Runesmith r : level.getEntitiesOfClass(Runesmith.class, new AABB(anvil).inflate(48))) {
            if (r.isAlive() && r.settlement() == hold) {
                return r;
            }
        }
        return null;
    }

    /** The Shrine's second: the Runesmith up, and his work at the anvil. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        BlockPos anvil = hold.has(HoldRooms.RUNIC_FORGE) ? anvil(hold) : null;
        if (anvil == null) {
            if (job != null) {                                    // the forge is gone: the kit back at the Shrine, the gold back in the hoard
                if (!kit.isEmpty()) {
                    keep(level, hold, hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5, kit);
                    kit = ItemStack.EMPTY;
                }
                if (paid > 0) {
                    hold.addCurrency(paid, "A rune not struck");
                    hold.tellOwner(hold.settlementName() + ": the Runic Forge is gone before the " + job.title + " was struck: "
                            + paid + " gold back to the hoard.", ChatFormatting.GOLD);
                }
                job = null;
                paid = 0;
                dirty = true;
            }
            return;
        }
        if (!level.isLoaded(anvil)) {
            return;
        }
        Runesmith smith = runesmith(level, hold, anvil);
        if (smith != null && lord && !(smith instanceof claude.khazuk.Runelord)) {
            smith = raise(level, hold, smith, anvil);                // raised to Runelord (D5b)
        }
        if (smith == null) {
            if (respawnIn > 0) {
                respawnIn--;
            } else {
                Runesmith r = (lord ? KRegistry.RUNELORD : KRegistry.RUNESMITH).get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (r != null) {
                    r.bindTo(hold);
                    Vec3 at = workSpot(level, hold);
                    r.emergeAt(at != null ? at.x : anvil.getX() + 0.5, at != null ? at.y : anvil.getY(), at != null ? at.z : anvil.getZ() - 1.5);
                    r.standAt(anvil);
                    level.addFreshEntity(r);
                    hold.tellOwner(hold.settlementName() + ": the " + (lord ? "Runelord" : "Runesmith") + " comes up out of the stone to the rune anvil.",
                            ChatFormatting.AQUA);
                    respawnIn = RESPAWN_SECONDS;
                    dirty = true;
                }
            }
            return;
        }
        respawnIn = RESPAWN_SECONDS;
        if (job == null) {
            smith.setDigging(false);
            return;
        }
        if (fighting(smith)) {                                     // a foe first: the rune waits, he isn't pinned to the anvil (the review of 0638f33)
            smith.setDigging(false);
            return;
        }
        Vec3 spot = workSpot(level, hold);
        if (spot == null) {
            spot = new Vec3(anvil.getX() + 0.5, anvil.getY(), anvil.getZ() - 1.5);
        }
        if (smith.distanceToSqr(spot) > 1.5 * 1.5) {
            smith.setDigging(false);
            smith.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
            return;
        }
        // on his spot, squarely: the work clip's blow comes down straight ahead of him (the detail session's handoff), so he stands in the
        // middle of the slab with his body turned to the anvil, not only his head
        smith.getNavigation().stop();
        smith.setPos(spot.x, spot.y, spot.z);
        float yaw = (float) (net.minecraft.util.Mth.atan2(anvil.getZ() + 0.5 - spot.z, anvil.getX() + 0.5 - spot.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90f;
        smith.setYRot(yaw);
        smith.setYBodyRot(yaw);
        smith.setYHeadRot(yaw);
        BlockPos cell = BlockPos.containing(spot.x, spot.y, spot.z);
        if (!cell.equals(smith.guardPos())) {
            smith.standAt(anvil);                                // his post is the slab, wherever he stood before
        }
        smith.setDigging(true);
        smith.getLookControl().setLookAt(Vec3.atCenterOf(anvil));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, anvil.getX() + 0.5, anvil.getY() + 1.1, anvil.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.05);
        level.playSound(null, anvil, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.35f, 1.4f + level.getRandom().nextFloat() * 0.3f);
        if (--left > 0) {
            dirty = true;
            return;
        }
        finish(level, hold, anvil);
        smith.setDigging(false);
    }

    /**
     * The Runesmith raised to Runelord where he stands (D5b): a Runelord in his place, whole, and the hold's Anvil of Doom laid beside the
     * rune anvil, once.
     */
    private Runesmith raise(ServerLevel level, ShrineBlockEntity hold, Runesmith smith, BlockPos anvil) {
        claude.khazuk.Runelord r = KRegistry.RUNELORD.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (r == null) {
            return smith;
        }
        r.bindTo(hold);
        r.snapTo(smith.getX(), smith.getY(), smith.getZ(), smith.getYRot(), 0f);
        if (smith.hasCustomName()) {
            r.setCustomName(smith.getCustomName());
        }
        r.standAt(anvil);
        smith.discard();
        level.addFreshEntity(r);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, r.getX(), r.getY() + 1, r.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
        level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.5f, 0.5f);
        String gift = "";
        if (!anvilGiven) {
            anvilGiven = true;
            net.minecraft.world.entity.item.ItemEntity relic = new net.minecraft.world.entity.item.ItemEntity(level, anvil.getX() + 0.5,
                    anvil.getY() + 1.1, anvil.getZ() + 0.5, new ItemStack(KRegistry.ANVIL_OF_DOOM_ITEM.get()));
            relic.setUnlimitedLifetime();                        // it waits for its god however long he's away (the review of 2f93cc2)
            level.addFreshEntity(relic);
            gift = " He brings up the hold's oldest relic with him: the Anvil of Doom lies by the rune anvil. Set it down, stand on it, and strike it.";
        }
        hold.tellOwner(hold.settlementName() + ": " + struck + " runes struck, and the Runesmith is a Runelord now." + gift, ChatFormatting.GOLD);
        dirty = true;
        return r;
    }

    private void finish(ServerLevel level, ShrineBlockEntity hold, BlockPos anvil) {
        Kind k = job;
        job = null;
        dirty = true;
        level.playSound(null, anvil, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1f, 0.8f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, anvil.getX() + 0.5, anvil.getY() + 1.2, anvil.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
        if (k == null) {
            return;
        }
        if (k.onKit()) {
            if (kit.isEmpty()) {
                return;
            }
            runeKit(kit, k);
            String name = kit.getHoverName().getString();
            keep(level, hold, anvil.getX() + 0.5, anvil.getY() + 1.1, anvil.getZ() + 0.5, kit);
            kit = ItemStack.EMPTY;
            paid = 0;
            countStruck();
            hold.tellOwner(hold.settlementName() + ": the " + k.title + " is struck into your " + name + ". It lies on the anvil.", ChatFormatting.GOLD);
        } else {
            strike(k);
            countStruck();
            hold.tellOwner(hold.settlementName() + ": the " + k.title + " " + "I".repeat(level(k)) + " is struck: " + k.what + ".", ChatFormatting.GOLD);
        }
    }

    /** The Shrine is gone: the kit at the anvil is dropped where it stood, never lost (the review of a3aecff). */
    public void dropKit(ServerLevel level, BlockPos at) {
        if (!kit.isEmpty()) {
            Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, kit);
            kit = ItemStack.EMPTY;
            job = null;
            dirty = true;
        }
    }

    /** One more rune struck; at {@value #TO_RUNELORD} the Runesmith is to be raised (the next second, where he stands). */
    private void countStruck() {
        struck++;
        if (struck >= TO_RUNELORD) {
            lord = true;
        }
    }

    /** Tests: the work finished at once. */
    public void finishForTests(ServerLevel level, ShrineBlockEntity hold, BlockPos anvil) {
        finish(level, hold, anvil);
    }

    // ------------------------------------------------------------------ saves

    public void save(CompoundTag tag) {
        tag.putInt("rune_iron", iron);
        tag.putInt("rune_cleaving", cleaving);
        tag.putInt("rune_warding", warding);
        tag.putString("rune_chosen", chosen.name());
        tag.putString("rune_job", job == null ? "" : job.name());
        tag.putInt("rune_paid", paid);
        tag.putInt("rune_left", left);
        tag.putInt("rune_respawn", respawnIn);
        tag.putInt("rune_struck", struck);
        tag.putBoolean("rune_lord", lord);
        tag.putBoolean("rune_anvil_given", anvilGiven);
    }

    public void load(CompoundTag tag) {
        iron = Math.max(0, Math.min(MAX, tag.getIntOr("rune_iron", 0)));
        cleaving = Math.max(0, Math.min(MAX, tag.getIntOr("rune_cleaving", 0)));
        warding = Math.max(0, Math.min(MAX, tag.getIntOr("rune_warding", 0)));
        chosen = kind(tag.getStringOr("rune_chosen", "IRON"), Kind.IRON);
        String j = tag.getStringOr("rune_job", "");
        job = j.isEmpty() ? null : kind(j, null);
        paid = Math.max(0, tag.getIntOr("rune_paid", 0));
        left = Math.max(0, tag.getIntOr("rune_left", 0));
        respawnIn = Math.max(0, tag.getIntOr("rune_respawn", 0));
        struck = Math.max(0, tag.getIntOr("rune_struck", 0));
        lord = tag.getBooleanOr("rune_lord", false);
        anvilGiven = tag.getBooleanOr("rune_anvil_given", false);
    }

    /** The kit at the anvil, saved by the Shrine itself (an item needs the level's registries: ShrineBlockEntity#saveAdditional). */
    public ItemStack kit() {
        return kit;
    }

    public void kit(ItemStack stack) {
        kit = stack;
    }

    private static @Nullable Kind kind(String s, @Nullable Kind or) {
        try {
            return Kind.valueOf(s);
        } catch (IllegalArgumentException e) {
            return or;
        }
    }
}
