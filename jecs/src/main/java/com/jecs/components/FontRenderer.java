package com.jecs.components;

public class FontRenderer extends Component {

    @Override
    public void start() {
        if (entity.getComponent(SpriteRenderer.class) != null) {
            IO.println("Found Font Renderer!");
        }
    }

    @Override
    public void update(float dt) {

    }
}
