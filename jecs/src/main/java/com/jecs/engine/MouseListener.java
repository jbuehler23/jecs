package com.jecs.engine;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

public class MouseListener {

    private static MouseListener instance;
    private double scrollX, scrollY;
    private double xPos, yPos, lastY, lastX, worldX, worldY, lastWorldX, lastWorldY;
    private Vector2f gameViewportPos = new Vector2f();
    private Vector2f gameViewportSize = new Vector2f();

    /**
     * Only supporting 3 mouse buttons for now
     */
    private boolean mouseButtonPressed[] = new boolean[3];
    private boolean isDragging;

    private int mouseButtonDown = 0;

    private MouseListener() {
        this.scrollX = 0.0;
        this.scrollY = 0.0;
        this.yPos = 0.0;
        this.xPos = 0.0;
        this.lastX = 0.0;
        this.lastY = 0.0;
    }

    public static MouseListener get() {
        if (MouseListener.instance == null) {
            MouseListener.instance = new MouseListener();
        }
        return MouseListener.instance;
    }

    public static void mousePosCallback(long window, double xPos, double yPos) {
        if (get().mouseButtonDown > 0) {
            get().isDragging = true;
        }
        get().lastX = get().xPos;
        get().lastY = get().yPos;
        get().lastWorldX = get().worldX;
        get().lastWorldY = get().worldY;
        get().xPos = xPos;
        get().yPos = yPos;
        calcWorldX();
        calcWorldY();

    }


    public static void mouseButtonCallback(long window, int button, int action, int mods) {
        // Registers the mouse button callback with GLFW
        if (action == GLFW_PRESS) {
            get().mouseButtonDown++;
            if (button < get().mouseButtonPressed.length) {
                get().mouseButtonPressed[button] = true;
            }
        } else if (action == GLFW_RELEASE) {
            get().mouseButtonDown--;
            if (button < get().mouseButtonPressed.length) {
                get().mouseButtonPressed[button] = false;
                get().isDragging = false;
            }
        }
    }

    public static void mouseScrollCallback(long window, double xOffset, double yOffset) {
        get().scrollX = xOffset;
        get().scrollY = yOffset;
    }

    public static void endFrame() {
        get().scrollX = 0.0;
        get().scrollY = 0.0;
        get().lastY = get().yPos;
        get().lastX = get().xPos;
        get().lastWorldY = get().worldY;
        get().lastWorldX = get().worldX;
    }

    public static float getWorldDx() {
        return (float)(get().lastWorldX - get().worldX);
    }

    public static float getWorldDy() {
        return (float)(get().lastWorldY - get().worldY);
    }

    public static float getX() {
        return (float) get().xPos;
    }

    public static float getY() {
        return (float) get().yPos;
    }

    public static float getDx() {
        return (float)(get().lastX - get().xPos);
    }

    public static float getDy() {
        return (float)(get().lastY - get().yPos);
    }

    public static float getScrollX() {
        return (float) get().scrollX;
    }

    public static float getScrollY() {
        return (float) get().scrollY;
    }

    public static boolean isDragging() {
        return get().isDragging;
    }

    public static boolean mouseButtonDown(int button) {
        if (button < get().mouseButtonPressed.length) {
            return get().mouseButtonPressed[button];
        } else {
            return false;
        }

    }

    public static float getWorldX() {
        return (float) get().worldX;
    }

    public static float getWorldY() {
        return (float) get().worldY;
    }


//    public static float getWorldX() {
//        float currentX = getX();
//        //converts to 0-1 range first, then convert to -1, 1
//        currentX = (currentX / (float) Window.getWidth()) * 2.0f - 1.0f;
//        Vector4f tmp = new Vector4f(currentX, 0, 0 , 1);
//        tmp.mul(Window.getScene().camera().getInverseProjection()).mul(Window.getScene().camera().getInverseView());
//        currentX = tmp.x;
//
//        return currentX;
//    }
//
//    public static float getWorldY() {
//        //Y-Coords are flipped from projection
//        float currentY = Window.getHeight() - getY();
//        //converts to 0-1 range first, then convert to -1, 1
//        currentY = (currentY / (float) Window.getHeight()) * 2.0f - 1.0f;
//        Vector4f tmp = new Vector4f(0, currentY, 0 , 1);
//        tmp.mul(Window.getScene().camera().getInverseProjection()).mul(Window.getScene().camera().getInverseView());
//        currentY = tmp.y;
//
//        return currentY;
//    }

    public static void setGameViewportPos(Vector2f gameViewportPos) {
        get().gameViewportPos.set(gameViewportPos);
    }

    public static void setGameViewportSize(Vector2f gameViewportSize) {
        get().gameViewportSize.set(gameViewportSize);
    }

    public static float getScreenX() {
        float currentX = getX() - get().gameViewportPos.x;
        //converts to 0-1 range first, then convert to -1, 1
        currentX = (currentX / get().gameViewportSize.x) * 1920.0f;


        return currentX;
    }

    public static float getScreenY() {
        //Y-Coords are flipped from projection
        float currentY = getY() - get().gameViewportPos.y;
        //converts to 0-1 range first, then convert to -1, 1
        currentY = 1080.0f - ((currentY / get().gameViewportSize.y) * 1080.0f);

        return currentY;
    }

    public static boolean isMouseInsideViewport() {
        float x = getX();
        float y = getY();
        return x >= get().gameViewportPos.x
                && x <= get().gameViewportPos.x + get().gameViewportSize.x
                && y >= get().gameViewportPos.y
                && y <= get().gameViewportPos.y + get().gameViewportSize.y;
    }

    private static void calcWorldX() {
        float currentX = getX() - get().gameViewportPos.x;
        //converts to 0-1 range first, then convert to -1, 1
        currentX = (currentX / get().gameViewportSize.x) * 2.0f - 1.0f;
        Vector4f tmp = new Vector4f(currentX, 0, 0 , 1);

        Camera camera = Window.getScene().camera();
        Matrix4f viewProjection = new Matrix4f();
        camera.getInverseView().mul(camera.getInverseProjection(), viewProjection);
        tmp.mul(viewProjection);

        get().worldX = tmp.x;
    }

    private static void calcWorldY() {
        //Y-Coords are flipped from projection
        float currentY = getY() - get().gameViewportPos.y;
        //converts to 0-1 range first, then convert to -1, 1
        currentY = -((currentY / get().gameViewportSize.y) * 2.0f - 1.0f);
        Vector4f tmp = new Vector4f(0, currentY, 0 , 1);

        Camera camera = Window.getScene().camera();
        Matrix4f viewProjection = new Matrix4f();
        camera.getInverseView().mul(camera.getInverseProjection(), viewProjection);
        tmp.mul(viewProjection);

        get().worldY = tmp.y;
    }


}
