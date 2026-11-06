package claude.khazuk.god;

import claude.khazuk.Dawi;
import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import claude.khazuk.Runesmith;
import claude.khazuk.ShrineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * D7, the Ancestor Gods' powers, paid out of a hold's devotion (Devotion), each with its token item:
 * <ul>
 * <li><b>Grungni's Hammer</b> ({@value #HAMMER_COST}): a forge-hammer of light comes down where the god points, up to {@value #REACH}
 * blocks off (GrungniHammer): everything not his within its reach is struck, set alight and flung. No block is broken.</li>
 * <li><b>Valaya's Ward</b> ({@value #WARD_COST}): for {@value #WARD_TICKS} ticks the god, his Dawi and his mounts within
 * {@value #WARD_RADIUS} blocks of him are warded: fire and shots turned aside whole, every other blow halved; each is healed
 * {@value #WARD_HEAL} and put out at once.</li>
 * <li><b>Grimnir's Fury</b> ({@value #FURY_COST}): for {@value #FURY_TICKS} ticks every Dwarf of his within {@value #FURY_RADIUS} blocks
 * rages as a Slayer does: half again as hard a blow (and shot), a quarter faster on his feet, a third faster to reload, unmoved by blows,
 * and stubborn whatever his wounds (no fear). The god burns with it too: Strength II and Speed.</li>
 * </ul>
 * Stronger with a rune-priest of his near (within {@value #SMITH_NEAR} blocks of the god or where the Hammer lands): a Runesmith makes a
 * power {@value #SMITH_BLESSING}x (the Hammer's blow and reach, the Ward's and the Fury's time and reach), a Runelord
 * {@value #LORD_BLESSING}x. Called from the Throne of Power, a power costs a quarter less. The token remembers its hold, so a power called far
 * from it still draws on it.
 */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class AncestorPowers {

    public static final int HAMMER_COST = 30, WARD_COST = 40, FURY_COST = 40;
    public static final double REACH = 64, WARD_RADIUS = 12, FURY_RADIUS = 16, SMITH_NEAR = 16;
    public static final int WARD_TICKS = 600, FURY_TICKS = 600;
    public static final float WARD_TAKEN = 0.5f, WARD_HEAL = 8f;
    public static final double FURY_DAMAGE = 0.5, FURY_SPEED = 0.25, FURY_STEADY = 1.0;
    public static final float FURY_SHOT = 1.5f, FURY_RELOAD = 0.67f;
    public static final float SMITH_BLESSING = 1.25f, LORD_BLESSING = 1.5f, THRONE_PRICE = 0.75f;
    /** Ticks a token rests after a call (paid or refused): a spammed click asks the hold at most so often. */
    public static final int CALLED_COOLDOWN = 40, REFUSED_COOLDOWN = 20;

    /** The three powers: who grants it, its token, its price, and how it's called. */
    public enum Power {
        HAMMER("Grungni", "Grungni's Hammer", HAMMER_COST, "point it at the ground and use it, and his hammer comes down there."),
        WARD("Valaya", "Valaya's Ward", WARD_COST, "use it, and she shields you and your Dawi round you."),
        FURY("Grimnir", "Grimnir's Fury", FURY_COST, "use it, and your Dawi round you rage like Slayers.");

        public final String god, title, how;
        public final int cost;

        Power(String god, String title, int cost, String how) {
            this.god = god;
            this.title = title;
            this.cost = cost;
            this.how = how;
        }

        public ItemStack token() {
            return switch (this) {
                case HAMMER -> new ItemStack(KRegistry.GRUNGNIS_HAMMER.get());
                case WARD -> new ItemStack(KRegistry.VALAYAS_WARD.get());
                case FURY -> new ItemStack(KRegistry.GRIMNIRS_FURY.get());
            };
        }
    }

    /** Who is warded, and who rages, until which game tick. Not saved: half a minute each. */
    private static final Map<LivingEntity, Long> WARDED = new WeakHashMap<>(), FURIOUS = new WeakHashMap<>();

    private AncestorPowers() {
    }

    public static boolean warded(LivingEntity e) {
        Long until = WARDED.get(e);
        return until != null && e.level().getGameTime() < until;
    }

    public static boolean furious(LivingEntity e) {
        Long until = FURIOUS.get(e);
        return until != null && e.level().getGameTime() < until;
    }

    /** {@code e} rages until game tick {@code until} (the Fury, and the tests). */
    public static void rage(LivingEntity e, long until) {
        FURIOUS.put(e, until);
        if (e instanceof Dawi d) {
            d.feelFury();
        }
    }

    /** The world closed: nobody is warded or raging any more (the review of dd26ec4: the old world's entities otherwise linger till collected). */
    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
        WARDED.clear();
        FURIOUS.clear();
    }

    /** The Ward and the Fury lifted from {@code e} at once (the tests; a power's own end comes on the tick). */
    public static void lift(LivingEntity e) {
        WARDED.remove(e);
        FURIOUS.remove(e);
        if (e instanceof Dawi d) {
            d.feelFury();
        }
    }

    // ------------------------------------------------------------------ the price, and the blessing

    /** What {@code p} costs {@code god} now: a quarter less from the Throne of Power. */
    public static int cost(Power p, Player god) {
        return god.getVehicle() instanceof claude.khazuk.throne.ThroneOfPower ? Math.round(p.cost * THRONE_PRICE) : p.cost;
    }

    /** The rune-priests' blessing on a power called by {@code god} (near him, or near {@code at}): 1, a Runesmith's, or a Runelord's. */
    public static float blessing(ServerLevel level, UUID god, Vec3 near, @Nullable Vec3 at) {
        float best = 1f;
        for (Vec3 p : at == null ? new Vec3[]{near} : new Vec3[]{near, at}) {
            for (Runesmith s : level.getEntitiesOfClass(Runesmith.class, new AABB(p, p).inflate(SMITH_NEAR), Runesmith::isAlive)) {
                if (s.settlement() != null && god.equals(s.settlement().owner()) && s.distanceToSqr(p) <= SMITH_NEAR * SMITH_NEAR) {
                    best = Math.max(best, Devotion.lord(s) ? LORD_BLESSING : SMITH_BLESSING);
                }
            }
        }
        return best;
    }

    /**
     * Whether {@code e} is the god's own, for a blow of his that hits all round: the god himself ({@code god}, by id), the Dawi of any hold
     * of his, his Throne and his Anvil, his pets, and whatever is allied to him.
     */
    public static boolean godsOwn(LivingEntity e, UUID god, @Nullable ServerPlayer godPlayer) {
        if (god.equals(e.getUUID())) {
            return true;
        }
        if (e instanceof Dawi d && god.equals(d.godId())) {
            return true;
        }
        if (e instanceof claude.khazuk.throne.ThroneOfPower t && god.equals(t.owner())
                || e instanceof claude.khazuk.hold.AnvilOfDoom a && god.equals(a.owner())) {
            return true;
        }
        if (e instanceof OwnableEntity o && o.getOwnerReference() != null && god.equals(o.getOwnerReference().getUUID())) {
            return true;
        }
        return godPlayer != null && e.isAlliedTo(godPlayer);
    }

    // ------------------------------------------------------------------ the three powers (the tokens call these; so do the tests)

    private static String tooLittle(ShrineBlockEntity hold, Power p, int cost) {
        return hold.settlementName() + " has too little devotion for " + p.title + " (" + hold.devotion().devotion() + " of " + cost
                + "). The Dawi pray at the Shrine: more of them up, ale in the barrels, Runesmiths, grudges settled.";
    }

    /** Grungni's Hammer on {@code at} for {@code hold}: refused (why) if the hold can't pay. */
    public static @Nullable String hammer(ShrineBlockEntity hold, ServerPlayer god, ServerLevel level, Vec3 at) {
        int cost = cost(Power.HAMMER, god);
        if (!level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(at))) {
            return "Grungni's Hammer can't fall where the world isn't awake: closer in.";   // it would take the devotion and never strike (the bug hunt)
        }
        if (!hold.devotion().spend(cost)) {
            return tooLittle(hold, Power.HAMMER, cost);
        }
        hold.answered(Power.HAMMER);                             // its Ancestor answers on the hold's statue
        float bless = blessing(level, god.getUUID(), god.position(), at);
        level.addFreshEntity(GrungniHammer.of(level, god, at, god.getYRot(), bless));
        god.sendSystemMessage(Component.literal("Grungni's Hammer falls" + blessed(bless) + ".").withStyle(ChatFormatting.GOLD), true);
        return null;
    }

    /** Valaya's Ward round {@code god} for {@code hold}: refused (why) if the hold can't pay. How many it warded, through {@link #lastCount}. */
    public static @Nullable String ward(ShrineBlockEntity hold, ServerPlayer god, ServerLevel level) {
        int cost = cost(Power.WARD, god);
        if (!hold.devotion().spend(cost)) {
            return tooLittle(hold, Power.WARD, cost);
        }
        hold.answered(Power.WARD);                               // its Ancestor answers on the hold's statue
        float bless = blessing(level, god.getUUID(), god.position(), null);
        double r = WARD_RADIUS * bless;
        long until = level.getGameTime() + Math.round(WARD_TICKS * bless);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, god.getBoundingBox().inflate(r, 6, r),
                e -> e.isAlive() && godsOwn(e, god.getUUID(), god) && e.distanceToSqr(god) <= r * r)) {
            WARDED.put(e, until);
            e.heal(WARD_HEAL);
            e.clearFire();
            n++;
        }
        lastCount = n;
        dome(level, god.position(), r);
        level.playSound(null, god.getX(), god.getY(), god.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.3f);
        god.sendSystemMessage(Component.literal("Valaya's Ward is over " + n + (n == 1 ? "" : " of yours") + blessed(bless) + ".")
                .withStyle(ChatFormatting.GOLD), true);
        return null;
    }

    /** Grimnir's Fury round {@code god} for {@code hold}: refused (why) if the hold can't pay. How many it filled, through {@link #lastCount}. */
    public static @Nullable String fury(ShrineBlockEntity hold, ServerPlayer god, ServerLevel level) {
        int cost = cost(Power.FURY, god);
        if (!hold.devotion().spend(cost)) {
            return tooLittle(hold, Power.FURY, cost);
        }
        hold.answered(Power.FURY);                               // its Ancestor answers on the hold's statue
        float bless = blessing(level, god.getUUID(), god.position(), null);
        double r = FURY_RADIUS * bless;
        int ticks = Math.round(FURY_TICKS * bless);
        long until = level.getGameTime() + ticks;
        int n = 0;
        for (Dawi d : level.getEntitiesOfClass(Dawi.class, god.getBoundingBox().inflate(r, 6, r),
                d -> d.isAlive() && godsOwn(d, god.getUUID(), god) && d.distanceToSqr(god) <= r * r)) {
            rage(d, d instanceof claude.khazuk.Slayer ? until + ticks : until);   // 0.14.2: Grimnir's own burns twice as long in a Slayer
            n++;
        }
        FURIOUS.put(god, until);
        god.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 1));
        god.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 0));
        lastCount = n;
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI / 20;
            level.sendParticles(ParticleTypes.FLAME, god.getX() + Math.cos(a) * 2, god.getY() + 0.3, god.getZ() + Math.sin(a) * 2, 1,
                    Math.cos(a) * 0.15, 0.2, Math.sin(a) * 0.15, 0.02);
        }
        level.playSound(null, god.getX(), god.getY(), god.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2f, 0.8f);
        god.sendSystemMessage(Component.literal("Grimnir's Fury fills " + n + " Dawi" + blessed(bless) + ". Khazuk!")
                .withStyle(ChatFormatting.GOLD), true);
        return null;
    }

    /** How many the last Ward or Fury reached, for the tests. */
    public static int lastCount;

    private static String blessed(float bless) {
        return bless >= LORD_BLESSING ? ", a Runelord's blessing on it" : bless > 1 ? ", a Runesmith's blessing on it" : "";
    }

    /** The Ward's dome: a gold shell of light round where it was called. */
    private static void dome(ServerLevel level, Vec3 c, double r) {
        for (int i = 0; i < 18; i++) {
            double lat = i * Math.PI / 36;
            int ring = Math.max(6, (int) (Math.cos(lat) * 36));
            for (int j = 0; j < ring; j++) {
                double lon = j * Math.PI * 2 / ring;
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(lat) * Math.cos(lon) * r, c.y + Math.sin(lat) * r,
                        c.z + Math.cos(lat) * Math.sin(lon) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ the ward, the fury and the tokens' meter, as the game runs

    /** The Ward: fire and shots turned aside whole, every other blow halved. /kill and the void get past it. */
    @SubscribeEvent
    public static void incoming(LivingIncomingDamageEvent e) {
        LivingEntity hurt = e.getEntity();
        if (hurt.level().isClientSide() || !warded(hurt)) {
            return;
        }
        DamageSource s = e.getSource();
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        if (s.is(DamageTypeTags.IS_FIRE) || s.is(DamageTypeTags.IS_PROJECTILE)) {
            e.setCanceled(true);
            hurt.clearFire();
            return;
        }
        e.setAmount(e.getAmount() * WARD_TAKEN);
    }

    /** Twice a second: the warded glint gold, the furious smoulder; the spent ones let go. */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) {
            return;
        }
        glow(WARDED, new DustParticleOptions(0xF2C744, 1.2f));   // gold; an END_ROD glint among it reads at night
        glow(FURIOUS, null);
    }

    private static void glow(Map<LivingEntity, Long> on, @Nullable DustParticleOptions dust) {
        for (Iterator<Map.Entry<LivingEntity, Long>> it = on.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Long> en = it.next();
            LivingEntity le = en.getKey();
            if (!le.isAlive() || le.level().getGameTime() >= en.getValue()) {
                it.remove();
                if (le instanceof Dawi d && le.isAlive()) {
                    d.feelFury();                                // the rage spent: back to himself
                }
                continue;
            }
            if (le.level() instanceof ServerLevel sl) {
                double y = le.getY() + le.getBbHeight() * 0.6;
                if (dust != null) {
                    sl.sendParticles(dust, le.getX(), y, le.getZ(), 3, le.getBbWidth() * 0.6, le.getBbHeight() * 0.4, le.getBbWidth() * 0.6, 0);
                    sl.sendParticles(ParticleTypes.END_ROD, le.getX(), y, le.getZ(), 1, le.getBbWidth() * 0.6, le.getBbHeight() * 0.4, le.getBbWidth() * 0.6, 0);
                } else {
                    sl.sendParticles(ParticleTypes.SMALL_FLAME, le.getX(), y, le.getZ(), 2, le.getBbWidth() * 0.4, le.getBbHeight() * 0.3, le.getBbWidth() * 0.4, 0.01);
                }
            }
        }
    }

    /** Holding a token, the god sees his hold's devotion over his hotbar each second, and what each power costs him there. */
    @SubscribeEvent
    public static void meter(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer god) || god.tickCount % 20 != 7 || !(holding(god.getMainHandItem()) || holding(god.getOffhandItem()))) {
            return;
        }
        ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(god, god.blockPosition());
        if (hold == null) {
            return;
        }
        StringBuilder b = new StringBuilder(hold.settlementName()).append(": devotion ").append(hold.devotion().devotion()).append("/")
                .append(Devotion.MAX);
        for (Power p : Power.values()) {
            b.append(", ").append(p.god).append(" ").append(cost(p, god));
        }
        float bless = blessing((ServerLevel) god.level(), god.getUUID(), god.position(), null);
        if (bless > 1) {
            b.append(bless >= LORD_BLESSING ? " (a Runelord near)" : " (a Runesmith near)");
        }
        god.sendOverlayMessage(Component.literal(b.toString()).withStyle(ChatFormatting.GOLD));
    }

    private static boolean holding(ItemStack s) {
        return s.getItem() instanceof Token;
    }

    // ------------------------------------------------------------------ the tokens

    /** {@code god}'s hold for {@code stack}: the one he's in (or his nearest loaded), remembered on it; else the one it remembers. */
    public static @Nullable ShrineBlockEntity holdFor(ServerPlayer god, ItemStack stack) {
        ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(god, god.blockPosition());
        if (hold != null) {
            CompoundTag t = new CompoundTag();
            t.putString("dim", hold.getLevel().dimension().identifier().toString());
            t.putInt("x", hold.getBlockPos().getX());
            t.putInt("y", hold.getBlockPos().getY());
            t.putInt("z", hold.getBlockPos().getZ());
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.merge(t));
            return hold;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag t = data.copyTag();
        Identifier dim = Identifier.tryParse(t.getStringOr("dim", ""));
        ServerLevel level = dim == null ? null : god.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null || !t.contains("dim")) {
            return null;
        }
        BlockPos at = new BlockPos(t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0));
        if (!level.getWorldBorder().isWithinBounds(at)) {
            return null;
        }
        level.getChunk(at);                                      // far off: its Shrine's chunk loaded a moment, to ask it (a refusal cools the token)
        return level.getBlockEntity(at) instanceof ShrineBlockEntity h && god.getUUID().equals(h.owner()) ? h : null;
    }

    /** The ground {@code god} is looking at, within reach: where the Hammer lands (on top of what he points at), or null. */
    static @Nullable Vec3 target(ServerLevel level, ServerPlayer god) {
        Vec3 eye = god.getEyePosition(), end = eye.add(god.getViewVector(1f).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, god));
        return hit.getType() != HitResult.Type.BLOCK ? null : onTop(level, hit.getBlockPos(), hit.getLocation());
    }

    /** On top of block {@code p} (clicked at {@code where}): a wall's face goes up onto its top. */
    static Vec3 onTop(ServerLevel level, BlockPos p, Vec3 where) {
        for (int i = 0; i < 8 && !level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty(); i++) {
            p = p.above();
        }
        return new Vec3(where.x, p.getY() + 1, where.z);
    }

    /** A power's token: used, it calls its power on its hold's devotion. */
    public static class Token extends Item {
        final Power power;

        public Token(Properties p, Power power) {
            super(p);
            this.power = power;
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer god) || !(level instanceof ServerLevel sl)) {
                return InteractionResult.SUCCESS;
            }
            return call(sl, god, hand, power == Power.HAMMER ? target(sl, god) : null);
        }

        /** Used on a block close by (a click on a block never reaches use): the Hammer on top of it; the others as from the air. */
        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
            if (!(ctx.getPlayer() instanceof ServerPlayer god) || !(ctx.getLevel() instanceof ServerLevel sl)) {
                return InteractionResult.SUCCESS;
            }
            return call(sl, god, ctx.getHand(), power == Power.HAMMER ? onTop(sl, ctx.getClickedPos(), ctx.getClickLocation()) : null);
        }

        private InteractionResult call(ServerLevel sl, ServerPlayer god, InteractionHand hand, @Nullable Vec3 at) {
            ItemStack stack = god.getItemInHand(hand);
            ShrineBlockEntity hold = holdFor(god, stack);
            String no;
            if (hold == null) {
                no = "The Ancestors answer a hold's prayers. Set down an Ancestor Shrine first.";
            } else if (power == Power.HAMMER && at == null) {
                no = "Point at the ground: Grungni's Hammer needs somewhere to fall.";
            } else {
                no = switch (power) {
                    case HAMMER -> hammer(hold, god, sl, at);
                    case WARD -> ward(hold, god, sl);
                    case FURY -> fury(hold, god, sl);
                };
            }
            if (no != null) {
                god.sendSystemMessage(Component.literal(no).withStyle(ChatFormatting.YELLOW));
                god.getCooldowns().addCooldown(stack, REFUSED_COOLDOWN);
                return InteractionResult.FAIL;
            }
            god.getCooldowns().addCooldown(stack, CALLED_COOLDOWN);
            return InteractionResult.SUCCESS;
        }
    }
}
