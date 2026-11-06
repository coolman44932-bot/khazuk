package claude.khazuk;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Unit variants, TWW3's way (0.12): no two Dwarfs of a regiment alike. Each rank-and-file kind has {@value #PALETTES} palettes (beard,
 * skin and clan colours: the kind's own texture, then {@code <kind>_v1..5}) and groups of swappable parts in their own bones (the fast
 * models' v_* bones, bbsrc/khazuk_build.js VGROUPS, which this must match): a helm (horns, wings, a tall crest), a beard (braided, forked,
 * plain), a weapon (axe or warhammer, pick or mattock, great axe or great hammer). A Dwarf's look is rolled from his UUID, so it is the
 * same on every client and after every save, with nothing sent or stored. The heroes (Runesmith, Runelord) are showpieces and have none.
 */
public final class Looks {

    public static final int PALETTES = 6;

    /** A kind's variants: its model id and its groups of parts (an empty name: none of that group shown). */
    public record Kind(String id, List<List<String>> groups) {
    }

    public static final Kind MINER = new Kind("miner", List.of(List.of("v_pick", "v_mattock"), List.of("v_braids", "v_fork", "")));
    public static final Kind WARRIOR = new Kind("dwarf_warrior", List.of(List.of("v_axe", "v_hammer"), List.of("v_horns", "v_wings", "v_crest"),
            List.of("v_braids", "v_fork", "")));
    public static final Kind QUARRELLER = new Kind("quarreller", List.of(List.of("v_braids", "v_fork", "")));
    public static final Kind THUNDERER = new Kind("thunderer", List.of(List.of("v_braids", "v_fork", "")));
    public static final Kind LONGBEARD = new Kind("longbeard", List.of(List.of("v_axe", "v_hammer"), List.of("v_horns", "v_wings"), List.of("v_braids", "")));
    public static final Kind LONGBEARD_GREAT = new Kind("longbeard_great", List.of(List.of("v_gaxe", "v_ghammer"), List.of("v_horns", "v_wings"),
            List.of("v_braids", "")));
    /** 0.14: an Ironbreaker's axe or hammer, a crest or a masked helm's wings; a Hammerer's crest or wings, his beard braided or forked. */
    public static final Kind IRONBREAKER = new Kind("ironbreaker", List.of(List.of("v_axe", "v_hammer"), List.of("v_crest", "v_wings"), List.of("v_braids", "")));
    public static final Kind HAMMERER = new Kind("hammerer", List.of(List.of("v_crest", "v_wings"), List.of("v_braids", "v_fork")));
    public static final List<Kind> ALL = List.of(MINER, WARRIOR, QUARRELLER, THUNDERER, LONGBEARD, LONGBEARD_GREAT, IRONBREAKER, HAMMERER);

    /** A Dwarf's look: his palette, and which part of each group he shows. Packed in an int ({@link #pack}) for the render state. */
    public record Look(int palette, int[] picks) {
    }

    private Looks() {
    }

    /** {@code id}'s look as a {@code kind}: the same on every side, every time (a hash of the UUID, its bits split between the choices). */
    public static Look roll(Kind kind, UUID id) {
        long h = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17));
        int palette = (int) Long.remainderUnsigned(h, PALETTES);
        h = mix(h);
        int[] picks = new int[kind.groups().size()];
        for (int i = 0; i < picks.length; i++) {
            picks[i] = (int) Long.remainderUnsigned(h >>> (i * 10), kind.groups().get(i).size());
        }
        return new Look(palette, picks);
    }

    /** SplitMix64's finaliser: neighbouring UUIDs scatter over every choice. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** The look in one int: the palette in the low 4 bits, each pick in 4 more. */
    public static int pack(Look look) {
        int v = look.palette() & 15;
        for (int i = 0; i < look.picks().length; i++) {
            v |= (look.picks()[i] & 15) << (4 + 4 * i);
        }
        return v;
    }

    public static int palette(int packed) {
        return packed & 15;
    }

    /** The texture of palette {@code p} of {@code kind}: its own for 0, else {@code <kind>_v<p>}. */
    public static Identifier texture(Kind kind, int p) {
        return Identifier.fromNamespaceAndPath(Khazuk.MODID, "textures/entity/" + kind.id() + (p == 0 ? "" : "_v" + p) + ".png");
    }

    /** The bones a packed look hides: every part of each group but the one it shows. */
    public static List<String> hidden(Kind kind, int packed) {
        List<String> out = new ArrayList<>();
        for (int g = 0; g < kind.groups().size(); g++) {
            int pick = packed >>> (4 + 4 * g) & 15;
            List<String> parts = kind.groups().get(g);
            for (int i = 0; i < parts.size(); i++) {
                if (i != pick && !parts.get(i).isEmpty()) {
                    out.add(parts.get(i));
                }
            }
        }
        return out;
    }
}
