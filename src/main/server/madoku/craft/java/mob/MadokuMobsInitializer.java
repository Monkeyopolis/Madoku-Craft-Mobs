package madoku.craft.java.mob;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Mobs jar. */
public final class MadokuMobsInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() {
		MobAPIManager.registerProvider(new MadokuMobProvider());
		MobAPIManager.initialize();
	}
	@Override public void onServerStarted(MinecraftServer server) { MobAPIManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MobAPIManager.onServerTick(server); }
	@Override public void onServerStopped(MinecraftServer server) { MobAPIManager.onServerStopped(); }
}
