package com.example.flightstats;

import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import com.google.ai.edge.aicore.GenerativeAIException;

public class GenerativeAIExceptionTest {
    @Test
    public void dumpFeature() {
        System.out.println("Dumping GenerativeAIException Fields:");
        for (Field f : GenerativeAIException.class.getDeclaredFields()) {
            System.out.println("Field: " + f.getName() + " Type: " + f.getType().getName());
        }
        System.out.println("Dumping GenerativeAIException Methods:");
        for (Method m : GenerativeAIException.class.getDeclaredMethods()) {
            System.out.println("Method: " + m.getName() + " ReturnType: " + m.getReturnType().getName());
        }
    }
}
