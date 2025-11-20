package com.jecs.engine;

import com.jecs.renderer.Shader;
import com.jecs.renderer.Texture;
import com.jecs.util.Time;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;

import java.awt.event.KeyEvent;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

public class LevelEditorScene extends Scene {

    private float[] vertexArray = {
      // position                         //color                       //uv coordinates
      100.5f, -0.5f, 0.0f,                1.0f, 0.0f, 0.0f, 1.0f,       1, 1,// bottom right 0
     -0.5f,  100.5f, 0.0f,                0.0f, 1.0f, 0.0f, 1.0f,       0, 0,// top left     1
      100.5f,  100.5f, 0.0f,              0.0f, 0.0f, 1.0f, 1.0f,       1, 0,// top right    2
     -0.5f, -0.5f, 0.0f,                  1.0f, 1.0f, 0.0f, 1.0f,       0, 1// bottom left  3
    };

    //IMPORTANT: must be in counter-clockwise order
    private int[] elementArray = {
            /*
                    *       *
                    *       *
             */
            2, 1, 0, // top right triangle
            0, 1, 3 // bottom left triangle
    };

    private int vaoID, vboID, eboID;

    private Texture testTexture;

    private Shader defaultShader;


    public LevelEditorScene() {
    }

    @Override
    public void init() {
        this.camera = new Camera(new Vector2f());
        defaultShader = new Shader("assets/shaders/default.glsl");

        defaultShader.compileAndLink();
        this.testTexture = new Texture("assets/images/testImage.png");

        // Generate VAO, VBO, and EBO buffer objects, then send to the GPU
        vaoID = glGenVertexArrays();
        glBindVertexArray(vaoID);

        // create a float buffer of vertices
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexArray.length);
        vertexBuffer.put(vertexArray).flip();

        // Create VBO upload the vertex buffer
        vboID = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboID);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);

        // Create the indices and upload
        IntBuffer elementBuffer = BufferUtils.createIntBuffer(elementArray.length);
        elementBuffer.put(elementArray).flip();

        eboID = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboID);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer, GL_STATIC_DRAW);

        // Add the vertex attribute pointers
        // how does the GPU know we have position first 3, then color in 4 after (total length of vertex is 7)
        int positionsSize = 3; //x, y, x
        int colorSize = 4; //r, g, b, a
        int uvSize = 2;
        int vertexSizeInBytes = (positionsSize + colorSize + uvSize) * Float.BYTES;
        //position in Shader
        glVertexAttribPointer(0, positionsSize, GL_FLOAT, false, vertexSizeInBytes, 0);
        glEnableVertexAttribArray(0);

        //color
        glVertexAttribPointer(1, colorSize, GL_FLOAT, false, vertexSizeInBytes, positionsSize * Float.BYTES);
        glEnableVertexAttribArray(1);

        //uv coords
        glVertexAttribPointer(2, uvSize, GL_FLOAT, false, vertexSizeInBytes, (positionsSize + colorSize) * Float.BYTES);
        glEnableVertexAttribArray(2);
    }

    @Override
    public void update(float dt) {
//        camera.position.x -= dt * 50.0f;


        defaultShader.use();

        // Upload texture to shader - upload texture ID at slot 0
        defaultShader.uploadTexture("TEX_SAMPLER", 0);
        glActiveTexture(GL_TEXTURE0);
        //bind pushes the texture into slot 0
        testTexture.bind();

        //upload shader matrix
        defaultShader.uploadMat4f("uProjection", camera.getProjectionMatrix());
        defaultShader.uploadMat4f("uView", camera.getViewMatrix());
        defaultShader.uploadFloat("uTime", Time.getTime());
        // Bind the VAO we're using
        glBindVertexArray(vaoID);
        // Enable the vertex attribute pointers
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        //Draw the elements starting from index 0 in element array
        glDrawElements(GL_TRIANGLES, elementArray.length, GL_UNSIGNED_INT, 0);

        // Unbind everything
        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
        glBindVertexArray(0);
        defaultShader.detach();
    }

}
