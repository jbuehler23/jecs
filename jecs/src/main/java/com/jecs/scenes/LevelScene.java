package com.jecs.scenes;

import com.jecs.engine.Window;

public class LevelScene extends Scene {

    public LevelScene() {
        //revert back to white scene
        IO.println("Inside LevelScene");
        Window.get().r = 1;
        Window.get().g = 1;
        Window.get().b = 1;
    }

    @Override
    public void update(float dt) {

    }
}
