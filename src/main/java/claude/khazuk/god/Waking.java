package claude.khazuk.god;

import claude.khazuk.KRegistry;
import claude.khazuk.Khazuk;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The waking of the Dawi (the user, 2026-11-02, after the Skaven's uplifting: "with skaven we had to feed a rat warpstone to make the first
 * warpstone then you got the starting kit"). There are no Dwarfs until their god wakes them: a gold ingot struck on the deep rock
 * (deepslate, below y {@value #DEEP_Y}) cracks it over {@value #WAKE_TICKS} ticks, and the first Dawi step out of the mountain. They name
 * their hold (the NameHold screen; a name is theirs until then), hand their god the Ancestor Shrine and the kit to found it (the Ancestors'
 * Plans of the Hold Gate, the Great Hall and the mine, gold for the first digs, bread), and go back into the stone to wait: the first Shrine
 * set down brings them up, {@value #FIRST_MINERS} Miners and {@value #FIRST_WARRIORS} Warriors already in its deeps.
 *
 * <p>The Shrine takes root only for a god who has woken the Dawi (ShrineBlock); a creative god may set one anywhere. After the first, a
 * Shrine is crafted, for a second hold and a third.
 */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class Waking {

    public static final int DEEP_Y = 0;
    public static final int WAKE_TICKS = 60;
    public static final int FIRST_MINERS = 3, FIRST_WARRIORS = 1;
    public static final int KIT_GOLD = 40, KIT_BREAD = 16;

    /**
     * What a player is to the Dawi: whether they have woken them, the first hold's name, how many Shrines they have set down, and whether
     * the first Dawi have come up to one yet (once only, whichever Shrine they set down first after waking them, however often it's moved).
     */
    public record God(boolean woke, String holdName, int holds, boolean firstUp) {
        public static final God NONE = new God(false, "", 0, false);
        public static final MapCodec<God> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.fieldOf("woke").forGetter(God::woke),
                Codec.STRING.fieldOf("hold_name").forGetter(God::holdName),
                Codec.INT.fieldOf("holds").forGetter(God::holds),
                Codec.BOOL.optionalFieldOf("first_up", false).forGetter(God::firstUp)).apply(i, God::new));

        public God(boolean woke, String holdName, int holds) {
            this(woke, holdName, holds, false);
        }
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Khazuk.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<God>> GOD = ATTACHMENTS.register("dawi_god",
            () -> AttachmentType.builder(() -> God.NONE).serialize(God.CODEC).copyOnDeath().build());

    /** A waking under way: where the rock was struck, and the ticks it has to go. */
    private record Under(ServerLevel level, BlockPos at, int left) {
    }

    private static final Map<UUID, Under> WAKING = new HashMap<>();
    /** The Shrine each god is renaming (sneak-used with an empty hand), until the name comes back. */
    private static final Map<UUID, BlockPos> RENAMING = new HashMap<>();
    /** How far a god may wander from the cracking rock before it falls quiet. */
    static final double WAKE_REACH = 24;

    private Waking() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(Waking::payloads);
    }

    public static God god(Player p) {
        return p.getData(GOD);
    }

    public static void setGod(Player p, God g) {
        p.setData(GOD, g);
    }

    /** The deep rock a god's gold wakes: deepslate and tuff, below y {@value #DEEP_Y}. */
    public static boolean deepRock(BlockState s, BlockPos at) {
        return at.getY() < DEEP_Y && (s.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) || s.is(Blocks.COBBLED_DEEPSLATE));
    }

    @SubscribeEvent
    static void strike(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = e.getItemStack();
        if (!held.is(Items.GOLD_INGOT) || god(p).woke() || WAKING.containsKey(p.getUUID())) {
            return;
        }
        BlockPos at = e.getPos();
        if (!deepRock(level.getBlockState(at), at)) {
            return;
        }
        begin(level, p, at);                                     // the gold is spent when the Dawi wake: a waking cut short costs nothing
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS_SERVER);   // the server swings the hand: the client's handler passes
    }

    /** Begins the waking at {@code at}, struck by {@code p}: the rock cracks for {@value #WAKE_TICKS} ticks. */
    public static void begin(ServerLevel level, ServerPlayer p, BlockPos at) {
        WAKING.put(p.getUUID(), new Under(level, at, WAKE_TICKS));
        p.sendSystemMessage(Component.literal("The deep rock answers your gold. Something stirs in the stone.").withStyle(ChatFormatting.GOLD));
        level.playSound(null, at, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
    }

    /** Whether {@code id} has a waking under way (the tests). */
    public static boolean waking(UUID id) {
        return WAKING.containsKey(id);
    }

    @SubscribeEvent
    static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent e) {
        WAKING.clear();
        RENAMING.clear();
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post e) {
        if (WAKING.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Under>> it = WAKING.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Under u = en.getValue();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || p.level() != u.level()
                    || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(u.at())) > WAKE_REACH * WAKE_REACH) {
                it.remove();                                     // gone, dead, or wandered off: the stone sleeps on, the gold still theirs
                if (p != null && p.isAlive()) {
                    p.sendSystemMessage(Component.literal("The stone falls quiet. Strike it again, and stay by it.").withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            if (u.left() % 10 == 0) {
                u.level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState()),
                        u.at().getX() + 0.5, u.at().getY() + 1.0, u.at().getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.1);
                u.level().sendParticles(ParticleTypes.WAX_OFF, u.at().getX() + 0.5, u.at().getY() + 0.5, u.at().getZ() + 0.5, 6, 0.5, 0.5, 0.5, 0.0);
                u.level().playSound(null, u.at(), SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 1.2f, 0.5f + 0.02f * (WAKE_TICKS - u.left()));
            }
            if (u.left() <= 0) {
                it.remove();
                woken(u.level(), p, u.at());
            } else {
                en.setValue(new Under(u.level(), u.at(), u.left() - 1));
            }
        }
    }

    /** The rock opens and the first Dawi step out: they hand their god the Shrine and the kit, and ask for their hold's name. */
    public static void woken(ServerLevel level, ServerPlayer p, BlockPos at) {
        RENAMING.remove(p.getUUID());                            // the name asked next is the hold's own, not an old Shrine's
        if (!p.isCreative()) {
            spendGold(p);
        }
        boolean wet = false;
        for (BlockPos c : new BlockPos[]{at, at.above()}) {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                wet |= !level.getFluidState(c.relative(d)).isEmpty();
            }
        }
        for (BlockPos c : new BlockPos[]{at, at.above()}) {
            BlockState was = level.getBlockState(c);
            if (deepRock(was, c)) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, c, Block.getId(was));
                if (!wet) {
                    level.setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());   // the doorway in the rock they came out of (never into water or lava)
                }
            }
        }
        String name = "Karak " + p.getGameProfile().name();
        setGod(p, new God(true, name, god(p).holds(), god(p).firstUp()));
        give(p, new ItemStack(KRegistry.SHRINE_ITEM.get()));
        give(p, new ItemStack(KRegistry.PLAN_HOLD_GATE.get()));
        give(p, new ItemStack(KRegistry.PLAN_GREAT_HALL.get()));
        give(p, new ItemStack(KRegistry.PLAN_MINE.get()));
        give(p, new ItemStack(Items.GOLD_INGOT, KIT_GOLD));
        give(p, new ItemStack(Items.BREAD, KIT_BREAD));
        p.sendSystemMessage(Component.literal("The rock splits, and the first Dawi step out of the mountain, beards white with stone dust. They kneel: "
                + "they call you the Ancestors.").withStyle(ChatFormatting.GOLD));
        p.sendSystemMessage(Component.literal("They hand you their Ancestor Shrine, the plans of a hold and its gold, and go back into the stone to wait. "
                + "Set the Shrine down where the hold should be, and they come up to it.").withStyle(ChatFormatting.YELLOW));
        level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.6f);
        if (p.connection != null && p.connection.hasChannel(AskName.TYPE)) {
            PacketDistributor.sendToPlayer(p, new AskName(name));
        }
    }

    /** The ingot the god struck the rock with, spent now the Dawi have woken. */
    private static void spendGold(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.GOLD_INGOT)) {
                inv.getItem(i).shrink(1);
                return;
            }
        }
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
        }
    }

    /**
     * The name a Shrine set down by {@code p} at {@code at} takes: the hold's own name while it is their only Shrine (a first Shrine picked
     * up and set down elsewhere keeps it), else "the hold's name, hold N", N one more than the other Shrines of theirs that stand.
     */
    public static String nameFor(ServerPlayer p, BlockPos at) {
        God g = god(p);
        String base = g.holdName().isEmpty() ? p.getGameProfile().name() + "'s Hold" : g.holdName();
        int others = 0;
        for (var s : claude.settlementcore.settlement.SettlementSpawns.loaded(p.level())) {
            if (s instanceof claude.khazuk.ShrineBlockEntity h && p.getUUID().equals(h.owner()) && !h.getBlockPos().equals(at)) {
                others++;
            }
        }
        return others == 0 ? base : base + ", hold " + (others + 1);
    }

    /** {@code p} sneak-used their Shrine with an empty hand: ask them its new name. */
    public static void askRename(ServerPlayer p, claude.khazuk.ShrineBlockEntity hold) {
        RENAMING.put(p.getUUID(), hold.getBlockPos());
        if (p.connection != null && p.connection.hasChannel(AskName.TYPE)) {
            PacketDistributor.sendToPlayer(p, new AskName(hold.settlementName()));
        }
    }

    // ------------------------------------------------------------------ the hold's name: asked of the client, sent back

    /** Server to client: open the naming screen, with the name the Dawi have for it until then. */
    public record AskName(String suggested) implements CustomPacketPayload {
        public static final Type<AskName> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Khazuk.MODID, "ask_hold_name"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AskName> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AskName::suggested, AskName::new);

        @Override
        public Type<AskName> type() {
            return TYPE;
        }
    }

    /** Client to server: the hold's name. */
    public record NameHold(String name) implements CustomPacketPayload {
        public static final Type<NameHold> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Khazuk.MODID, "name_hold"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NameHold> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, NameHold::name, NameHold::new);

        @Override
        public Type<NameHold> type() {
            return TYPE;
        }
    }

    public static final int NAME_MAX = 40;

    private static void payloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(AskName.TYPE, AskName.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> ClientHooks.askName(msg.suggested())));
        r.playToServer(NameHold.TYPE, NameHold.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                named(p, msg.name());
            }
        }));
    }

    /**
     * The god names a hold (up to {@value #NAME_MAX} letters, no formatting or control characters; blank keeps the old name): the Shrine they
     * asked to rename, or after the waking the hold's own name.
     */
    public static void named(ServerPlayer p, String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}\\p{Cf}\u0080-\u009f\u00a7]", "").strip();   // no codes, controls or invisible marks
        if (name.codePointCount(0, name.length()) > NAME_MAX) {
            name = name.substring(0, name.offsetByCodePoints(0, NAME_MAX)).strip();   // whole letters only, never half of one
        }
        God g = god(p);
        BlockPos renaming = RENAMING.remove(p.getUUID());
        if (!g.woke() && renaming == null || name.isEmpty()) {
            return;
        }
        if (renaming != null) {
            if (p.level().getBlockEntity(renaming) instanceof claude.khazuk.ShrineBlockEntity h && p.getUUID().equals(h.owner())) {
                String was = h.settlementName();
                h.claim(p, name);
                if (was.equals(g.holdName())) {
                    setGod(p, new God(g.woke(), name, g.holds(), g.firstUp()));   // the first hold renamed: its name is the hold's
                }
                p.sendSystemMessage(Component.literal("The Dawi carve the new name over the door: " + name + ".").withStyle(ChatFormatting.GOLD));
            }
            return;
        }
        setGod(p, new God(true, name, g.holds(), g.firstUp()));
        p.sendSystemMessage(Component.literal("The Dawi carve the name over their door: " + name + ".").withStyle(ChatFormatting.GOLD));
    }

    /** The client's side of the naming: a separate class, so the server never loads a screen. */
    static final class ClientHooks {
        private ClientHooks() {
        }

        static void askName(String suggested) {
            claude.khazuk.client.NameHoldScreen.open(suggested);
        }
    }
}
