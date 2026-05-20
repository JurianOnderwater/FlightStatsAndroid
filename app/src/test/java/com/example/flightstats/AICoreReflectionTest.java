package com.example.flightstats;

import org.junit.Test;
import com.google.ai.edge.aicore.DownloadCallback;
import com.google.ai.edge.aicore.DownloadConfig;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

public class AICoreReflectionTest {
    @Test
    public void dumpMethods() {
        System.out.println("Dumping DownloadCallback:");
        for (Method m : DownloadCallback.class.getDeclaredMethods()) {
            System.out.print("Method: " + m.getName() + "(");
            Class<?>[] pts = m.getParameterTypes();
            for(int i=0; i<pts.length; i++) {
                System.out.print(pts[i].getName() + (i<pts.length-1 ? ", " : ""));
            }
            System.out.println(")");
        }
        
        System.out.println("Dumping DownloadConfig Constructors:");
        for (Constructor c : DownloadConfig.class.getConstructors()) {
            System.out.print("Constructor: DownloadConfig(");
            Class<?>[] pts = c.getParameterTypes();
            for(int i=0; i<pts.length; i++) {
                System.out.print(pts[i].getName() + (i<pts.length-1 ? ", " : ""));
            }
            System.out.println(")");
        }
    }
}
