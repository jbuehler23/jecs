package com.jecs.scenes;

import com.jecs.components.*;
import com.jecs.engine.*;
import com.jecs.renderer.DebugDraw;
import com.jecs.util.AssetPool;
import imgui.ImGui;
import imgui.ImVec2;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class LevelEditorScene extends Scene {

    private Entity entity1;
    private Spritesheet sprites;
    private SpriteRenderer entity1SpriteRenderer;

    Entity levelEditorStuff = new Entity("LevelEditor", new Transform(), 0);

    public LevelEditorScene() {
    }

    @Override
    public void init() {
        levelEditorStuff.addComponent(new MouseControls());
        levelEditorStuff.addComponent(new GridLines());

        loadResources();
        this.camera = new Camera(new Vector2f());
        sprites = AssetPool.getSpritesheet("assets/images/spritesheets/decorationsAndBlocks.png");
//        DebugDraw.addLine2D(new Vector2f(0,0), new Vector2f(800, 800), new Vector3f(1, 0, 0), 6000);
        if (loaded) {
            this.activeEntity = entities.getFirst();
            return;
        }


//        entity1 = new Entity(
//                "Entity 1",
//                new Transform(new Vector2f(200, 100), new Vector2f(256, 256)),
//                2);
//        entity1SpriteRenderer = new SpriteRenderer().withColor(new Vector4f(1, 0, 0, 1));
//        entity1.addComponent(entity1SpriteRenderer);
//        entity1.addComponent(new RigidBody());
//        this.addEntityToScene(entity1);
//        //hardcode the selected entity to be this one
//        this.activeEntity = entity1;
//
//        Entity entity2 = new Entity(
//                "Entity 2",
//                new Transform(new Vector2f(400, 100), new Vector2f(256, 256))
//                ,3);
//        SpriteRenderer entity2SpriteRenderer = new SpriteRenderer()
//                .withSprite(new Sprite().setTexture(AssetPool.getOrAddTexture("assets/images/blendImage2.png")));
//        entity2.addComponent(entity2SpriteRenderer);
//        this.addEntityToScene(entity2);



    }

    private void loadResources() {
        AssetPool.getOrAddShader("assets/shaders/default.glsl");

        AssetPool.addSpritesheet("assets/images/spritesheets/decorationsAndBlocks.png",
                new Spritesheet(AssetPool.getOrAddTexture("assets/images/spritesheets/decorationsAndBlocks.png"),
                        16,
                        16,
                        81,
                        0));
        AssetPool.getOrAddTexture("assets/images/blendImage2.png");
    }

//    private int spriteIndex = 0;
//    private float spriteFlipTime = 0.2f;
//    private float spriteFlipTimeLeft = 0.0f;

//    float t = 0.0f;
    float angle = 0.0f;
    float x = 0.0f;
    float y = 0.0f;
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

//        float x = ((float) Math.sin(t) * 200.0f) + 600;
//        float y = ((float) Math.cos(t) * 200.0f) + 400;
//        t += 0.05f;
//        DebugDraw.addLine2D(new Vector2f(600, 400), new Vector2f(x, y), new Vector3f(0, 0, 1));

        levelEditorStuff.update(dt);
        DebugDraw.addCircle2D(new Vector2f(x, y), 64, new Vector3f(0, 1, 0), 1);
        x += 50f * dt;
        y += 50f * dt;

//        DebugDraw.addBox2D(new Vector2f(400, 200), new Vector2f(64, 32), angle, new Vector3f(0, 1, 0), 1);
        angle += 10.0f * dt;
//        float mouseX = MouseListener.getWorldX();
//        float mouseY = MouseListener.getWorldY();
//        // Draw a small crosshair at mouse position
//        DebugDraw.addLine2D(new Vector2f(mouseX - 20, mouseY), new Vector2f(mouseX + 20, mouseY), new Vector3f(1, 0, 0));
//        DebugDraw.addLine2D(new Vector2f(mouseX, mouseY - 20), new Vector2f(mouseX, mouseY + 20), new Vector3f(0, 1, 0));

        for (Entity entity : this.entities) {
            entity.update(dt);
        }

        this.renderer.render();
    }

    @Override
    public void imgui() {
        ImGui.begin("Test Window");

        ImVec2 windowPos = new ImVec2();
        ImGui.getWindowPos(windowPos);
        ImVec2 windowSize = new ImVec2();
        ImGui.getWindowSize(windowSize);
        ImVec2 itemSpacing = new ImVec2();
        ImGui.getStyle().getItemSpacing(itemSpacing);

        float windowX2 = windowPos.x + windowSize.x;
        for (int i = 0; i < sprites.size(); i++) {
            //loop through the sprites in the sheet and place on a new line if we need to
            Sprite sprite = sprites.getSprite(i);
            float spriteWidth = sprite.getWidth() * 2;
            float spriteHeight = sprite.getHeight() * 2;
            int id = sprite.getTexId();
            Vector2f[] texCoords = sprite.getTexCoords();

            ImGui.pushID(i);
            if (ImGui.imageButton(String.valueOf(id), id, spriteWidth, spriteHeight, texCoords[2].x, texCoords[0].y, texCoords[0].x, texCoords[2].y)) {
                Entity entity = Prefabs.generateSpriteObject(sprite, 32, 32);
                // Attach this to mouse cursor
                levelEditorStuff.getComponent(MouseControls.class).pickupEntity(entity);
            }
            ImGui.popID();

            ImVec2 lastButtonPos = new ImVec2();
            ImGui.getItemRectMax(lastButtonPos);
            float lastButtonX2 = lastButtonPos.x;
            float nextButtonX2 = lastButtonX2 + itemSpacing.x + spriteWidth;

            if (i + 1 < sprites.size() && nextButtonX2 < windowX2) {
                ImGui.sameLine();
            }
        }
        ImGui.end();
    }
}
