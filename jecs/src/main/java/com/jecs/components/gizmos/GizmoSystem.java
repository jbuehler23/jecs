package com.jecs.components.gizmos;

import com.jecs.components.Component;
import com.jecs.components.Spritesheet;
import com.jecs.engine.KeyListener;
import com.jecs.engine.Window;

import java.security.Key;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;

public class GizmoSystem extends Component {
    private Spritesheet gizmos;
    private enum GizmoMode {
        TRANSLATE,
        SCALE,
        ROTATE
    }

    private GizmoMode usingGizmo = GizmoMode.TRANSLATE;

    public GizmoSystem(Spritesheet spritesheet) {
        gizmos = spritesheet;
    }

    @Override
    public void start() {
        entity.addComponent(new TranslateGizmo(gizmos.getSprite(1), Window.getImGuiLayer().getPropertiesWindow()));
        entity.addComponent(new ScaleGizmo(gizmos.getSprite(2), Window.getImGuiLayer().getPropertiesWindow()));
    }

    @Override
    public void update(float dt) {
        if (usingGizmo.equals(GizmoMode.TRANSLATE)) {
            entity.getComponent(TranslateGizmo.class).setUsing();
            entity.getComponent(ScaleGizmo.class).setNotUsing();
        } else if (usingGizmo.equals(GizmoMode.SCALE)) {
            entity.getComponent(TranslateGizmo.class).setNotUsing();
            entity.getComponent(ScaleGizmo.class).setUsing();
        }

        if (KeyListener.isKeyPressed(GLFW_KEY_E)) {
            usingGizmo = GizmoMode.TRANSLATE;
        } else if (KeyListener.isKeyPressed(GLFW_KEY_R)) {
            usingGizmo = GizmoMode.SCALE;
        }
    }
}
