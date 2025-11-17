package com.jecs.util;

public class Time {
    /**
     * Application Start time
     */
    public static float timeStarted = System.nanoTime();

    /**
     *
     * @return Time elapsed since application started in nanoseconds
     */
    public static float getTime() { return (float) ((System.nanoTime() - timeStarted) * 1e-9);}
}
