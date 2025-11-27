package com.jecs.components;

import com.jecs.renderer.Texture;
import org.joml.Vector2f;

public class Sprite {


    private Texture texture = null;
    private Vector2f[] texCoords = new Vector2f[]{
                new Vector2f(1, 1),
                new Vector2f(1, 0),
                new Vector2f(0, 0),
                new Vector2f(0, 1),
        };
    private int height, width;

    public Sprite setTexture(Texture texture) {
        this.texture = texture;
        return this;
    }

    public Texture getTexture() {
        return texture;
    }

    public Vector2f[] getTexCoords() {
        return texCoords;
    }

    public Sprite setTextureCoords(Vector2f[] texCoords) {
        this.texCoords = texCoords;
        return this;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Sprite setHeight(int spriteHeight) {
        this.height = spriteHeight;
        return this;
    }

    public Sprite setWidth(int spriteWidth) {
        this.width = spriteWidth;
        return this;
    }

    public int getTexId() {
        return texture == null ?  -1 : texture.getId();
    }
}
