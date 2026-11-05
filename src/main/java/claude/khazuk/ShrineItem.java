package claude.khazuk;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * The Ancestor Shrine's item: the statue of the three Ancestors itself, small, in hand and in the inventory (GeckoLib's item render of the
 * block's own geo, client/ShrineRenderer), its idle clip playing: the three figures are mush as a 16 px icon. Set down, it's the Shrine.
 */
public class ShrineItem extends BlockItem implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ShrineItem(Block block, Properties props) {
        super(block, props);
    }

    /** The client's renderer for it (only ever called on the client). */
    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(claude.khazuk.client.ShrineRenderer.itemProvider());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ShrineItem>("main", 0, test -> test.setAndContinue(ShrineBlockEntity.IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
