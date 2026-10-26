package madoku.craft.java.mob;

import madoku.craft.java.core.status.EntityStunManager;
import madoku.craft.java.core.time.TimeAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


/** Owns the short-lived Frostbite stacks applied by Frosted Stray arrows. */
public final class FrostbiteManager {
	private static final long STACK_LINGER_TICKS = 5L * 20L;
	private static final long CONSUMPTION_COOLDOWN_TICKS = 5L * 20L;
	private static final long STUN_DURATION_TICKS = 20L;
	private static final long AFTER_STUN_WEAKNESS_DURATION_TICKS = 15L * 20L;
	private static final float CONSUMPTION_DAMAGE = 6.0F;
	private static final float FROSTBITE_SOUND_VOLUME = 0.8F;
	private static final float FROSTBITE_SOUND_PITCH = 1.0F;
	private static final Map<UUID, FrostbiteState> ACTIVE = new ConcurrentHashMap<>();

	private FrostbiteManager() {
	}

	public static void apply(LivingEntity target, Entity attacker) {
		if (target == null || target.isRemoved() || !target.isAlive() || target.level().isClientSide()) {
			return;
		}
		if (attacker != null && target.getUUID().equals(attacker.getUUID())) {
			return;
		}

		long now = currentTick();
		FrostbiteState existing = ACTIVE.get(target.getUUID());
		if (existing != null && now < existing.cooldownUntilTick) {
			return;
		}

		if (existing == null || existing.stacks <= 0 || now >= existing.expiresAtTick) {
			long expiresAt = safeAdd(now, STACK_LINGER_TICKS);
			FrostbiteState state = new FrostbiteState(target, 1, expiresAt, 0L, 0L);
			ACTIVE.put(target.getUUID(), state);
			applyFreezeVisual(state);
			playFrostbiteSound(target);
			return;
		}

		int stacks = existing.stacks + 1;
		if (stacks < 3) {
			existing.stacks = stacks;
			existing.expiresAtTick = safeAdd(existing.expiresAtTick, STACK_LINGER_TICKS);
			applyFreezeVisual(existing);
			playFrostbiteSound(target);
			return;
		}

		long cooldownUntil = safeAdd(now, CONSUMPTION_COOLDOWN_TICKS);
		long effectsAt = safeAdd(now, STUN_DURATION_TICKS);
		ACTIVE.put(target.getUUID(), new FrostbiteState(target, 0, 0L, cooldownUntil, effectsAt));
		clearFreezeVisual(target);
		playFrostbiteSound(target);
		ServerLevel level = (ServerLevel) target.level();
		DamageSource source = attacker instanceof LivingEntity livingAttacker
			? level.damageSources().mobAttack(livingAttacker)
			: level.damageSources().generic();
		target.hurtServer(level, source, CONSUMPTION_DAMAGE);
		EntityStunManager.applyStun(target, STUN_DURATION_TICKS);
	}

	/** Performs deferred post-stun work only for entities with active Frostbite state. */
	public static void onServerTick() {
		if (ACTIVE.isEmpty()) {
			return;
		}
		long now = currentTick();
		for (Map.Entry<UUID, FrostbiteState> entry : ACTIVE.entrySet()) {
			FrostbiteState state = entry.getValue();
			if (state == null || state.target == null || state.target.isRemoved() || !state.target.isAlive()) {
				if (state != null) {
					clearFreezeVisual(state.target);
				}
				ACTIVE.remove(entry.getKey(), state);
				continue;
			}
			if (state.stacks > 0) {
				applyFreezeVisual(state);
				if (now >= state.expiresAtTick) {
					clearFreezeVisual(state.target);
					ACTIVE.remove(entry.getKey(), state);
				}
				continue;
			}
			if (state.effectsAtTick > 0L && now >= state.effectsAtTick) {
				state.target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) AFTER_STUN_WEAKNESS_DURATION_TICKS, 0));
				state.effectsAtTick = 0L;
			}
			if (state.effectsAtTick == 0L && now >= state.cooldownUntilTick) {
				ACTIVE.remove(entry.getKey(), state);
			}
		}
	}

	public static void onEntityCleanup(Entity entity) {
		if (entity != null) {
			if (entity instanceof LivingEntity livingEntity) {
				clearFreezeVisual(livingEntity);
			}
			ACTIVE.remove(entity.getUUID());
		}
	}

	public static void reset() {
		for (FrostbiteState state : ACTIVE.values()) {
			if (state != null) {
				clearFreezeVisual(state.target);
			}
		}
		ACTIVE.clear();
	}

	private static void applyFreezeVisual(FrostbiteState state) {
		if (state == null || state.appliedVisualStacks == state.stacks) {
			return;
		}
		updateFreezeVisual(state.target, state.stacks);
		state.appliedVisualStacks = state.stacks;
	}

	private static void updateFreezeVisual(LivingEntity target, int stacks) {
		if (!(target instanceof Player) || target.isRemoved()) {
			return;
		}
		int ticksRequiredToFreeze = Math.max(2, target.getTicksRequiredToFreeze());
		int ticksFrozen = switch (stacks) {
			case 1 -> Math.max(1, ticksRequiredToFreeze / 3);
			case 2 -> Math.max(1, ticksRequiredToFreeze - Math.max(1, ticksRequiredToFreeze / 7));
			default -> 0;
		};
		target.setTicksFrozen(ticksFrozen);
	}

	private static void clearFreezeVisual(LivingEntity target) {
		if (target instanceof Player && !target.isRemoved()) {
			target.setTicksFrozen(0);
		}
	}

	private static void playFrostbiteSound(LivingEntity target) {
		if (target instanceof Player && !target.isRemoved()) {
			target.playSound(SoundEvents.PLAYER_HURT_FREEZE, FROSTBITE_SOUND_VOLUME, FROSTBITE_SOUND_PITCH);
		}
	}

	private static long currentTick() {
		return Math.max(0L, TimeAPIManager.getGameplayTicks());
	}

	private static long safeAdd(long base, long amount) {
		if (amount <= 0L || Long.MAX_VALUE - base < amount) {
			return Long.MAX_VALUE;
		}
		return base + amount;
	}

	private static final class FrostbiteState {
		private final LivingEntity target;
		private int stacks;
		private long expiresAtTick;
		private final long cooldownUntilTick;
		private long effectsAtTick;
		private int appliedVisualStacks;

		private FrostbiteState(
			LivingEntity target,
			int stacks,
			long expiresAtTick,
			long cooldownUntilTick,
			long effectsAtTick
		) {
			this.target = target;
			this.stacks = stacks;
			this.expiresAtTick = expiresAtTick;
			this.cooldownUntilTick = cooldownUntilTick;
			this.effectsAtTick = effectsAtTick;
		}
	}
}
