package edu.au.cpsc.module7.di;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import edu.au.cpsc.module7.services.*;
import javafx.fxml.FXMLLoader;
import com.google.inject.Injector;
import com.google.inject.Provider;
import javax.inject.Inject;

/**
 * Guice module for application-level bindings.
 */
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        // Bind services as singletons
        bind(SettingsService.class).in(Singleton.class);
        bind(SystemToolsManager.class).in(Singleton.class);
        bind(DNSQueryService.class).in(Singleton.class);
        bind(NetworkScannerService.class).in(Singleton.class);
        bind(TcpdumpPacketCaptureService.class).in(Singleton.class);
        bind(PacketCaptureService.class).in(Singleton.class);
        bind(ARPScanner.class).in(Singleton.class);
        bind(NetworkVisualizationService.class).in(Singleton.class);
        bind(ProtocolDissectorService.class).in(Singleton.class);
        
        // AWS Firewall Tester services
        bind(FirewallPayloadGenerator.class).in(Singleton.class);
        bind(AwsFirewallTestingService.class).in(Singleton.class);
        
        // Route 53 testing services
        bind(Route53ResolverTestingService.class).in(Singleton.class);
        bind(Route53RoutingPolicyTestingService.class).in(Singleton.class);
        
        // Tor proxy service for geographic IP diversity
        bind(TorProxyService.class).in(Singleton.class);
        
        // Subnet Helper service
        bind(SubnetHelperService.class).in(Singleton.class);
        
        // Bind FXMLLoader
        bind(FXMLLoader.class).toProvider(new Provider<FXMLLoader>() {
            @Inject
            private Injector injector;

            @Override
            public FXMLLoader get() {
                FXMLLoader loader = new FXMLLoader();
                loader.setControllerFactory(injector::getInstance);
                return loader;
            }
        });
    }
} 