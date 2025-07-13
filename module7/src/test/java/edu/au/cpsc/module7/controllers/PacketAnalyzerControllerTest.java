package edu.au.cpsc.module7.controllers;

import edu.au.cpsc.module7.models.CapturedPacket;
import edu.au.cpsc.module7.services.TcpdumpPacketCaptureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javafx.collections.ObservableList;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PacketAnalyzerControllerTest {

    @Mock
    private TcpdumpPacketCaptureService mockCaptureService;

    private PacketAnalyzerController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new PacketAnalyzerController(mockCaptureService);
        
        // The 'allPackets' list is private, so we need to use reflection to set it for our test.
        Field allPacketsField = PacketAnalyzerController.class.getDeclaredField("allPackets");
        allPacketsField.setAccessible(true);
        allPacketsField.set(controller, javafx.collections.FXCollections.observableArrayList());
    }

    @Test
    void testCalculateProtocolStatistics() throws Exception {
        // Get the private 'allPackets' list
        Field allPacketsField = PacketAnalyzerController.class.getDeclaredField("allPackets");
        allPacketsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ObservableList<CapturedPacket> allPackets = (ObservableList<CapturedPacket>) allPacketsField.get(controller);

        // Add some mock packets
        allPackets.addAll(
            createMockPacket("TCP"),
            createMockPacket("UDP"),
            createMockPacket("TCP"),
            createMockPacket("ARP"),
            createMockPacket("TCP")
        );

        // Use reflection to invoke the private method
        Method method = PacketAnalyzerController.class.getDeclaredMethod("calculateProtocolStatistics");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Long> stats = (Map<String, Long>) method.invoke(controller);

        // Verify the statistics
        assertEquals(3, stats.get("TCP"));
        assertEquals(1, stats.get("UDP"));
        assertEquals(1, stats.get("ARP"));
        assertNull(stats.get("ICMP")); // Should not be present
    }

    private CapturedPacket createMockPacket(String protocol) {
        return new CapturedPacket(1, LocalDateTime.now(), "src", "dst", 1, 1, protocol, 64, new byte[0], "info");
    }
} 