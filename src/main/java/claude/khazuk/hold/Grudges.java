package claude.khazuk.hold;

import claude.khazuk.ShrineBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * D4, the Book of Grudges, the Dammaz Kron (docs/plan.md section 4): the hold writes down every wrong done to it, and settling one is
 * rewarded. Kept on the Shrine and saved with it; read in the Dammaz Kron (BookOfGrudges, the item).
 * <ul>
 * <li><b>Written</b> (hold/GrudgeEvents): against whatever kills one of the hold's Dwarfs; whatever blows up or breaks one of its buildings
 * (a creeper's blast, a stranger's pick); whoever takes gold from it. A grudge names a kind of foe ({@value #PER_WRONG} of them must fall for
 * each wrong, and more wrongs by the same kind add to it), or one foe by name: a named mob, a player (that one must fall).</li>
 * <li><b>Settled</b> when the hold's Dwarfs or its god kill enough of it. The hold is paid {@value #GOLD_BASE} gold and {@value #GOLD_PER}
 * more for every foe the grudge needed, its barrels get {@value #ALE_REWARD} ale, and from then on its Dwarfs bear that grudge: they hit
 * that kind of foe {@value #BEARER_PERCENT}% harder ("grudgebearers"; the Book does not forget).</li>
 * <li>An open grudge costs the hold nothing (buffs, not nerfs). The Book keeps the last {@value #KEPT} grudges; what its Dwarfs bear is kept
 * for good.</li>
 * </ul>
 */
public final class Grudges {

    public static final int PER_WRONG = 5, GOLD_BASE = 10, GOLD_PER = 2, ALE_REWARD = 16, KEPT = 64;
    public static final int BEARER_PERCENT = 25;
    /** A wrong by one foe again within this many ticks (a stranger's every pick-stroke) is written quietly: the god is told once. */
    public static final int QUIET_TICKS = 200;

    /** One grudge: whom it names (a kind of foe, one foe by name, a player), why, and how many of them have fallen to the hold. */
    public static final class Grudge {
        final String key;                                        // "kind:<type id>", "one:<uuid>" or "player:<uuid>"
        final String type;                                       // the foe's entity type id: what the hold bears once it's settled
        final String name;
        String why;
        int need, have;
        final long written;
        boolean settled;
        long told = Long.MIN_VALUE / 2;                          // when its god was last told of it (not saved: after a restart he is told again)

        Grudge(String key, String type, String name, String why, int need, long written) {
            this.key = key;
            this.type = type;
            this.name = name;
            this.why = why;
            this.need = need;
            this.written = written;
        }

        public String name() {
            return name;
        }

        public String why() {
            return why;
        }

        public int need() {
            return need;
        }

        public int have() {
            return have;
        }

        public boolean settled() {
            return settled;
        }

        public boolean kind() {
            return key.startsWith("kind:");
        }
    }

    private final List<Grudge> book = new ArrayList<>();
    /** How many times its god has been told of a grudge written or grown, for the tests. */
    public int toldForTests;
    private final Set<String> bears = new LinkedHashSet<>();
    private boolean dirty;

    public List<Grudge> grudges() {
        return List.copyOf(book);
    }

    public List<Grudge> open() {
        return book.stream().filter(g -> !g.settled).toList();
    }

    /** Whether the hold's Dwarfs bear a grudge against {@code target}'s kind: they hit it harder. */
    public boolean bears(Entity target) {
        return !bears.isEmpty() && bears.contains(typeId(target));
    }

    public Set<String> borne() {
        return Set.copyOf(bears);
    }

    public boolean takeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    static String typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    /** What a grudge calls {@code foe}: its own name for a player or a named mob, its kind's for the rest ("Zombies"). */
    static String nameOf(Entity foe, boolean one) {
        if (one) {
            return foe.getName().getString();
        }
        return plural(foe.getType().getDescription().getString());
    }

    /** "Zombies", "Witches", "Wolves", "Silverfish". */
    public static String plural(String kind) {
        String k = kind.toLowerCase(java.util.Locale.ROOT);
        if (k.endsWith("s") || k.endsWith("fish") || k.endsWith("sheep")) {
            return kind;
        }
        if (k.endsWith("ch") || k.endsWith("sh") || k.endsWith("x")) {
            return kind + "es";
        }
        if (k.endsWith("lf")) {
            return kind.substring(0, kind.length() - 1) + "ves";
        }
        return kind + "s";
    }

    /** The key a grudge against {@code foe} has: a player and a named mob are one foe, the rest a kind. */
    static String keyOf(Entity foe) {
        if (foe instanceof Player) {
            return "player:" + foe.getUUID();
        }
        if (foe.hasCustomName()) {
            return "one:" + foe.getUUID();
        }
        return "kind:" + typeId(foe);
    }

    /**
     * {@code foe} wronged the hold ({@code why}: "slew Thorin, a Miner"): written in the Book, or added to the open grudge against it. Its
     * god is told, and if he rides the Throne of Power the Book on it opens. Returns the grudge.
     */
    public Grudge wronged(ServerLevel level, ShrineBlockEntity hold, Entity foe, String why) {
        String key = keyOf(foe);
        boolean one = !key.startsWith("kind:");
        long now = level.getGameTime();
        for (Grudge g : book) {
            if (!g.settled && g.key.equals(key)) {
                if (!one) {
                    g.need += PER_WRONG;
                }
                boolean quiet = one && now - g.told < QUIET_TICKS;
                g.why = why;
                dirty = true;
                if (quiet) {
                    return g;                                    // the same foe at it again: written, its god already told
                }
                g.told = now;
                toldForTests++;
                hold.tellOwner(hold.settlementName() + ": the grudge against " + g.name + " grows: " + why + ". "
                        + (one ? "Their death settles it." : (g.need - g.have) + " more of them must fall."), ChatFormatting.DARK_RED);
                book(level, hold, "grudge_written");
                return g;
            }
        }
        Grudge g = new Grudge(key, typeId(foe), nameOf(foe, one), why, one ? 1 : PER_WRONG, now);
        g.told = now;
        toldForTests++;
        book.add(g);
        trim();
        dirty = true;
        hold.tellOwner(hold.settlementName() + ": a grudge is written in the Dammaz Kron against " + g.name + ": " + why + ". "
                + (one ? "Their death settles it." : g.need + " of them must fall."), ChatFormatting.DARK_RED);
        book(level, hold, "grudge_written");
        return g;
    }

    /** {@code dead} fell to the hold's Dwarfs or its god: counted against every open grudge that names it; the ones it fills, settled. */
    public void felled(ServerLevel level, ShrineBlockEntity hold, LivingEntity dead) {
        if (book.isEmpty()) {
            return;
        }
        String kind = "kind:" + typeId(dead), one = (dead instanceof Player ? "player:" : "one:") + dead.getUUID();
        for (Grudge g : book) {
            if (!g.settled && (g.key.equals(kind) || g.key.equals(one))) {
                g.have++;
                dirty = true;
                if (g.have >= g.need) {
                    settle(level, hold, g);
                }
            }
        }
    }

    private void settle(ServerLevel level, ShrineBlockEntity hold, Grudge g) {
        g.settled = true;
        boolean newly = bears.add(g.type);
        int gold = GOLD_BASE + GOLD_PER * g.need;
        hold.addCurrency(gold, "A grudge settled");
        hold.brew().addAle(ALE_REWARD);
        hold.tellOwner(hold.settlementName() + ": the grudge against " + g.name + " is settled and struck out. The Ancestors are pleased: "
                + gold + " gold to the hoard and " + ALE_REWARD + " ale to the barrels"
                + (newly ? ", and the Dawi bear it: they strike " + nameOf(g) + " " + BEARER_PERCENT + "% harder from now on." : "."),
                ChatFormatting.GOLD);
        book(level, hold, "grudge_settled");
    }

    private static String nameOf(Grudge g) {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(g.type));
        return t == null ? g.name : plural(t.getDescription().getString());
    }

    /** The Book on the Throne of Power opens for its god, if he rides it. */
    private static void book(ServerLevel level, ShrineBlockEntity hold, String clip) {
        if (hold.owner() != null && level.getServer().getPlayerList().getPlayer(hold.owner()) instanceof ServerPlayer p
                && p.getVehicle() instanceof claude.khazuk.throne.ThroneOfPower t) {
            t.triggerAnim("book", clip);
        }
    }

    /** The oldest settled grudges go first, then the oldest open ones, past {@value #KEPT} (what the hold bears stays). */
    private void trim() {
        while (book.size() > KEPT) {
            int i = 0;
            while (i < book.size() && !book.get(i).settled) {
                i++;
            }
            book.remove(i < book.size() ? i : 0);
        }
    }

    // ------------------------------------------------------------------ the Book's pages

    /** The Dammaz Kron's pages: its title page, then every grudge, open first, three to a page; settled ones struck out. */
    public List<Component> pages(String holdName) {
        List<Component> pages = new ArrayList<>();
        int open = open().size(), done = book.size() - open;
        MutableComponent title = Component.literal("Dammaz Kron\n").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("the Book of Grudges of " + holdName + "\n\n").withStyle(ChatFormatting.RESET, ChatFormatting.ITALIC))
                .append(Component.literal(open + " open, " + done + " settled.\n\n").withStyle(ChatFormatting.RESET))
                .append(Component.literal(bears.isEmpty() ? "The Dawi bear no grudge yet." : "The Dawi bear grudges against "
                        + String.join(", ", bears.stream().map(Grudges::kindName).toList()) + ".").withStyle(ChatFormatting.RESET, ChatFormatting.DARK_GRAY));
        pages.add(title);
        List<Grudge> order = new ArrayList<>(open());
        for (int i = book.size() - 1; i >= 0; i--) {
            if (book.get(i).settled) {
                order.add(book.get(i));
            }
        }
        MutableComponent page = null;
        int on = 0;
        for (Grudge g : order) {
            if (page == null) {
                page = Component.empty();
            }
            MutableComponent name = Component.literal(g.name + "\n").withStyle(g.settled
                    ? new ChatFormatting[]{ChatFormatting.STRIKETHROUGH, ChatFormatting.DARK_GRAY} : new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD});
            page.append(name);
            page.append(Component.literal(g.why + "\n").withStyle(ChatFormatting.RESET, g.settled ? ChatFormatting.DARK_GRAY : ChatFormatting.BLACK));
            page.append(Component.literal((g.settled ? "Settled." : g.have + " of " + g.need + " fallen.") + "\n\n")
                    .withStyle(ChatFormatting.RESET, g.settled ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            if (++on == 3) {
                pages.add(page);
                page = null;
                on = 0;
            }
        }
        if (page != null) {
            pages.add(page);
        }
        return pages;
    }

    private static String kindName(String type) {
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(type));
        return t == null ? type : t.getDescription().getString();
    }

    // ------------------------------------------------------------------ saves

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Grudge g : book) {
            CompoundTag c = new CompoundTag();
            c.putString("key", g.key);
            c.putString("type", g.type);
            c.putString("name", g.name);
            c.putString("why", g.why);
            c.putInt("need", g.need);
            c.putInt("have", g.have);
            c.putLong("written", g.written);
            c.putBoolean("settled", g.settled);
            list.add(c);
        }
        tag.put("grudges", list);
        ListTag b = new ListTag();
        for (String t : bears) {
            b.add(StringTag.valueOf(t));
        }
        tag.put("grudge_bears", b);
    }

    public void load(CompoundTag tag) {
        book.clear();
        bears.clear();
        for (Tag t : tag.getListOrEmpty("grudges")) {
            if (t instanceof CompoundTag c && !c.getStringOr("key", "").isEmpty()) {
                Grudge g = new Grudge(c.getStringOr("key", ""), c.getStringOr("type", "minecraft:pig"), c.getStringOr("name", "?"),
                        c.getStringOr("why", ""), Math.max(1, c.getIntOr("need", 1)), c.getLongOr("written", 0));
                g.have = Math.max(0, c.getIntOr("have", 0));
                g.settled = c.getBooleanOr("settled", false);
                book.add(g);
            }
        }
        for (Tag t : tag.getListOrEmpty("grudge_bears")) {
            t.asString().ifPresent(bears::add);
        }
    }

    /** Tests: a grudge's count, by the name it was written under. */
    public @Nullable Grudge named(String name) {
        for (int i = book.size() - 1; i >= 0; i--) {
            if (book.get(i).name.equals(name)) {
                return book.get(i);
            }
        }
        return null;
    }
}
