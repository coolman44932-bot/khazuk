package claude.khazuk.engine;

import claude.khazuk.KRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The Ancestor Engine, a god's token as the Throne of Power is. Used on the ground it is set down there, cold and kneeling, facing the
 * way you face, and is yours; sneak-used by its god (or broken) it is this item again.
 */
public class AncestorEngineItem extends Item {

    public AncestorEngineItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        AncestorEngine e = KRegistry.ANCESTOR_ENGINE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (e == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
        if (!level.noCollision(e)) {
            return InteractionResult.FAIL;                       // no room for it here: it stands 2 wide and 3 tall
        }
        if (ctx.getPlayer() != null) {
            e.setOwner(ctx.getPlayer().getUUID());
        }
        level.addFreshEntity(e);
        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
