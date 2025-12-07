package com.jecs.components;

import com.jecs.engine.Entity;
import com.jecs.engine.MouseListener;
import com.jecs.engine.Window;
import com.jecs.util.Settings;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class MouseControls extends Component {
    //control to do with mouse lives in here
    Entity holdingEntity = null;

    public void pickupEntity(Entity e) {
        this.holdingEntity = e;
        Window.getScene().addEntityToScene(e);
    }

    public void place() {
        this.holdingEntity = null;
    }

    @Override
    public void update(float dt) {
        if (holdingEntity != null) {
            holdingEntity.transform.position.x = MouseListener.getWorldX();
            holdingEntity.transform.position.y = MouseListener.getWorldY();
            if (holdingEntity.transform.position.x >= 0.0f) {
                holdingEntity.transform.position.x = (int) (holdingEntity.transform.position.x / Settings.GRID_WIDTH) * Settings.GRID_WIDTH;
            } else {
                holdingEntity.transform.position.x = (int) (holdingEntity.transform.position.x / Settings.GRID_WIDTH) * Settings.GRID_WIDTH - Settings.GRID_WIDTH;
            }

            if (holdingEntity.transform.position.y >= 0.0f) {
                holdingEntity.transform.position.y = (int) (holdingEntity.transform.position.y / Settings.GRID_HEIGHT) * Settings.GRID_HEIGHT;
            } else {
                holdingEntity.transform.position.y = (int) (holdingEntity.transform.position.y / Settings.GRID_HEIGHT) * Settings.GRID_HEIGHT - Settings.GRID_HEIGHT;
            }

            if (MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
                place();
            }
        }
    }
}
