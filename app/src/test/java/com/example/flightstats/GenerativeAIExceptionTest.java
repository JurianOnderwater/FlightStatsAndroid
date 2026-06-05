package com.example.flightstats;

import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class GenerativeAIExceptionTest {
    @Test
    public void dumpFeature() {
        try {
            Class<?> clazz = Class.forName("com.google.ai.edge.aicore.GenerativeAIException");
            System.out.println("Dumping GenerativeAIException Fields:");
            for (Field f : clazz.getDeclaredFields()) {
                System.out.println("Field: " + f.getName() + " Type: " + f.getType().getName());
            }
            System.out.println("Dumping GenerativeAIException Methods:");
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println("Method: " + m.getName() + " ReturnType: " + m.getReturnType().getName());
            }
        } catch (ClassNotFoundException e) {
            System.out.println("GenerativeAIException class not found in classpath.");
        }
    }
}
