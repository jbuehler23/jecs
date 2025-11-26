package com.jecs.engine;

public abstract class Component {


    public transient Entity entity = null;

    public void update(float dt){
        //NOOP
    }

    public void start() {
        //NOOP
    }

    public void imgui() {
        //NOOP
    }
}
