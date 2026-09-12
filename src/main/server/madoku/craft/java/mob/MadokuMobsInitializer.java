package madoku.craft.java.mob;

import madoku.craft.java.core.module.MadokuStandaloneModule;
import madoku.craft.java.core.module.MadokuStandaloneRuntime;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.MinecraftServer;

/** Fabric entrypoint for the standalone Mobs jar. */
public final class MadokuMobsInitializer implements ModInitializer, MadokuStandaloneModule {
	@Override public void onInitialize() { MadokuStandaloneRuntime.initialize(this); }
	@Override public void initialize() { MadokuMobManager.initialize(); }
	@Override public void onServerStarted(MinecraftServer server) { MadokuMobManager.onServerStarted(server); }
	@Override public void onServerTick(MinecraftServer server) { MadokuMobManager.onServerTick(server); }
	@Override public void onServerStopped(MinecraftServer server) { MadokuMobManager.onServerStopped(); }
}
