package claude.khazuk.engine;

import claude.khazuk.Khazuk;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Client to server: which of the Ancestor Engine's keys its rider has down (the attack and the use key), and which were pressed since
 * the client last said (those two, and the vent key). It says nothing of what they do: the chop, the claw, the dig and the vent are
 * decided on the server ({@link AncestorEngine#keysFrom}), which hears only the engine's own rider. The jump and sprint keys go by
 * vanilla's own input packet.
 */
public record EngineKeys(byte bits) implements CustomPacketPayload {

    static final int ATTACK = 1, USE = 2, VENT = 4, ATTACK_PRESSED = 8, USE_PRESSED = 16;

    public static final Type<EngineKeys> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Khazuk.MODID, "engine_keys"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EngineKeys> CODEC = StreamCodec.composite(ByteBufCodecs.BYTE, EngineKeys::bits, EngineKeys::new);

    public EngineKeys(boolean attack, boolean use, boolean vent, boolean attackPressed, boolean usePressed) {
        this((byte) ((attack ? ATTACK : 0) | (use ? USE : 0) | (vent ? VENT : 0) | (attackPressed ? ATTACK_PRESSED : 0) | (usePressed ? USE_PRESSED : 0)));
    }

    /** The keys held, nothing newly pressed but what a key going down says itself. */
    public EngineKeys(boolean attack, boolean use, boolean vent) {
        this(attack, use, vent, false, false);
    }

    public boolean attack() {
        return (bits & ATTACK) != 0;
    }

    public boolean use() {
        return (bits & USE) != 0;
    }

    public boolean vent() {
        return (bits & VENT) != 0;
    }

    public boolean attackPressed() {
        return (bits & ATTACK_PRESSED) != 0;
    }

    public boolean usePressed() {
        return (bits & USE_PRESSED) != 0;
    }

    @Override
    public Type<EngineKeys> type() {
        return TYPE;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(EngineKeys::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToServer(TYPE, CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                AncestorEngine.keysFrom(p, msg);
            }
        }));
    }
}
