package critter.test;

import java.util.Objects;

/** Assertions for the test runner. */
public final class Check {
    private Check() {
    }

    public static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("expected <" + expected + "> but got <" + actual + ">");
    }

    public static void eq(String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but got <" + actual + ">");
        }
    }

    public static void near(double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError("expected " + expected + " ± " + tolerance + " but got " + actual);
    }

    public static void yes(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    public static void no(boolean cond, String what) {
        if (cond) throw new AssertionError(what);
    }

    public static <T extends Throwable> T throwsA(Class<T> type, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
        }
        throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
    }
}
