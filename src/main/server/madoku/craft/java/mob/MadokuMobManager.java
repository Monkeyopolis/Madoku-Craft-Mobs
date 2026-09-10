package madoku.craft.java.mob;

import net.minecraft.server.MinecraftServer;

/** Orchestrates the mob, entity, regional-difficulty, and world-difficulty subsystems. */
public final class MadokuMobManager {
	private MadokuMobManager() {
	}

	public static void initialize() {
		MadokuMobNetworking.initialize();
		MobConfigManager.initialize();
		MobWorldDifficultyManager.initialize();
		MobRegionalDifficultyManager.initialize();
		MobEntityManager.initialize();
	}

	public static void onServerStarted(MinecraftServer server) {
		// Integrated servers can stop and restart inside the same JVM. The server-stop
		// hook clears these runtime configuration snapshots, so reload them before
		// entities begin spawning or loading again.
		MobConfigManager.initialize();
		WorldDifficultyConfigManager.initialize();
		MobRegionalDifficultyManager.onServerStarted(server);
		MobEntityManager.onServerStarted(server);
	}

	public static void onServerTick(MinecraftServer server) {
		MobRegionalDifficultyManager.onServerTick(server);
		MobEntityManager.onServerTick(server);
	}

	public static void onServerStopped() {
		MobEntityManager.onServerStopped();
		MobRegionalDifficultyManager.onServerStopped();
		MobWorldDifficultyManager.onServerStopped();
		MobConfigManager.reset();
		EntityComponentsManager.clearMobBabySettingsCache();
	}

	public static void broadcastDifficultyNow(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyNow(server);
	}

	public static void broadcastDifficultyIfChanged(MinecraftServer server) {
		MobRegionalDifficultyManager.broadcastDifficultyIfChanged(server);
	}
}
