package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * The Dammaz Kron, the hold's Book of Grudges as its god carries it (D4): opened, it shows the grudges of the hold whose land he stands in
 * (else his nearest), as a book's pages: what wronged the hold, how many have fallen, and what is settled, struck out. Its pages are
 * written afresh from the hold's Book each time it opens.
 */
public class BookOfGrudges extends Item {

    public BookOfGrudges(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ShrineBlockEntity hold = ShrineBlockEntity.ownedAt(sp, sp.blockPosition());
        if (hold == null) {
            sp.sendSystemMessage(Component.literal("The Dammaz Kron's pages are blank: you have no hold to keep its grudges.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        write(stack, hold);
        sp.openItemGui(stack, hand);
        return InteractionResult.SUCCESS;
    }

    /** The book's pages, from the hold's Book of Grudges now. */
    public static void write(ItemStack stack, ShrineBlockEntity hold) {
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Dammaz Kron"), hold.settlementName(), 0,
                hold.grudges().pages(hold.settlementName()).stream().map(Filterable::passThrough).toList(), true));
    }
}
