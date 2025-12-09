package com.jecs.components.gizmos;

import com.jecs.components.Sprite;
import com.jecs.editor.PropertiesWindow;
import com.jecs.engine.MouseListener;

public class TranslateGizmo extends Gizmo {

    public TranslateGizmo(Sprite arrowSprite, PropertiesWindow propertiesWindow) {
        super(arrowSprite, propertiesWindow);
    }


    @Override
    public void update(float dt) {
        if (activeEntity != null){
            if (xAxisActive && !yAxisActive) {
                activeEntity.transform.position.x -= MouseListener.getWorldDx();
            } else if (yAxisActive) {
                activeEntity.transform.position.y -= MouseListener.getWorldDy();
            }
        }

        super.update(dt);
    }

}
