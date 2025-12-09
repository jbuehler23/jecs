package com.jecs.components.gizmos;

import com.jecs.components.Component;
import com.jecs.components.NonPickable;
import com.jecs.components.Sprite;
import com.jecs.components.SpriteRenderer;
import com.jecs.editor.PropertiesWindow;
import com.jecs.engine.Entity;
import com.jecs.engine.MouseListener;
import com.jecs.engine.Prefabs;
import com.jecs.engine.Window;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;

public class Gizmo extends Component {

    private Vector4f xAxisColor = new Vector4f(1, 0.3f, .3f, 1);
    private Vector4f xAxisColorHover = new Vector4f(1, 0, 0, 1);
    private Vector4f yAxisColor = new Vector4f(0.3f, 1, 0.3f, 1);
    private Vector4f yAxisColorHover = new Vector4f(0, 1, 0, 1);

    private Entity xAxisEntity;
    private Entity yAxisEntity;
    private final PropertiesWindow propertiesWindow;
    private SpriteRenderer xAxisSprite;
    private SpriteRenderer yAxisSprite;
    protected Entity activeEntity = null;

    private Vector2f xAxisOffset = new Vector2f(64, -5);
    private Vector2f yAxisOffset = new Vector2f(16, 61);

    private int gizmoWidth = 16;
    private int gizmoHeight = 48;
    protected boolean xAxisActive = false;
    protected boolean yAxisActive = false;
    private boolean using;

    public Gizmo(Sprite arrowSprite, PropertiesWindow propertiesWindow) {
        this.xAxisEntity = Prefabs.generateSpriteObject(arrowSprite, 24, 48);
        this.yAxisEntity = Prefabs.generateSpriteObject(arrowSprite, 24, 48);
        this.propertiesWindow = propertiesWindow;
        this.xAxisSprite = this.xAxisEntity.getComponent(SpriteRenderer.class);
        this.yAxisSprite = this.yAxisEntity.getComponent(SpriteRenderer.class);

        this.xAxisEntity.addComponent(new NonPickable());
        this.yAxisEntity.addComponent(new NonPickable());

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
        if (!using) {
            return;
        }

        this.activeEntity = this.propertiesWindow.getActiveEntity();
        if (this.activeEntity != null) {
            this.setActive();
        } else {
            this.setInactive();
            return;
        }

        boolean xAxisHot = checkXHoverState();
        boolean yAxisHot = checkYHoverState();

        if ((xAxisHot || xAxisActive) && MouseListener.isDragging() && MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
            xAxisActive = true;
            yAxisActive = false;
        } else if ((yAxisHot || yAxisActive) && MouseListener.isDragging() && MouseListener.mouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
            yAxisActive = true;
            xAxisActive = false;
        } else {
            xAxisActive = false;
            yAxisActive = false;
        }

        if (this.activeEntity != null) {
            this.xAxisEntity.transform.position.set(this.activeEntity.transform.position);
            this.yAxisEntity.transform.position.set(this.activeEntity.transform.position);
            this.xAxisEntity.transform.position.add(this.xAxisOffset);
            this.yAxisEntity.transform.position.add(this.yAxisOffset);
        }
    }

    private boolean checkYHoverState() {
        Vector2f mousePos = new Vector2f(MouseListener.getWorldX(), MouseListener.getWorldY());
        if (mousePos.x <= yAxisEntity.transform.position.x && mousePos.x >= yAxisEntity.transform.position.x - gizmoWidth
                && mousePos.y <= yAxisEntity.transform.position.y && mousePos.y >= yAxisEntity.transform.position.y - gizmoHeight) {
            yAxisSprite.setColor(yAxisColorHover);
            return true;
        }

        yAxisSprite.setColor(yAxisColor);
        return false;
    }

    private boolean checkXHoverState() {
        Vector2f mousePos = new Vector2f(MouseListener.getWorldX(), MouseListener.getWorldY());
        if (mousePos.x <= xAxisEntity.transform.position.x && mousePos.x >= xAxisEntity.transform.position.x - gizmoHeight
                && mousePos.y >= xAxisEntity.transform.position.y && mousePos.y <= xAxisEntity.transform.position.y + gizmoWidth) {
            xAxisSprite.setColor(xAxisColorHover);
            return true;
        }

        xAxisSprite.setColor(xAxisColor);
        return false;
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
    
    public void setUsing() {
        this.using = true;
    }
    
    public void setNotUsing() {
        this.using = false;
        this.setInactive();
    }
}
