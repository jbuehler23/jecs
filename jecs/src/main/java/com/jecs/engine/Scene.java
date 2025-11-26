package com.jecs.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jecs.renderer.Renderer;
import imgui.ImGui;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public abstract class Scene {

    protected Renderer renderer = new Renderer();
    protected Camera camera;

    private boolean isRunning = false;
    final List<Entity> entities = new ArrayList<>();
    protected Entity activeEntity = null;
    protected boolean loaded = false;

    public Scene() {

    }

    public abstract void update(float dt);

    public void init() {

    }

    public void start() {
        for (Entity entity : entities) {
            entity.start();
            this.renderer.add(entity);
        }
        isRunning = true;
    }

    public void addGameObjectToScene(Entity entity) {
        if (!isRunning) {
            entities.add(entity);
        } else {
            entities.add(entity);
            entity.start();
            this.renderer.add(entity);
        }
    }

    public Camera camera() {
        return this.camera;
    }

    public void sceneImgui(){
        if (activeEntity != null) {
            ImGui.begin("Inspector");
            activeEntity.imgui();
            ImGui.end();
        }

        imgui();
    }

    public void imgui() {
        //create custom scene-integrated stuff
    }

    public void load() {
        IO.println("Loading scene");
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentSerde())
                .registerTypeAdapter(Entity.class, new EntitySerde())
                .create();
        String inFile = "";
        try {
            inFile = new String(Files.readAllBytes(Paths.get("level.txt")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!inFile.isEmpty()) {
            Entity[] entities = gson.fromJson(inFile, Entity[].class);
            for (Entity e : entities) {
                addGameObjectToScene(e);
            }
            this.loaded = true;
        }
    }

    public void save() {
        IO.println("Saving scene");
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Component.class, new ComponentSerde())
                .registerTypeAdapter(Entity.class, new EntitySerde())
                .create();
        try {
            FileWriter writer = new FileWriter("level.txt");
            writer.write(gson.toJson(this.entities));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
