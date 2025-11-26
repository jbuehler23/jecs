package com.jecs.components;

import com.jecs.renderer.Texture;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class Spritesheet {

    //overall spritesheet texture
    private Texture texture;

    private List<Sprite> sprites;

    public Spritesheet(Texture texture, int spriteWidth, int spriteHeight, int numSprites, int padding) {
        this.sprites = new ArrayList<>();

        this.texture = texture;
        int currentX = 0;
        //top left sprite, bottom left corner of uv coordinates
        int currentY = texture.getHeight() - spriteHeight;
        for (int i = 0; i < numSprites; i++) {
            //calculate all uv coords of individual sprites
            //normalize into positions between 0->1 (decimal values)
            float topY = (currentY + spriteHeight) / (float) texture.getHeight();
            float rightX = (currentX + spriteWidth) / (float) texture.getWidth();
            float leftX = currentX / (float) texture.getWidth();
            float bottomY = currentY / (float) texture.getHeight();

            Vector2f[] texCoords = new Vector2f[]{
                    new Vector2f(rightX, topY),
                    new Vector2f(rightX, bottomY),
                    new Vector2f(leftX, bottomY),
                    new Vector2f(leftX, topY),
            };

            Sprite sprite = new Sprite()
                    .withTexture(this.texture)
                    .withTexCoords(texCoords);
            this.sprites.add(sprite);

            currentX += spriteWidth + padding;
            if (currentX >= texture.getWidth()) {
                currentX = 0;
                currentY -= spriteHeight + padding;
            }
        }
    }

    public Sprite getSprite(int index) {
        return this.sprites.get(index);
    }
}
