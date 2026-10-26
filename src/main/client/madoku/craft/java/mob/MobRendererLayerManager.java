package madoku.craft.java.mob;

import java.util.List;
import java.util.function.UnaryOperator;

/** Shared helpers for replacing or extending vanilla living-entity renderer layers. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class MobRendererLayerManager {
	private MobRendererLayerManager() {
	}

	public static int removeAll(List<?> layers, Class<?> layerType) {
		if (layers == null || layerType == null) {
			return 0;
		}
		int removed = 0;
		List rawLayers = layers;
		for (int index = rawLayers.size() - 1; index >= 0; index--) {
			if (layerType.isInstance(rawLayers.get(index))) {
				rawLayers.remove(index);
				removed++;
			}
		}
		return removed;
	}

	public static boolean replaceFirst(List<?> layers, Class<?> layerType, Object replacement) {
		if (layers == null || layerType == null || replacement == null) {
			return false;
		}
		List rawLayers = layers;
		for (int index = 0; index < rawLayers.size(); index++) {
			if (layerType.isInstance(rawLayers.get(index))) {
				rawLayers.set(index, replacement);
				return true;
			}
		}
		return false;
	}

	public static <T> boolean wrapFirst(List<T> layers, Class<?> layerType, UnaryOperator<T> wrapper) {
		if (layers == null || layerType == null || wrapper == null) {
			return false;
		}
		for (int index = 0; index < layers.size(); index++) {
			T layer = layers.get(index);
			if (layerType.isInstance(layer)) {
				layers.set(index, wrapper.apply(layer));
				return true;
			}
		}
		return false;
	}

	public static boolean addIfAbsent(List<?> layers, Class<?> layerType, Object layer) {
		if (layers == null || layerType == null || layer == null) {
			return false;
		}
		for (Object existing : layers) {
			if (layerType.isInstance(existing)) {
				return false;
			}
		}
		List rawLayers = layers;
		rawLayers.add(layer);
		return true;
	}
}
