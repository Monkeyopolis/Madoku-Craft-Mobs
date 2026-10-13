package madoku.craft.java.mob;

/** Public client entrypoint for the Mobs indicator integration. */
public final class MobClientAPIManager {
	private MobClientAPIManager() { }

	public static void initialize() {
		MobIndicatorClient.initialize();
	}
}
