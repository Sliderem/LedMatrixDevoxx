package com.devoxx.led.scene;

import java.util.List;

/** The complete catalogue of Devoxx 2026 scenes, in a stable render order. */
public final class SceneRegistry {

    private SceneRegistry() {
    }

    /** The four themed scenes in a stable order. */
    public static List<Scene> all() {
        return List.of(
                new DevoxxScene(),
                new GoogleCloudScene(),
                new JavaCoffeeScene(),
                new PlasmaScene());
    }
}
