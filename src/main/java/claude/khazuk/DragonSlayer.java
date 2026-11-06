package claude.khazuk;

import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The hold's Dragon Slayer (0.14.2), on the detail session's showpiece (handoffs/dragon_slayer_2026-11-06_done.md): TWW3's great axe in
 * both hands, and his <b>deathblow</b>. On a large foe he sinks, swings the axe up overhead, leaps and chops it down: the clip's edge
 * lands at 0.6 s ({@value #DEATHBLOW_LANDS} ticks), and so does the blow, {@value #DEATHBLOW_BONUS} harder than his others, on whatever
 * is still within {@value #DEATHBLOW_REACH} blocks. Once in {@value #DEATHBLOW_TICKS} ticks; between, his plain blows.
 */
public class DragonSlayer extends Slayer {

    static final RawAnimation DEATHBLOW = RawAnimation.begin().thenPlay("animation.khazuk.deathblow");
    public static final int DEATHBLOW_LANDS = 12, DEATHBLOW_TICKS = 40;
    public static final double DEATHBLOW_BONUS = 0.5, DEATHBLOW_REACH = 4;
    static final Identifier DEATHBLOW_HIT = Identifier.fromNamespaceAndPath(Khazuk.MODID, "deathblow");

    private @Nullable LivingEntity deathblowAt;
    private int lands, next;
    private boolean landing;
    /** Deathblows landed, for the tests. */
    public int deathblowsForTests;

    public DragonSlayer(EntityType<? extends DragonSlayer> type, Level level) {
        super(type, level);
    }

    @Override
    protected void moreTriggers(AnimationController<Dawi> attack) {
        attack.triggerableAnim("deathblow", DEATHBLOW);
    }

    /** On a large foe, his deathblow begins (it lands {@value #DEATHBLOW_LANDS} ticks on); else his plain blow. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!landing && target instanceof LivingEntity l && large(l) && tickCount >= next && deathblowAt == null) {
            deathblowAt = l;
            lands = tickCount + DEATHBLOW_LANDS;
            next = tickCount + DEATHBLOW_TICKS;
            triggerAnim("attack", "deathblow");
            return true;
        }
        return super.doHurtTarget(level, target);
    }

    /** Whether his deathblow is in the air now (the tests). */
    public boolean deathblowComing() {
        return deathblowAt != null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (deathblowAt != null && level() instanceof ServerLevel sl && tickCount >= lands) {
            LivingEntity t = deathblowAt;
            deathblowAt = null;
            if (isAlive() && t.isAlive() && distanceTo(t) <= DEATHBLOW_REACH) {
                land(sl, t);
            }
        }
    }

    private void land(ServerLevel level, LivingEntity t) {
        var a = getAttribute(Attributes.ATTACK_DAMAGE);
        if (a != null) {
            a.addOrUpdateTransientModifier(new AttributeModifier(DEATHBLOW_HIT, DEATHBLOW_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        landing = true;
        try {
            if (super.doHurtTarget(level, t)) {
                deathblowsForTests++;
            }
        } finally {
            landing = false;
            if (a != null) {
                a.removeModifier(DEATHBLOW_HIT);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getY(0.5), t.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), getX(), getY() + 0.1, getZ(),
                30, 0.8, 0.05, 0.8, 0.15);                       // the ground he lands on
    }
}
