package madoku.craft.mobs;

import madoku.craft.API.system.MadokuInfoDebugSystem;
import madoku.craft.API.system.MadokuTickSystem;
import madoku.craft.mobs.system.CreeperMobSystem;
import madoku.craft.mobs.system.PillagerMobSystem;
import madoku.craft.mobs.system.SkeletonMobSystem;
import madoku.craft.mobs.system.SpiderMobSystem;
import madoku.craft.mobs.system.ZombieMobSystem;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MadokuCraftMobs implements ModInitializer {
	public static final String MOD_ID = "madoku-craft-mobs";
	public static final String LOG_SOURCE = "MOBS";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		infoDebug(LOG_SOURCE, "Initializing {}.", MOD_ID);
		MadokuTickSystem.init();
		ZombieMobSystem.init();
		SpiderMobSystem.init();
		CreeperMobSystem.init();
		SkeletonMobSystem.init();
		PillagerMobSystem.init();
		infoDebug(LOG_SOURCE, "{} systems ready.", MOD_ID);
		LOGGER.info("Madoku Craft Mobs initialized.");
	}

	public static void infoDebug(String source, String message, Object... args) {
		MadokuInfoDebugSystem.info(LOGGER, source, message, args);
	}
}
