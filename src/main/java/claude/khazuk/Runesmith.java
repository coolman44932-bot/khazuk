package claude.khazuk;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Runesmith (D5): the hold's hero at its Runic Forge, who strikes its runes (hold/Runes). He comes up out of the stone at the rune anvil
 * once the forge is dug, and stands his forge; the god works him at the anvil, not by orders. A hero of the army book (a Thane's match in
 * a fight, his hammer a rune-hammer), so he is the hold's toughest Dwarf: the stronger of the army book's and TWW3's per stat (the
 * user's rule). On a fast model until the detail session gives him a showpiece's (bbsrc/khazuk_build.js, 'runesmith').
 * <p>The floor (D5's tests): one Runesmith beats four zombies.
 */
public class Runesmith extends Dawi {

    public static final double MAX_HEALTH = 60, DAMAGE = 13, ARMOR = 16, TOUGHNESS = 4, KNOCKBACK_RESISTANCE = 0.9;

    public Runesmith(EntityType<? extends Runesmith> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.ARMOR, ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    /** He stands his forge: a guard at the anvil, where he was set down. */
    public void standAt(BlockPos anvil) {
        setMode(Mode.GUARD);
    }

    /** The god works him at the anvil (hold/RuneEvents), not by orders: a right-click says so. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide()) {
            player.sendSystemMessage(Component.literal("The Runesmith tends the rune anvil: use the anvil to have him strike a rune (sneak to choose "
                    + "which of the hold's), or bring a weapon or armour to it for your own.").withStyle(ChatFormatting.AQUA));
        }
        return InteractionResult.SUCCESS;
    }
}
