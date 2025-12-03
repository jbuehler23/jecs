package com.jecs.renderer;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;

public class Texture {
    private String filepath;
    private int texID;
    private int width, height;

    public Texture() {
        texID = -1;
        this.width = -1;
        this.height = -1;
    }

    public Texture(int width, int height) {
        this.filepath = "Generated";

        //generate Texture on GPU
        this.texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0);
    }

    public Texture init(String filepath) {
        this.filepath = filepath;

        //generate Texture on GPU
        this.texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);

        //Set texture parameters
        // Repeat the image in both directions
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        // When stretching the image, it should pixelate (no blur)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        // When shrinking the image, we want to pixelate (no blur)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        IntBuffer channels = BufferUtils.createIntBuffer(1);
        stbi_set_flip_vertically_on_load(true);
        //use image loading library - width, height, rgb or rgba
        ByteBuffer image = stbi_load(filepath, width, height, channels, 0);
        if (image != null) {
            this.width = width.get(0);
            this.height = height.get(0);
            if (channels.get(0) == 4) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            } else if (channels.get(0) == 3) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width.get(0), height.get(0), 0, GL_RGB, GL_UNSIGNED_BYTE, image);
            } else {
                assert false: "Error in texture: unknown number of channels: " + channels.get(0);
            }
        } else {
            assert false : "Error (Texture): Could not load image " + filepath;
        }

        //frees memory that STB has allocated - not using JVM
        stbi_image_free(image);
        return this;
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, texID);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getId() {
        return this.texID;
    }

    public String getFilepath() {
        return filepath;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Texture texture = (Texture) o;
        return texID == texture.texID && width == texture.width && height == texture.height && Objects.equals(filepath, texture.filepath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filepath, texID, width, height);
    }
}
