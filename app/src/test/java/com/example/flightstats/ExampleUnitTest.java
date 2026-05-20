package com.example.flightstats;

import org.junit.Test;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExampleUnitTest {
    @Test
    public void listAllClasses() {
        try (PrintWriter out = new PrintWriter(new FileWriter("/Users/jurian/Documents/Personal/Code/FlightStatsAndroid/class_list.txt"))) {
            Class<?> clazz = Class.forName("com.google.ai.edge.aicore.GenerativeModel");
            ProtectionDomain pd = clazz.getProtectionDomain();
            CodeSource cs = pd.getCodeSource();
            if (cs != null) {
                URL url = cs.getLocation();
                out.println("Jar location: " + url);
                try (ZipInputStream zip = new ZipInputStream(url.openStream())) {
                    for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                            String className = entry.getName().replace('/', '.');
                            className = className.substring(0, className.length() - ".class".length());
                            out.println(className);
                        }
                    }
                }
            } else {
                out.println("CodeSource is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}