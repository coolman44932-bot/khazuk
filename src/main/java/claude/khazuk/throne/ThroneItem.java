package claude.khazuk.throne;

import claude.khazuk.KRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** The Throne of Power folded up. Used on the ground it is set down there, facing the way you face, and is yours. */
public class ThroneItem extends Item {

    public ThroneItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        ThroneOfPower t = KRegistry.THRONE.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE);
        if (t == null) {
            return InteractionResult.FAIL;
        }
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        t.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        t.setYBodyRot(yaw);
        t.setYHeadRot(yaw);
        if (!level.noCollision(t)) {
            return InteractionResult.FAIL;                       // no room for it here
        }
        if (ctx.getPlayer() != null) {
            t.setOwner(ctx.getPlayer().getUUID());
        }
        t.unfold(ctx.getItemInHand());                           // the Book's grudge, carried folded
        level.addFreshEntity(t);
        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
