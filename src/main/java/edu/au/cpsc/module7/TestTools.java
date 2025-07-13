package edu.au.cpsc.module7;

import com.google.inject.Guice;
import com.google.inject.Injector;
import edu.au.cpsc.module7.di.AppModule;
import edu.au.cpsc.module7.services.SystemToolsManager;

import java.util.List;
import java.util.Map;

public class TestTools {
    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new AppModule());
        SystemToolsManager toolsManager = injector.getInstance(SystemToolsManager.class);
        
        System.out.println("=== Tool Availability Check ===");
        Map<String, Boolean> availability = toolsManager.checkToolAvailability();
        for (Map.Entry<String, Boolean> entry : availability.entrySet()) {
            System.out.println(entry.getKey() + ": " + (entry.getValue() ? "✓ Available" : "✗ Missing"));
        }
        
        System.out.println("\n=== Missing Tools ===");
        List<String> missing = toolsManager.getMissingTools();
        if (missing.isEmpty()) {
            System.out.println("No missing tools detected");
        } else {
            for (String tool : missing) {
                System.out.println("- " + tool);
            }
        }
        
        System.out.println("\n=== hping3 Alternative Check ===");
        System.out.println("Has hping3 alternative: " + toolsManager.hasHping3Alternative());
    }
} 