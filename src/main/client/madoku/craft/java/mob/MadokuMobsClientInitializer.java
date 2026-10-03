package madoku.craft.java.mob;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint for the standalone Mobs jar. */
public final class MadokuMobsClientInitializer implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MobIndicatorClient.initialize();
	}
}
