package com.example.flightstats;

import org.junit.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class AICoreReflectionTest {
    @Test
    public void dumpMethods() {
        try {
            Class<?> callbackClass = Class.forName("com.google.ai.edge.aicore.DownloadCallback");
            System.out.println("Dumping DownloadCallback:");
            for (Method m : callbackClass.getDeclaredMethods()) {
                System.out.print("Method: " + m.getName() + "(");
                Class<?>[] pts = m.getParameterTypes();
                for(int i=0; i<pts.length; i++) {
                    System.out.print(pts[i].getName() + (i<pts.length-1 ? ", " : ""));
                }
                System.out.println(")");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("DownloadCallback class not found in classpath.");
        }
        
        try {
            Class<?> configClass = Class.forName("com.google.ai.edge.aicore.DownloadConfig");
            System.out.println("Dumping DownloadConfig Constructors:");
            for (Constructor<?> c : configClass.getConstructors()) {
                System.out.print("Constructor: DownloadConfig(");
                Class<?>[] pts = c.getParameterTypes();
                for(int i=0; i<pts.length; i++) {
                    System.out.print(pts[i].getName() + (i<pts.length-1 ? ", " : ""));
                }
                System.out.println(")");
            }
        } catch (ClassNotFoundException e) {
            System.out.println("DownloadConfig class not found in classpath.");
        }
    }
}
