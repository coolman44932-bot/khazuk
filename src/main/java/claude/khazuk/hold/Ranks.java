package claude.khazuk.hold;

import claude.khazuk.Dawi;
import claude.khazuk.Hammerer;
import claude.khazuk.Ironbreaker;
import claude.khazuk.KRegistry;
import claude.khazuk.Longbeard;
import claude.khazuk.Warrior;
import claude.khazuk.Marksman;
import claude.khazuk.Quarreller;
import claude.khazuk.ShrineBlockEntity;
import claude.khazuk.Thunderer;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

/**
 * D6: the hold's missile troops, raised by its Barracks. With a Barracks dug, every {@value #RAISE_SECONDS} s the Shrine brings up one more
 * Quarreller or Thunderer (whichever the hold has fewer of) out of {@value #COST} food, until it keeps {@value #EACH} of each. They keep near
 * the Shrine as the hold's people do and shoot what comes within their range (Marksman), over the hold's walls when it's walled. Counted
 * among the loaded ones round the Shrine; not on the engine's roll (nor its muster), as the Runesmith isn't.
 */
public final class Ranks {

    public static final int RAISE_SECONDS = 30, EACH = 4, COST = 8;
    /** 0.11: with a Great Hall, a Longbeard every {@value #LONGBEARD_SECONDS} s for {@value #LONGBEARD_COST} food, {@value #LONGBEARD_EACH} of each loadout. */
    public static final int LONGBEARD_SECONDS = 60, LONGBEARD_EACH = 2, LONGBEARD_COST = 12;
    /**
     * 0.14: with a Mine, an Ironbreaker every {@value #ELITE_SECONDS} s for {@value #ELITE_COST} food until it keeps {@value #IRONBREAKER_EACH}, posted
     * on guard in the Mine; with a King's Throne Room, a Hammerer the same way until it keeps {@value #HAMMERER_EACH}, the god's guard.
     */
    public static final int ELITE_SECONDS = 60, ELITE_COST = 15, IRONBREAKER_EACH = 4, HAMMERER_EACH = 4;
    /** 0.14.2: with a Shrine of Grimnir, a Troll Slayer every {@value #ELITE_SECONDS} s for {@value #SLAYER_COST} food until it keeps {@value #SLAYER_EACH}. */
    public static final int SLAYER_COST = 12, SLAYER_EACH = 4;

    private int clock;

    /**
     * Who the hold's rooms have raised, by kind (saved): the hold keeps its share by this roll, not by who is in sight of its Shrine, so a
     * unit led away on follow, or out of the loaded chunks, still counts (the bug hunt of 2d0b04c: they were raised again and again).
     * Struck off when he dies (Dawi#die). A hold saved before the roll seeds it once from those round its Shrine.
     */
    private final java.util.Map<String, java.util.Set<java.util.UUID>> roll = new java.util.HashMap<>();
    private boolean seeded;

    static String key(EntityType<?> type) {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    }

    private static EntityType<? extends Dawi> typeOf(Class<? extends Dawi> kind) {
        if (kind == Quarreller.class) {
            return KRegistry.QUARRELLER.get();
        }
        if (kind == Thunderer.class) {
            return KRegistry.THUNDERER.get();
        }
        if (kind == Ironbreaker.class) {
            return KRegistry.IRONBREAKER.get();
        }
        if (kind == Hammerer.class) {
            return KRegistry.HAMMERER.get();
        }
        throw new IllegalArgumentException("no rank " + kind.getSimpleName());
    }

    /** {@code d} on the hold's roll of the ranks its rooms raised. */
    public void enlist(Dawi d) {
        roll.computeIfAbsent(key(d.getType()), k -> new java.util.HashSet<>()).add(d.getUUID());
    }

    /** {@code id} off the roll (he died). */
    public void forget(java.util.UUID id) {
        roll.values().forEach(s -> s.remove(id));
    }

    /** How many of {@code type} the hold's rooms have raised and still has. */
    public int rolled(EntityType<?> type) {
        java.util.Set<java.util.UUID> s = roll.get(key(type));
        return s == null ? 0 : s.size();
    }

    /** How many of {@code type} the hold's rooms have raised and still has (a Warrior grown into a Longbeard isn't of them). */
    public static int count(ServerLevel level, ShrineBlockEntity hold, EntityType<?> type) {
        return hold.ranks().rolled(type);
    }

    /** How many of {@code kind} the hold's rooms have raised. */
    public static int count(ServerLevel level, ShrineBlockEntity hold, Class<? extends Dawi> kind) {
        return hold.ranks().rolled(typeOf(kind));
    }

    /** A hold saved before the roll: those of the ranks round its Shrine now go on it, once. */
    private void seed(ServerLevel level, ShrineBlockEntity hold) {
        seeded = true;
        for (Dawi d : level.getEntitiesOfClass(Dawi.class, new AABB(hold.getBlockPos()).inflate(ShrineBlockEntity.RADIUS + 16),
                m -> m.isAlive() && m.settlement() == hold && (m instanceof Marksman || m instanceof Ironbreaker || m instanceof Hammerer
                        || m instanceof claude.khazuk.Slayer
                        || m instanceof Longbeard l && !l.veteran()))) {
            enlist(d);
        }
    }

    public void save(net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.nbt.CompoundTag r = new net.minecraft.nbt.CompoundTag();
        roll.forEach((k, ids) -> {
            net.minecraft.nbt.ListTag l = new net.minecraft.nbt.ListTag();
            for (java.util.UUID id : ids) {
                l.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
            }
            r.put(k, l);
        });
        tag.put("ranks_roll", r);
        tag.putBoolean("ranks_seeded", seeded);
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        roll.clear();
        net.minecraft.nbt.CompoundTag r = tag.getCompoundOrEmpty("ranks_roll");
        for (String k : r.keySet()) {
            java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
            for (net.minecraft.nbt.Tag t : r.getListOrEmpty(k)) {
                t.asString().ifPresent(v -> {
                    try {
                        ids.add(java.util.UUID.fromString(v));
                    } catch (IllegalArgumentException ignored) {
                    }
                });
            }
            roll.put(k, ids);
        }
        seeded = tag.getBooleanOr("ranks_seeded", false);
    }

    /** The Shrine's second: one more of the fewer kind, every {@value #RAISE_SECONDS} s, while the Barracks stands and the food lasts. */
    public void second(ServerLevel level, ShrineBlockEntity hold) {
        if (!seeded) {
            seed(level, hold);
        }
        ++clock;
        if (clock % RAISE_SECONDS == 0 && hold.has(HoldRooms.BARRACKS)) {
            raise(level, hold);
        }
        if (clock % LONGBEARD_SECONDS == 0 && hold.has(HoldRooms.GREAT_HALL)) {
            raiseLongbeard(level, hold);
        }
        if (clock % ELITE_SECONDS == 20 && hold.has(Rooms.Function.OREMINE)) {
            raiseIronbreaker(level, hold);
        }
        if (clock % ELITE_SECONDS == 40 && hold.has(HoldRooms.KEEP)) {
            raiseHammerer(level, hold);
        }
        if (clock % ELITE_SECONDS == 50 && hold.has(HoldRooms.SLAYER_SHRINE)) {
            raiseSlayer(level, hold);
        }
    }

    /** The first room of the hold's with {@code f}, or null. */
    public static Building roomOf(ShrineBlockEntity hold, Rooms.Function f) {
        for (Building b : hold.buildings()) {
            Rooms.Room r = Rooms.get(b.room());
            if (r != null && r.function() == f) {
                return b;
            }
        }
        return null;
    }

    /** Where one stands in the middle of {@code b}: its centre, at its floor. */
    public static BlockPos middleOf(Building b) {
        var c = b.box().getCenter();
        return new BlockPos(c.getX(), b.standingY(), c.getZ());
    }

    /** One more Ironbreaker now, posted in the Mine (the clock's minute, and the tests), while the hold keeps fewer than its share. Whether one came up. */
    public boolean raiseIronbreaker(ServerLevel level, ShrineBlockEntity hold) {
        Building mine = roomOf(hold, Rooms.Function.OREMINE);
        int n = count(level, hold, Ironbreaker.class);
        if (mine == null || n >= IRONBREAKER_EACH || hold.food() < ELITE_COST) {
            return false;
        }
        Ironbreaker d = KRegistry.IRONBREAKER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(ELITE_COST, "Ironbreakers raised");
        d.bindTo(hold);
        hold.ranks().enlist(d);
        d.guardAt(middleOf(mine));
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (n == 0) {
            hold.tellOwner(hold.settlementName() + ": the first Ironbreaker comes up in gromril head to toe, bombs at his belt, and goes down to "
                    + "stand guard in the Mine. What climbs out of the deeps meets him first.", ChatFormatting.GOLD);
        }
        return true;
    }

    /** One more Troll Slayer now, sworn at the Shrine of Grimnir (the clock's minute, and the tests), while the hold keeps fewer than its share. */
    public boolean raiseSlayer(ServerLevel level, ShrineBlockEntity hold) {
        int n = rolled(KRegistry.TROLL_SLAYER.get());
        Building shrine = roomOf(hold, HoldRooms.SLAYER_SHRINE);
        if (shrine == null || n >= SLAYER_EACH || hold.food() < SLAYER_COST) {
            return false;
        }
        claude.khazuk.Slayer d = KRegistry.TROLL_SLAYER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(SLAYER_COST, "Troll Slayers sworn");
        d.bindTo(hold);
        hold.ranks().enlist(d);
        BlockPos at = middleOf(shrine);                          // he comes up in the Shrine of Grimnir, sworn
        d.emergeAt(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(d);
        if (n == 0 && rolled(KRegistry.GIANT_SLAYER.get()) + rolled(KRegistry.DRAGON_SLAYER.get()) == 0) {
            hold.tellOwner(hold.settlementName() + ": at the Shrine of Grimnir a Dwarf shaves his head, dyes his crest and swears the Slayer's oath: "
                    + "the first Troll Slayer. He seeks the biggest foe there is. Slay a giant of one and he is a Giant Slayer; slay a monster, "
                    + "and he is the hold's Dragon Slayer.", ChatFormatting.GOLD);
        }
        return true;
    }

    /** One more Hammerer now, at the Throne Room's post (the clock's minute, and the tests), while the hold keeps fewer than its share. Whether one came up. */
    public boolean raiseHammerer(ServerLevel level, ShrineBlockEntity hold) {
        Building throne = roomOf(hold, HoldRooms.KEEP);
        int n = count(level, hold, Hammerer.class);
        if (throne == null || n >= HAMMERER_EACH || hold.food() < ELITE_COST) {
            return false;
        }
        Hammerer d = KRegistry.HAMMERER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(ELITE_COST, "Hammerers raised");
        d.bindTo(hold);
        hold.ranks().enlist(d);
        d.setPost(middleOf(throne));
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (n == 0) {
            hold.tellOwner(hold.settlementName() + ": the first Hammerer swears his oath at the throne, great hammer on his shoulder. While you walk "
                    + "in the hold the King's Guard keeps at your side, and what strikes you answers to them.", ChatFormatting.GOLD);
        }
        return true;
    }

    /** One more Longbeard of the fewer loadout now (the clock's minute, and the tests), while the hold keeps fewer than its share. Whether one came up. */
    public boolean raiseLongbeard(ServerLevel level, ShrineBlockEntity hold) {
        int shield = count(level, hold, KRegistry.LONGBEARD.get()), great = count(level, hold, KRegistry.LONGBEARD_GREAT.get());
        if (shield >= LONGBEARD_EACH && great >= LONGBEARD_EACH || hold.food() < LONGBEARD_COST) {
            return false;
        }
        EntityType<? extends Longbeard> kind = shield <= great ? KRegistry.LONGBEARD.get() : KRegistry.LONGBEARD_GREAT.get();
        Longbeard d = kind.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(LONGBEARD_COST, "Longbeards raised");
        d.bindTo(hold);
        hold.ranks().enlist(d);
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (shield + great == 0) {
            hold.tellOwner(hold.settlementName() + ": the Great Hall's old ones come up, grumbling: the first Longbeard. Where he stands, the Dawi round "
                    + "him hold firm whatever their wounds.", ChatFormatting.GOLD);
        }
        return true;
    }

    /**
     * A Warrior's beard has grown long in the hold's wars: he is a Longbeard now, where he stands, with his orders (his guard post too).
     * The Warrior goes back to the hold the engine's way (Shrine#absorb: off the roll, out of a crew, or a muster's defender back into the
     * reserve), so the engine's count stays whole; the Longbeard is the hold's own beside it, as the Great Hall's are, not counted against
     * what the Hall raises (the review of f66dd12).
     */
    public static Longbeard promote(ServerLevel level, ShrineBlockEntity hold, Warrior w) {
        Longbeard d = KRegistry.LONGBEARD.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return null;
        }
        d.bindTo(hold);
        d.snapTo(w.getX(), w.getY(), w.getZ(), w.getYRot(), w.getXRot());
        d.setYHeadRot(w.getYHeadRot());
        if (w.hasCustomName()) {
            d.setCustomName(w.getCustomName());
        }
        d.setHealth(d.getMaxHealth() * Math.max(0.1f, w.getHealth() / w.getMaxHealth()));
        d.copyKills(w);
        d.takeOrders(w);
        d.markVeteran();
        hold.absorb(w);
        w.discard();
        level.addFreshEntity(d);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, d.getX(), d.getY() + 1, d.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
        hold.tellOwner(hold.settlementName() + ": a Warrior has felled " + w.kills() + " foes in the hold's wars, and his beard has grown long: "
                + "he is a Longbeard now.", ChatFormatting.GOLD);
        return d;
    }

    /** One more of the fewer kind now (the clock's own second, and the tests). Whether one came up. */
    public boolean raise(ServerLevel level, ShrineBlockEntity hold) {
        int q = count(level, hold, Quarreller.class), t = count(level, hold, Thunderer.class);
        if (q >= EACH && t >= EACH || hold.food() < COST) {
            return false;
        }
        EntityType<? extends Dawi> kind = q <= t ? KRegistry.QUARRELLER.get() : KRegistry.THUNDERER.get();
        Dawi d = kind.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (d == null) {
            return false;
        }
        hold.ration().spendFood(COST, (q <= t ? "Quarrellers" : "Thunderers") + " raised");
        d.bindTo(hold);
        hold.ranks().enlist(d);
        d.emergeAt(hold.getBlockPos().getX() + 0.5, hold.getBlockPos().getY() + 1, hold.getBlockPos().getZ() + 0.5);
        level.addFreshEntity(d);
        if (q + t == 0) {
            hold.tellOwner(hold.settlementName() + ": the Barracks sends up its first " + (q <= t ? "Quarreller, crossbow on his shoulder"
                    : "Thunderer, handgun primed") + ". The hold's walls have guns now.", ChatFormatting.GOLD);
        }
        return true;
    }
}
