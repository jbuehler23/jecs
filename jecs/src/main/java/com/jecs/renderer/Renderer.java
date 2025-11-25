package com.jecs.renderer;

import com.jecs.components.SpriteRenderer;
import com.jecs.engine.Entity;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Renderer {

    private final int MAX_BATCH_SIZE = 1000;
    private List<RenderBatch> batches;

    public Renderer() {
        this.batches = new ArrayList<>();
    }

    public void add(Entity entity) {
        SpriteRenderer spr = entity.getComponent(SpriteRenderer.class);
        if (spr != null) {
            addSpriteRenderer(spr);
        }
    }

    private void addSpriteRenderer(SpriteRenderer spriteRenderer) {
        boolean added = false;
        for (RenderBatch batch : batches) {
            //check to make sure the current batch z-index matches the sprites z-index
            if (batch.hasRoom() && batch.zIndex() == spriteRenderer.entity.zIndex()) {
                Texture tex = spriteRenderer.getTexture();
                if (tex == null || (batch.hasTexture(tex) || batch.hasTextureRoom())) {
                    batch.addSprite(spriteRenderer);
                    added = true;
                    break;
                }
            }
        }
        if (!added) {
            RenderBatch newBatch = new RenderBatch(MAX_BATCH_SIZE, spriteRenderer.entity.zIndex());
            newBatch.start();
            batches.add(newBatch);
            newBatch.addSprite(spriteRenderer);
            //sort batches by z-index
            Collections.sort(batches);
        }
    }

    public void render() {
        for (RenderBatch batch : batches) {
            batch.render();
        }
    }
}
