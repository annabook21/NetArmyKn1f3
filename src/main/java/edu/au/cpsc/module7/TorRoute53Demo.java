package edu.au.cpsc.module7;

import edu.au.cpsc.module7.services.TorProxyService;
import edu.au.cpsc.module7.services.Route53RoutingPolicyTestingService;
import edu.au.cpsc.module7.models.Route53RoutingPolicyTest;

/**
 * Demo showing how Tor helps with Route 53 geographic testing
 */
public class TorRoute53Demo {
    
    public static void main(String[] args) {
        System.out.println("=== Tor/Orchid Route 53 Geographic Testing Demo ===\n");
        
        // Create services
        TorProxyService torService = new TorProxyService();
        Route53RoutingPolicyTestingService route53Service = new Route53RoutingPolicyTestingService(torService);
        
        // Check if Tor is available
        if (!torService.isTorAvailable()) {
            System.out.println("❌ Tor is not available!");
            System.out.println("\n" + torService.getTorSetupInstructions());
            return;
        }
        
        System.out.println("✅ Tor is available and running!");
        
        // Demo 1: Show current exit node
        System.out.println("\n=== Demo 1: Current Exit Node ===");
        torService.getCurrentExitNodeIP().thenAccept(ip -> {
            System.out.println("Current exit node IP: " + ip);
        }).join();
        
        // Demo 2: Test from multiple locations
        System.out.println("\n=== Demo 2: Testing from Multiple Geographic Locations ===");
        try {
            Route53RoutingPolicyTest test = route53Service.testGeolocationRoutingWithTor("example.com", 3).get();
            System.out.println("Test Result: " + test.getResult());
            System.out.println("Details:\n" + test.getErrorMessage());
        } catch (Exception e) {
            System.out.println("Test failed: " + e.getMessage());
        }
        
        // Demo 3: Show how Tor helps with different routing policies
        System.out.println("\n=== Demo 3: How Tor Helps with Route 53 Routing Policies ===");
        
        System.out.println("🌍 GEOLOCATION ROUTING:");
        System.out.println("  - Without Tor: Only test from your current location");
        System.out.println("  - With Tor: Test from multiple countries automatically");
        System.out.println("  - Tor exit nodes in US, Europe, Asia provide geographic diversity");
        
        System.out.println("\n⚡ LATENCY-BASED ROUTING:");
        System.out.println("  - Without Tor: Only measure latency from your location");
        System.out.println("  - With Tor: Measure latency from different global locations");
        System.out.println("  - Verify Route 53 routes to lowest latency endpoint per region");
        
        System.out.println("\n🔄 FAILOVER ROUTING:");
        System.out.println("  - Without Tor: Limited to testing from single location");
        System.out.println("  - With Tor: Test failover behavior across different regions");
        System.out.println("  - Verify primary/secondary endpoints work globally");
        
        System.out.println("\n⚖️ WEIGHTED ROUTING:");
        System.out.println("  - Without Tor: Weight distribution only from your location");
        System.out.println("  - With Tor: Test weight distribution from multiple locations");
        System.out.println("  - Verify consistent weight distribution globally");
        
        // Demo 4: Show practical testing workflow
        System.out.println("\n=== Demo 4: Practical Testing Workflow ===");
        System.out.println("1. Start Tor: `tor --ControlPort 9051`");
        System.out.println("2. NetArmyKn1f3 detects Tor automatically");
        System.out.println("3. Select Route 53 testing with geographic diversity");
        System.out.println("4. Application tests from multiple exit nodes automatically");
        System.out.println("5. Results show how routing policies work globally");
        
        // Cleanup
        torService.shutdown();
        System.out.println("\n✅ Demo completed!");
    }
} 