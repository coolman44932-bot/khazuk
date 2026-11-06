package claude.khazuk.hold;

import claude.khazuk.KRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** The Anvil of Doom, as the hold's god carries it (D5b). Used on the ground it is set down there, facing the way he faces, and is his. */
public class AnvilOfDoomItem extends Item {

    public AnvilOfDoomItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        AnvilOfDoom a = KRegistry.ANVIL_OF_DOOM.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (a == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        a.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        a.setYBodyRot(yaw);
        a.setYHeadRot(yaw);
        if (!level.noCollision(a)) {
            return InteractionResult.FAIL;                       // no room for it here
        }
        if (ctx.getPlayer() != null) {
            a.setOwner(ctx.getPlayer().getUUID());
        }
        a.unfold(level, ctx.getItemInHand());                    // its runes cool on while it's folded, not afresh (the bug hunt of 2d0b04c)
        level.addFreshEntity(a);
        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
