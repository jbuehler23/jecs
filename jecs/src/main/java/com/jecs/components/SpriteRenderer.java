package com.jecs.components;

import com.jecs.engine.Component;
import com.jecs.engine.Transform;
import com.jecs.renderer.Texture;
import imgui.ImGui;
import org.joml.Vector2f;
import org.joml.Vector4f;

public class SpriteRenderer extends Component {

    private Vector4f color = new Vector4f(1, 1, 1, 1);

    private Sprite sprite = new Sprite();

    private transient Transform lastTransform;
    private transient boolean isDirty = true;

//    public SpriteRenderer(Vector4f color) {
//        this.color = color;
//        this.sprite = new Sprite(null);
//        this.isDirty = true;
//    }
//
//    public SpriteRenderer(Sprite sprite) {
//        this.sprite = sprite;
//        this.color = new Vector4f(1, 1, 1, 1);
//        this.isDirty = true;
//    }

    @Override
    public void start() {
        this.lastTransform = entity.transform.copy();
    }

    @Override
    public void update(float dt) {
        //the the entity transform has changed
        if (!this.lastTransform.equals(this.entity.transform)) {
            this.entity.transform.copy(this.lastTransform);
            isDirty = true;
        }
    }

    @Override
    public void imgui() {
        float[] imColor = { color.x, color.y, color.z, color.w };
        if (ImGui.colorPicker4("Color Picker: ", imColor)) {
            this.color.set(imColor[0], imColor[1], imColor[2], imColor[3]);
            this.isDirty = true;
        }
    }

    public Vector4f getColor() {
        return color;
    }

    public Texture getTexture() {
        return sprite.getTexture();
    }

    public Vector2f[] getTexCoords() {
        return sprite.getTexCoords();
    }

    private void setSprite(Sprite sprite) {
        this.sprite = sprite;
        this.isDirty = true;
    }

    private void setColor(Vector4f color) {
        if (!this.color.equals(color)) {
            this.isDirty = true;
            this.color.set(color);
        }
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void setClean() {
        this.isDirty = false;
    }


    public SpriteRenderer withColor(Vector4f color) {
        this.setColor(color);
        return this;
    }

    public SpriteRenderer withSprite(Sprite sprite) {
        this.setSprite(sprite);
        return this;
    }
}
