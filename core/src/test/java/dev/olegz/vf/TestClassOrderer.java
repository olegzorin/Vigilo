package dev.olegz.vf;

import java.util.Collections;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

public class TestClassOrderer implements ClassOrderer {
    // Tests that must run first (e.g. ones that create table partitions or a
    // shared test account). Add entries as DB-backed tests are introduced.
    private static final Class<?>[] priorityTests = {};

    @Override
    public void orderClasses(ClassOrdererContext context) {
        var classDescriptors = context.getClassDescriptors();
        int size = classDescriptors.size();
        if (size < 2) return;

        for (int i = 0; i < priorityTests.length; i++) {
            for (int j = i; j < size; j++) {
                if (priorityTests[i].equals(classDescriptors.get(j).getTestClass())) {
                    if (j > i) Collections.swap(classDescriptors, j, i);
                    break;
                }
            }
        }
    }
}
