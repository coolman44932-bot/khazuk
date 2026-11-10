package claude.khazuk.hold;

import claude.settlementcore.room.PlanPlacement;
import claude.settlementcore.settlement.Building;
import claude.settlementcore.settlement.Settlement;
import claude.settlementcore.work.HoardWork;

/**
 * The Storeroom's work: the engine's Hoard (its bays, its sorting on the way in, the haul and the larder that go by HoardWork), with no
 * keeper. The Skaven's Clan Hoard takes one of the horde to carry stacks left in the wrong bay to the right one; a hold's Dawi are far
 * fewer, and a Storeroom dug by a young hold would take a Miner off its digs for good (the review of 14bf2c9). What goes in is sorted
 * as it goes in (HoardWork#put), so nothing needs a keeper but what its god himself sets in the wrong barrel.
 */
public final class StoreWork extends HoardWork {

    public StoreWork(Settlement heart, Building building, PlanPlacement placement) {
        super(heart, building, placement);
    }

    @Override
    public int crewWanted() {
        return 0;
    }
}
