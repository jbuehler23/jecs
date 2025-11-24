package com.jecs.util;

import com.jecs.components.Spritesheet;
import com.jecs.renderer.Shader;
import com.jecs.renderer.Texture;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * ObjectPool implementation of assets
 * Stores memory references to Objects so they don't get called by the GC (object references are 4 bytes)
 */
public class AssetPool {
    private static Map<String, Shader> shaders = new HashMap<>();
    private static Map<String, Texture> textures = new HashMap<>();
    private static Map<String, Spritesheet> spritesheets = new HashMap<>();


    /**
     * Gets a shader from absolute path (e.g. assets/shaders/default.glsl)
     * If it doesn't exist, adds to object pool
     * @param resourceName
     * @return
     */
    public static Shader getOrAddShader(String resourceName) {
        File file = new File(resourceName);
        if (AssetPool.shaders.containsKey(file.getAbsolutePath())) {
            return AssetPool.shaders.get(file.getAbsolutePath());
        } else {
            //TODO: could just throw an error instead of adding if it's not loaded before trying to get
            Shader shader = new Shader(resourceName);
            shader.compileAndLink();
            // doesn't store the actual shader - just the reference (4 bytes)
            AssetPool.shaders.put(file.getAbsolutePath(), shader);
            return shader;
        }
    }

    public static Texture getOrAddTexture(String resourceName) {
        File file = new File(resourceName);
        if (AssetPool.textures.containsKey(file.getAbsolutePath())) {
            return AssetPool.textures.get(file.getAbsolutePath());
        } else {
            //TODO: could just throw an error instead of adding if it's not loaded before trying to get
            Texture texture = new Texture(resourceName);
            AssetPool.textures.put(file.getAbsolutePath(), texture);
            return texture;
        }
    }

    public static void addSpritesheet(String resourceName, Spritesheet spritesheet) {
        File file = new File(resourceName);
        if (!AssetPool.spritesheets.containsKey(file.getAbsolutePath())) {
            AssetPool.spritesheets.put(file.getAbsolutePath(), spritesheet);
        }
    }

    public static Spritesheet getSpritesheet(String resourceName) {
        File file = new File(resourceName);
        assert AssetPool.spritesheets.containsKey(file.getAbsolutePath()) : "Error: Tried to access spritesheet " + resourceName + " and has not been added to AssetPool";
        return AssetPool.spritesheets.getOrDefault(file.getAbsolutePath(), null);
    }
}
