package com.jecs.editor;

import com.jecs.components.NonPickable;
import com.jecs.engine.Entity;
import com.jecs.engine.MouseListener;
import com.jecs.renderer.PickingTexture;
import com.jecs.scenes.Scene;
import imgui.ImGui;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class PropertiesWindow {
    private Entity activeEntity = null;
    private PickingTexture pickingTexture;

    private float debounce = 0.2f;

    public PropertiesWindow(PickingTexture pickingTexture) {
        this.pickingTexture = pickingTexture;
    }

    public void imgui() {
        if (activeEntity != null) {
            ImGui.begin("Inspector");
            activeEntity.imgui();
            ImGui.end();
        }
    }

    public void update(float dt, Scene currentScene) {
        debounce -= dt;
        if (MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && MouseListener.isMouseInsideViewport() && debounce < 0) {
            int x = (int) MouseListener.getScreenX();
            int y = (int) MouseListener.getScreenY();
            int activeEntityId = pickingTexture.readPixel(x, y);
            Entity pickedEntity = currentScene.getEntity(activeEntityId);
            if (pickedEntity != null && pickedEntity.getComponent(NonPickable.class) == null) {
                activeEntity = pickedEntity;
            } else if (pickedEntity == null && !MouseListener.isDragging()) {
                activeEntity = null;
            }
            this.debounce = 0.2f;
        }
    }

    public Entity getActiveEntity() {
        return this.activeEntity;
    }
}
