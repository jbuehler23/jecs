package com.jecs.engine;

import com.jecs.components.Sprite;
import com.jecs.components.SpriteRenderer;
import com.jecs.components.Spritesheet;
import com.jecs.util.AssetPool;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class LevelEditorScene extends Scene {

    private Entity entity1;
    private Spritesheet sprites;

    public LevelEditorScene() {
    }

    @Override
    public void init() {
        loadResources();

        this.camera = new Camera(new Vector2f());

        sprites = AssetPool.getSpritesheet("assets/images/spritesheet.png");

        entity1 = new Entity("Entity 1", new Transform(new Vector2f(100, 100), new Vector2f(256, 256)));
        entity1.addComponent(new SpriteRenderer(sprites.getSprite(0)));
        this.addGameObjectToScene(entity1);

        Entity entity2 = new Entity("Entity 2", new Transform(new Vector2f(400, 100), new Vector2f(256, 256)));
        entity2.addComponent(new SpriteRenderer(sprites.getSprite(10)));
        this.addGameObjectToScene(entity2);



    }

    private void loadResources() {
        AssetPool.getOrAddShader("assets/shaders/default.glsl");
        AssetPool.addSpritesheet("assets/images/spritesheet.png",
                new Spritesheet(AssetPool.getOrAddTexture("assets/images/spritesheet.png"),
                        16,
                        16,
                        26,
                        0));
    }

    private int spriteIndex = 0;
    private float spriteFlipTime = 0.2f;
    private float spriteFlipTimeLeft = 0.0f;

    @Override
    public void update(float dt) {
//        IO.println("FPS: " + (1.0f / dt));
        spriteFlipTimeLeft -= dt;
        if (spriteFlipTimeLeft <= 0) {
            spriteFlipTimeLeft = spriteFlipTime;
            spriteIndex++;
            if (spriteIndex > 4) {
                spriteIndex = 0;
            }
            entity1.getComponent(SpriteRenderer.class).setSprite(sprites.getSprite(spriteIndex));
        }

        for (Entity entity : this.entities) {
            entity.update(dt);
        }

        this.renderer.render();
    }

}
