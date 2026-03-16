package madoku.craft.mobs;

import madoku.craft.mobs.difficulty.system.MadokuDifficulty;
import madoku.craft.mobs.mob.system.MadokuMob;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MadokuCraftMobs implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MadokuDifficulty.initialize();
		MadokuMob.initialize();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			MadokuMob.onServerStarted(server);
			MadokuDifficulty.onServerStarted(server);
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MadokuDifficulty.onServerStopped();
			MadokuMob.onServerStopped();
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MadokuDifficulty.onServerTick(server);
			MadokuMob.onServerTick(server);
		});

		LOGGER.info("Madoku Craft Mobs initialized.");
	}
}
