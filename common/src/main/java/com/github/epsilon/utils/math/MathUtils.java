package com.github.epsilon.utils.math;

import java.util.concurrent.ThreadLocalRandom;

public class MathUtils {

    private MathUtils() {
    }

    public static int getRandom(int min, int max) {
        return min >= max ? min : ThreadLocalRandom.current().nextInt() * (max - min) + min;
    }

    public static float getRandom(float min, float max) {
        return min >= max ? min : ThreadLocalRandom.current().nextFloat() * (max - min) + min;
    }

    public static double getRandom(double min, double max) {
        return min >= max ? min : ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

}
