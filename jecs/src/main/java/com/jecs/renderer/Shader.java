package com.jecs.renderer;

import org.joml.*;
import org.lwjgl.BufferUtils;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.glGetShaderInfoLog;

public class Shader {

    private int shaderProgramID;
    private boolean beingUsed = false;

    private String vertexSource;
    private String fragmentSource;
    private String filepath;

    public Shader(String filepath) {
        this.filepath = filepath;
        try {
            String source = new String(Files.readAllBytes(Paths.get(filepath)));
            String[] splitString = source.split("(#type)( )+([a-zA-Z]+)");

            // Find the first pattern after #type 'pattern' (vertex/fragment)
            int index = source.indexOf("#type") + 6;
            int eol = source.indexOf("\r\n", index);
            String firstPattern = source.substring(index, eol).trim();

            // Find the second pattern after #type 'pattern' (vertex/fragment)
            index = source.indexOf("#type", eol) + 6;
            eol = source.indexOf("\r\n", index);
            String secondPattern = source.substring(index, eol).trim();
            
            if (firstPattern.equals("vertex")) {
                vertexSource = splitString[1];
            } else if (firstPattern.equals("fragment")) {
                fragmentSource = splitString[1];
            } else {
                throw new IOException("Unexpected token "+ firstPattern);
            }

            if (secondPattern.equals("vertex")) {
                vertexSource = splitString[2];
            } else if (secondPattern.equals("fragment")) {
                fragmentSource = splitString[2];
            } else {
                throw new IOException("Unexpected token "+ firstPattern);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            assert false : "Error: Could not open Shader File: " + filepath;
        }
    }

    public void compileAndLink() {
        int vertexID, fragmentID;
        //Compile and Link Shaders

        //First load and compile vertex shader
        vertexID = glCreateShader(GL_VERTEX_SHADER);
        //Pass the shader source to GPU
        glShaderSource(vertexID, vertexSource);
        glCompileShader(vertexID);

        //Check if there were any errors in compilation
        int success = glGetShaderi(vertexID, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            int len = glGetShaderi(vertexID, GL_INFO_LOG_LENGTH);
            IO.println("ERROR: " + filepath + " - Vertex shader compilation failed.");
            IO.println(glGetShaderInfoLog(vertexID, len));
            assert false : "";
        }

        //Next, load fragment shader and compile vertex shader
        fragmentID = glCreateShader(GL_FRAGMENT_SHADER);
        //Pass the shader source to GPU
        glShaderSource(fragmentID, fragmentSource);
        glCompileShader(fragmentID);

        //Check if there were any errors in compilation
        success = glGetShaderi(fragmentID, GL_COMPILE_STATUS);
        if (success == GL_FALSE) {
            int len = glGetShaderi(fragmentID, GL_INFO_LOG_LENGTH);
            IO.println("ERROR: " + filepath + " - Fragment shader compilation failed.");
            IO.println(glGetShaderInfoLog(fragmentID, len));
            assert false : "";
        }

        // Link shaders and check for errors
        shaderProgramID = glCreateProgram();
        glAttachShader(shaderProgramID, vertexID);
        glAttachShader(shaderProgramID, fragmentID);
        glLinkProgram(shaderProgramID);

        // Check for Linking errors:
        success = glGetProgrami(shaderProgramID, GL_LINK_STATUS);
        if (success == GL_FALSE) {
            int len = glGetProgrami(shaderProgramID, GL_INFO_LOG_LENGTH);
            IO.println("ERROR: " + filepath + " - Linking of shaders failed.");
            IO.println(glGetProgramInfoLog(shaderProgramID, len));
            assert false : "";
        }


    }

    public void use() {
        if (!beingUsed) {
            // Bind shader program
            glUseProgram(shaderProgramID);
            beingUsed = true;
        }
    }

    public void detach() {
        glUseProgram(0);
        beingUsed = false;
    }

    public void uploadMat4f(String varName, Matrix4f matrix4f) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        //4x4 matrix
        FloatBuffer matBuffer = BufferUtils.createFloatBuffer(16);
        matrix4f.get(matBuffer); // flatten into 1d array [1, 1, 1, 1, ... ]
        glUniformMatrix4fv(varLocation, false, matBuffer);
    }

    public void uploadMat3f(String varName, Matrix3f matrix3f) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        //3x3 matrix
        FloatBuffer matBuffer = BufferUtils.createFloatBuffer(9);
        matrix3f.get(matBuffer); // flatten into 1d array [1, 1, 1, 1, ... ]
        glUniformMatrix3fv(varLocation, false, matBuffer);
    }

    public void uploadVec4f(String varName, Vector4f vec) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform4f(varLocation, vec.x, vec.y, vec.z, vec.w);
    }

    public void uploadVec3f(String varName, Vector3f vec) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform3f(varLocation, vec.x, vec.y, vec.z);
    }

    public void uploadVec2f(String varName, Vector2f vec) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform2f(varLocation, vec.x, vec.y);
    }

    public void uploadFloat(String varName, float val) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform1f(varLocation, val);
    }

    public void uploadInt(String varName, int val) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform1i(varLocation, val);
    }

    public void uploadTexture(String varName, int slot) {
        int varLocation = glGetUniformLocation(shaderProgramID, varName);
        use();
        glUniform1i(varLocation, slot);
    }
}
