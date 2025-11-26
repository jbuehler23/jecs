package com.jecs.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jecs.components.Sprite;
import com.jecs.components.SpriteRenderer;
import com.jecs.components.Spritesheet;
import com.jecs.renderer.Texture;
import com.jecs.util.AssetPool;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class LevelEditorScene extends Scene {

    private Entity entity1;
    private Spritesheet sprites;
    private SpriteRenderer entity1SpriteRenderer;

    public LevelEditorScene() {
    }

    @Override
    public void init() {
        loadResources();

        this.camera = new Camera(new Vector2f());

        sprites = AssetPool.getSpritesheet("assets/images/spritesheet.png");

        entity1 = new Entity(
                "Entity 1",
                new Transform(new Vector2f(200, 100), new Vector2f(256, 256)),
                2);
        entity1SpriteRenderer = new SpriteRenderer().withColor(new Vector4f(1, 0, 0, 1));
        entity1.addComponent(entity1SpriteRenderer);
        this.addGameObjectToScene(entity1);
        //hardcode the selected entity to be this one
        this.activeEntity = entity1;

        Entity entity2 = new Entity(
                "Entity 2",
                new Transform(new Vector2f(400, 100), new Vector2f(256, 256))
                ,3);
        SpriteRenderer entity2SpriteRenderer = new SpriteRenderer()
                .withSprite(new Sprite().withTexture(AssetPool.getOrAddTexture("assets/images/blendImage2.png")));
        entity2.addComponent(entity2SpriteRenderer);
        this.addGameObjectToScene(entity2);

        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        String serialized = gson.toJson(entity1);
        Entity entity = gson.fromJson(serialized, Entity.class);
        IO.println(entity);


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

//    private int spriteIndex = 0;
//    private float spriteFlipTime = 0.2f;
//    private float spriteFlipTimeLeft = 0.0f;

    @Override
    public void update(float dt) {
//        IO.println("FPS: " + (1.0f / dt));
//        spriteFlipTimeLeft -= dt;
//        if (spriteFlipTimeLeft <= 0) {
//            spriteFlipTimeLeft = spriteFlipTime;
//            spriteIndex++;
//            if (spriteIndex > 4) {
//                spriteIndex = 0;
//            }
//            entity1.getComponent(SpriteRenderer.class).setSprite(sprites.getSprite(spriteIndex));
//        }
//        Gson gson = new GsonBuilder()
//                .setPrettyPrinting()
//                .create();
//
//        IO.println(gson.toJson(entity1SpriteRenderer));


        for (Entity entity : this.entities) {
            entity.update(dt);
        }

        this.renderer.render();
    }

    @Override
    public void imgui() {
        ImGui.begin("Test Window");
        ImGui.text("Some random text");
        ImGui.end();
    }
}
