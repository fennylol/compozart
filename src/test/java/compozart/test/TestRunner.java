package compozart.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Runs every public no-argument method whose name starts with "test" on each named class.
 * Exits with status 1 if anything fails.
 */
public final class TestRunner {
    public static void main(String[] args) throws Exception {
        int passed = 0, failed = 0;
        for (String name : args) {
            Class<?> cls = Class.forName(name);
            Method[] methods = cls.getMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            for (Method m : methods) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
                String label = cls.getSimpleName() + "." + m.getName();
                try {
                    m.invoke(cls.getDeclaredConstructor().newInstance());
                    passed++;
                } catch (InvocationTargetException e) {
                    failed++;
                    Throwable t = e.getCause();
                    System.out.println("FAIL " + label + ": " + t);
                    for (StackTraceElement el : t.getStackTrace()) {
                        if (el.getClassName().startsWith("compozart.") && !el.getClassName().startsWith("compozart.test.Check")) {
                            System.out.println("     at " + el);
                        }
                        if (el.getClassName().equals(cls.getName())) break;
                    }
                }
            }
        }
        System.out.printf("%d passed, %d failed%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
