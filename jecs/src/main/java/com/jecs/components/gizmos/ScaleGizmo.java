package com.jecs.components.gizmos;

import com.jecs.components.Sprite;
import com.jecs.editor.PropertiesWindow;
import com.jecs.engine.MouseListener;

public class ScaleGizmo extends Gizmo {

    public ScaleGizmo(Sprite scaleSprite, PropertiesWindow propertiesWindow) {
        super(scaleSprite, propertiesWindow);
    }


    @Override
    public void update(float dt) {
        if (activeEntity != null){
            if (xAxisActive && !yAxisActive) {
                activeEntity.transform.scale.x -= MouseListener.getWorldDx();
            } else if (yAxisActive) {
                activeEntity.transform.scale.y -= MouseListener.getWorldDy();
            }
        }

        super.update(dt);
    }
}
