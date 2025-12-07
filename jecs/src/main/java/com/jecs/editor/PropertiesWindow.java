package com.jecs.editor;

import com.jecs.engine.Entity;
import com.jecs.engine.MouseListener;
import com.jecs.renderer.PickingTexture;
import com.jecs.scenes.Scene;
import imgui.ImGui;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class PropertiesWindow {
    private Entity activeEntity = null;
    private PickingTexture pickingTexture;

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
        if (MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && MouseListener.isMouseInsideViewport()) {
            int x = (int) MouseListener.getScreenX();
            int y = (int) MouseListener.getScreenY();
            int activeEntityId = pickingTexture.readPixel(x, y);
            activeEntity = currentScene.getEntity(activeEntityId);
        }
    }
}
