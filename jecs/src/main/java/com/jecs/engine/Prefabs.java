package com.jecs.engine;

import com.jecs.components.Sprite;
import com.jecs.components.SpriteRenderer;
import org.joml.Vector2f;

public class Prefabs {

    public static Entity generateSpriteObject(Sprite sprite, float sizeX, float sizeY) {
        Entity block = new Entity("Sprite_Object_Gen",
                new Transform(new Vector2f(), new Vector2f(sizeX, sizeY)),
                0);
        SpriteRenderer spriteRenderer = new SpriteRenderer().withSprite(sprite);
        block.addComponent(spriteRenderer);
        return block;
    }
}
