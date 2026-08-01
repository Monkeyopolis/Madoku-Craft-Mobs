package madoku.craft.mobs.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

public final class MadokuMobEntityTypes {
	public static final EntityType<?> BEE = resolve("minecraft:bee");
	public static final EntityType<?> CAVE_SPIDER = resolve("minecraft:cave_spider");
	public static final EntityType<?> CHICKEN = resolve("minecraft:chicken");
	public static final EntityType<?> CREEPER = resolve("minecraft:creeper");
	public static final EntityType<?> DROWNED = resolve("minecraft:drowned");
	public static final EntityType<?> ELDER_GUARDIAN = resolve("minecraft:elder_guardian");
	public static final EntityType<?> ENDER_DRAGON = resolve("minecraft:ender_dragon");
	public static final EntityType<?> HUSK = resolve("minecraft:husk");
	public static final EntityType<?> PARCHED = resolve("minecraft:parched");
	public static final EntityType<?> SKELETON = resolve("minecraft:skeleton");
	public static final EntityType<?> SPIDER = resolve("minecraft:spider");
	public static final EntityType<?> STRAY = resolve("minecraft:stray");
	public static final EntityType<?> WARDEN = resolve("minecraft:warden");
	public static final EntityType<?> WITCH = resolve("minecraft:witch");
	public static final EntityType<?> WITHER = resolve("minecraft:wither");
	public static final EntityType<?> WITHER_SKELETON = resolve("minecraft:wither_skeleton");
	public static final EntityType<?> ZOMBIE = resolve("minecraft:zombie");
	public static final EntityType<?> ZOMBIE_VILLAGER = resolve("minecraft:zombie_villager");
	public static final EntityType<?> BOGGED = resolve("minecraft:bogged");

	private MadokuMobEntityTypes() {}
	private static EntityType<?> resolve(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(identifier);
	}
}
