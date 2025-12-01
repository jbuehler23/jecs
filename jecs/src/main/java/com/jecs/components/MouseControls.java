package com.jecs.components;

import com.jecs.engine.Entity;
import com.jecs.engine.MouseListener;
import com.jecs.engine.Window;

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
            holdingEntity.transform.position.x = MouseListener.getWorldX() - 16;
            holdingEntity.transform.position.y = MouseListener.getWorldY() - 16;

            if (MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
                place();
            }
        }
    }
}
