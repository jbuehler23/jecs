package com.jecs.engine;

import com.jecs.renderer.Renderer;
import imgui.ImGui;

import java.util.ArrayList;
import java.util.List;

public abstract class Scene {

    protected Renderer renderer = new Renderer();
    protected Camera camera;

    private boolean isRunning = false;
    final List<Entity> entities = new ArrayList<>();
    protected Entity activeEntity = null;

    public Scene() {

    }

    public abstract void update(float dt);

    public void init() {

    }

    public void start() {
        for (Entity entity : entities) {
            entity.start();
            this.renderer.add(entity);
        }
        isRunning = true;
    }

    public void addGameObjectToScene(Entity entity) {
        if (!isRunning) {
            entities.add(entity);
        } else {
            entities.add(entity);
            entity.start();
            this.renderer.add(entity);
        }
    }

    public Camera camera() {
        return this.camera;
    }

    public void sceneImgui(){
        if (activeEntity != null) {
            ImGui.begin("Inspector");
            activeEntity.imgui();
            ImGui.end();
        }

        imgui();
    }

    public void imgui() {
        //create custom scene-integrated stuff
    }
}
