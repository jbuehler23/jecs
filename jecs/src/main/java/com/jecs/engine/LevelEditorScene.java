package com.jecs.engine;

import com.jecs.components.SpriteRenderer;
import com.jecs.util.AssetPool;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class LevelEditorScene extends Scene {

    public LevelEditorScene() {
    }

    @Override
    public void init() {
        this.camera = new Camera(new Vector2f());

        int xOffset = 10;
        int yOffset = 10;

        float totalWidth = (float) (600 - xOffset * 2);
        float totalHeight = (float) (300 - yOffset * 2);
        float sizeX = totalWidth / 100.0f;
        float sizeY = totalHeight / 100.0f;
//        float padding = 3;

        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 100; y ++) {
                float xPos = xOffset + (x * sizeX);
                float yPos = yOffset + (y * sizeY);

//                float xPos = xOffset + (x * sizeX) + (padding * x);
//                float yPos = yOffset + (y * sizeY) + (padding * y);

                Entity entity = new Entity("Entity " + x + "," + y, new Transform(new Vector2f(xPos, yPos), new Vector2f(sizeX, sizeY)));
                //should create a gradient
                entity.addComponent(new SpriteRenderer(new Vector4f(xPos / totalWidth, yPos / totalHeight, 1, 1)));
                this.addGameObjectToScene(entity);
            }
        }

        loadResources();

    }

    private void loadResources() {
        AssetPool.getOrAddShader("assets/shaders/default.glsl");
    }

    @Override
    public void update(float dt) {
        IO.println("FPS: " + (1.0f / dt));
        for (Entity entity : this.entities) {
            entity.update(dt);
        }

        this.renderer.render();
    }

}
