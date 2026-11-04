package claude.khazuk.hold;

import claude.khazuk.Dawi;
import claude.khazuk.Khazuk;
import claude.khazuk.ShrineBlockEntity;
import claude.settlementcore.room.Rooms;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.settlement.SettlementSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Where the Book of Grudges (hold/Grudges) hears of wrongs and of their settling. A Dwarf's own death is written by Dawi#die; here:
 * <ul>
 * <li>a blast that takes a block out of one of the hold's buildings: a grudge against what set it off (a creeper, a TNT's lighter, a ghast);</li>
 * <li>a player who breaks a block of one of the hold's buildings and isn't its god: against them, and if it was gold ("stole the hold's
 * gold") the Book says so;</li>
 * <li>a death at the hands of the hold's Dwarfs or its god (on his Throne or on foot): counted against the grudges that name it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Khazuk.MODID)
public final class GrudgeEvents {

    private GrudgeEvents() {
    }

    /** The hold whose building holds {@code pos}, and that building, or null. */
    record Hit(ShrineBlockEntity hold, Building building) {
    }

    static @Nullable Hit buildingAt(ServerLevel level, BlockPos pos) {
        for (Settlement s : SettlementSpawns.loaded(level)) {
            if (s instanceof ShrineBlockEntity h && h.inSettlement(pos)) {
                for (Building b : h.buildings()) {
                    if (b.box().isInside(pos)) {
                        return new Hit(h, b);
                    }
                }
            }
        }
        return null;
    }

    static String roomName(Building b) {
        Rooms.Room r = Rooms.get(b.room());
        return r == null ? "the hold" : "the " + r.displayName();
    }

    @SubscribeEvent
    static void blast(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity by = e.getExplosion().getIndirectSourceEntity();
        if (by == null || by instanceof Dawi) {
            return;
        }
        List<BlockPos> hit = e.getAffectedBlocks();
        java.util.Set<ShrineBlockEntity> told = new java.util.HashSet<>();
        for (BlockPos p : hit) {
            if (level.getBlockState(p).isAir()) {
                continue;
            }
            Hit h = buildingAt(level, p);
            if (h != null && !by.getUUID().equals(h.hold().owner()) && told.add(h.hold())) {
                h.hold().grudges().wronged(level, h.hold(), by, "blew a hole in " + roomName(h.building()));
            }
        }
    }

    @SubscribeEvent
    static void broke(BreakBlockEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getPlayer() instanceof ServerPlayer p) || p.isCreative()) {
            return;
        }
        Hit h = buildingAt(level, e.getPos());
        if (h == null || p.getUUID().equals(h.hold().owner())) {
            return;
        }
        BlockState s = e.getState();
        boolean gold = s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.RAW_GOLD_BLOCK) || s.is(BlockTags.GOLD_ORES);
        h.hold().grudges().wronged(level, h.hold(), p, gold ? "stole the hold's gold from " + roomName(h.building())
                : "broke the stone of " + roomName(h.building()));
    }

    @SubscribeEvent
    static void death(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        ShrineBlockEntity hold = holdOf(e.getSource().getEntity(), dead);
        if (hold != null && !(dead instanceof Dawi d && d.settlement() == hold)) {
            hold.grudges().felled(level, hold, dead);
        }
    }

    /** The hold a killer fights for: a Dwarf's own, or the god's (the hold whose land he stands in, else his nearest). */
    static @Nullable ShrineBlockEntity holdOf(@Nullable Entity killer, LivingEntity dead) {
        if (killer instanceof Dawi d) {
            return d.settlement();
        }
        if (killer instanceof ServerPlayer p) {
            return ShrineBlockEntity.ownedAt(p, dead.blockPosition());
        }
        return null;
    }
}
