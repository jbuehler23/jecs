package com.jecs.components;

import com.jecs.editor.PropertiesWindow;
import com.jecs.engine.Entity;
import com.jecs.engine.Prefabs;
import com.jecs.engine.Window;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class TranslateGizmo extends Component {
    private Vector4f xAxisColor = new Vector4f(1, 0, 0, 1);
    private Vector4f xAxisColorHover = new Vector4f();
    private Vector4f yAxisColor = new Vector4f(0, 1, 0, 1);
    private Vector4f yAxisColorHover = new Vector4f();

    private Entity xAxisEntity;
    private Entity yAxisEntity;
    private final PropertiesWindow propertiesWindow;
    private SpriteRenderer xAxisSprite;
    private SpriteRenderer yAxisSprite;
    private Entity activeEntity = null;

    private Vector2f xAxisOffset = new Vector2f(64, -5);
    private Vector2f yAxisOffset = new Vector2f(16, 61);

    public TranslateGizmo(Sprite arrowSprite, PropertiesWindow propertiesWindow) {
        this.xAxisEntity = Prefabs.generateSpriteObject(arrowSprite, 24, 48);
        this.yAxisEntity = Prefabs.generateSpriteObject(arrowSprite, 24, 48);
        this.propertiesWindow = propertiesWindow;
        this.xAxisSprite = this.xAxisEntity.getComponent(SpriteRenderer.class);
        this.yAxisSprite = this.yAxisEntity.getComponent(SpriteRenderer.class);

        Window.getScene().addEntityToScene(this.xAxisEntity);
        Window.getScene().addEntityToScene(this.yAxisEntity);
    }

    @Override
    public void start() {
        this.xAxisEntity.transform.rotation = 90;
        this.yAxisEntity.transform.rotation = 180;
        this.xAxisEntity.setNoSerialize();
        this.yAxisEntity.setNoSerialize();
    }

    @Override
    public void update(float dt) {
        if (this.activeEntity != null) {
            this.xAxisEntity.transform.position.set(this.activeEntity.transform.position);
            this.yAxisEntity.transform.position.set(this.activeEntity.transform.position);
            this.xAxisEntity.transform.position.add(this.xAxisOffset);
            this.yAxisEntity.transform.position.add(this.yAxisOffset);
        }

        this.activeEntity = this.propertiesWindow.getActiveEntity();
        if (this.activeEntity != null) {
            this.setActive();
        } else {
            this.setInactive();
        }
    }

    private void setActive() {
        this.xAxisSprite.setColor(xAxisColor);
        this.yAxisSprite.setColor(yAxisColor);
    }

    private void setInactive() {
        this.activeEntity = null;
        this.xAxisSprite.setColor(new Vector4f(0, 0, 0, 0));
        this.yAxisSprite.setColor(new Vector4f(0, 0, 0, 0));
    }
}
