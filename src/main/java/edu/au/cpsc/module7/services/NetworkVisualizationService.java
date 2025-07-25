package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.NetworkHost;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;

import java.util.List;
import java.util.Random;

/**
 * Service for generating network visualization data and HTML
 */
public class NetworkVisualizationService {
    private final ObjectMapper objectMapper;
    private final Random random;
    
    @Inject
    public NetworkVisualizationService() {
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }
    
    /**
     * Generates HTML content for network visualization using D3.js
     */
    public String generateNetworkMapHTML(List<NetworkHost> hosts, String layout) {
        try {
            // Calculate positions for hosts based on selected layout
            if ("Force".equals(layout)) {
                // Positions will be calculated by the D3 simulation, but we can set initial positions
                initializeHostPositions(hosts);
            } else {
                // Default to Ring layout
                calculateRingHostPositions(hosts);
            }
            
            // Generate JSON data for visualization
            String jsonData = generateVisualizationData(hosts);
            
            return generateHTML(jsonData, layout);
            
        } catch (Exception e) {
            return generateErrorHTML("Error generating network map: " + e.getMessage());
        }
    }
    
    private void initializeHostPositions(List<NetworkHost> hosts) {
        if (hosts.isEmpty()) return;

        double centerX = 400;
        double centerY = 300;

        for (NetworkHost host : hosts) {
            // Start all nodes near the center for the force simulation
            host.setX(centerX + (random.nextDouble() - 0.5) * 50);
            host.setY(centerY + (random.nextDouble() - 0.5) * 50);
        }
    }

    private void calculateRingHostPositions(List<NetworkHost> hosts) {
        if (hosts.isEmpty()) {
            return;
        }

        double centerX = 400;
        double centerY = 300;
        int hostsInRing = 30; // Increased from 20
        double ringSpacing = 45; // Increased from 40
        double startRadius = 60; // Increased from 50

        for (int i = 0; i < hosts.size(); i++) {
            int ringIndex = i / hostsInRing;
            int hostIndexInRing = i % hostsInRing;

            double currentRadius = startRadius + (ringIndex * ringSpacing);
            // Distribute hosts evenly on the current ring
            double angle = 2 * Math.PI * hostIndexInRing / hostsInRing;

            double x = centerX + currentRadius * Math.cos(angle);
            double y = centerY + currentRadius * Math.sin(angle);

            NetworkHost host = hosts.get(i);
            host.setX(x);
            host.setY(y);
        }
    }
    
    private String generateVisualizationData(List<NetworkHost> hosts) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode nodesArray = objectMapper.createArrayNode();
        ArrayNode linksArray = objectMapper.createArrayNode();
        
        // Add gateway/router node (assuming first host or create virtual one)
        ObjectNode gatewayNode = objectMapper.createObjectNode();
        gatewayNode.put("id", "gateway");
        gatewayNode.put("name", "Gateway/Router");
        gatewayNode.put("type", "gateway");
        gatewayNode.put("x", 400);
        gatewayNode.put("y", 300);
        gatewayNode.put("status", "online");
        gatewayNode.put("icon", "🌐");
        nodesArray.add(gatewayNode);
        
        // Add discovered hosts
        for (NetworkHost host : hosts) {
            ObjectNode hostNode = objectMapper.createObjectNode();
            hostNode.put("id", host.getIpAddress());
            hostNode.put("name", host.getDisplayName());
            hostNode.put("ip", host.getIpAddress());
            hostNode.put("hostname", host.getHostname() != null ? host.getHostname() : "");
            hostNode.put("type", "host");
            hostNode.put("x", host.getX());
            hostNode.put("y", host.getY());
            hostNode.put("status", host.isAlive() ? "online" : "offline");
            hostNode.put("responseTime", host.getResponseTime());
            hostNode.put("openPorts", host.getOpenPorts().size());
            hostNode.put("os", host.getOsGuess() != null ? host.getOsGuess() : "Unknown");
            hostNode.put("icon", getHostIcon(host));
            
            // Add services info
            ArrayNode servicesArray = objectMapper.createArrayNode();
            for (String service : host.getServices()) {
                servicesArray.add(service);
            }
            hostNode.set("services", servicesArray);
            
            // Add ports info
            ArrayNode portsArray = objectMapper.createArrayNode();
            for (int port : host.getOpenPorts()) {
                portsArray.add(port);
            }
            hostNode.set("ports", portsArray);
            
            nodesArray.add(hostNode);
            
            // Create link from gateway to host
            ObjectNode link = objectMapper.createObjectNode();
            link.put("source", "gateway");
            link.put("target", host.getIpAddress());
            link.put("type", "network");
            linksArray.add(link);
        }
        
        root.set("nodes", nodesArray);
        root.set("links", linksArray);
        
        return objectMapper.writeValueAsString(root);
    }
    
    private String getHostIcon(NetworkHost host) {
        if (!host.isAlive()) return "❌";
        
        String os = host.getOsGuess();
        if (os != null) {
            switch (os.toLowerCase()) {
                case "windows": return "🖥️";
                case "linux": case "unix": return "🐧";
                case "macos": return "🍎";
                default: return "💻";
            }
        }
        
        // Determine by services
        if (host.getServices().contains("HTTP") || host.getServices().contains("HTTPS")) {
            return "🌐";
        } else if (host.getServices().contains("SSH")) {
            return "🔧";
        } else if (host.getServices().contains("FTP")) {
            return "📁";
        } else if (host.getServices().contains("MySQL") || host.getServices().contains("PostgreSQL")) {
            return "🗄️";
        }
        
        return "💻";
    }
    
    private String generateHTML(String jsonData, String layout) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"utf-8\">\n");
        html.append("    <title>Network Map</title>\n");
        html.append("    <script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { margin: 0; font-family: Arial, sans-serif; background: #f0f0f0; }\n");
        html.append("        #network-map { width: 100%; height: 100vh; background: white; }\n");
        html.append("        .node { cursor: pointer; }\n");
        html.append("        .node-gateway { fill: #ff6b6b; stroke: #e63946; stroke-width: 3px; }\n");
        html.append("        .node-host-online { fill: #51cf66; stroke: #37b24d; stroke-width: 2px; }\n");
        html.append("        .node-host-offline { fill: #868e96; stroke: #495057; stroke-width: 2px; }\n");
        html.append("        .node-highlighted { stroke: #007bff; stroke-width: 4px; }\n");
        html.append("        .link { stroke: #999; stroke-width: 2px; stroke-dasharray: 5,5; }\n");
        html.append("        .link-active { stroke: #51cf66; stroke-width: 3px; }\n");
        html.append("        .node-label { font-size: 12px; font-weight: bold; text-anchor: middle; }\n");
        html.append("        .node-info { font-size: 10px; text-anchor: middle; fill: #666; }\n");
        html.append("        .tooltip {\n");
        html.append("            position: absolute;\n");
        html.append("            background: rgba(0, 0, 0, 0.8);\n");
        html.append("            color: white;\n");
        html.append("            padding: 10px;\n");
        html.append("            border-radius: 5px;\n");
        html.append("            font-size: 12px;\n");
        html.append("            pointer-events: none;\n");
        html.append("            z-index: 1000;\n");
        html.append("            max-width: 250px;\n");
        html.append("        }\n");
        html.append("        .legend {\n");
        html.append("            position: absolute;\n");
        html.append("            top: 10px;\n");
        html.append("            right: 10px;\n");
        html.append("            background: white;\n");
        html.append("            padding: 15px;\n");
        html.append("            border-radius: 5px;\n");
        html.append("            box-shadow: 0 2px 10px rgba(0,0,0,0.1);\n");
        html.append("        }\n");
        html.append("        .legend-item {\n");
        html.append("            display: flex;\n");
        html.append("            align-items: center;\n");
        html.append("            margin: 5px 0;\n");
        html.append("        }\n");
        html.append("        .legend-color {\n");
        html.append("            width: 15px;\n");
        html.append("            height: 15px;\n");
        html.append("            border-radius: 50%;\n");
        html.append("            margin-right: 8px;\n");
        html.append("        }\n");
        html.append("        .stats {\n");
        html.append("            position: absolute;\n");
        html.append("            top: 10px;\n");
        html.append("            left: 10px;\n");
        html.append("            background: white;\n");
        html.append("            padding: 15px;\n");
        html.append("            border-radius: 5px;\n");
        html.append("            box-shadow: 0 2px 10px rgba(0,0,0,0.1);\n");
        html.append("        }\n");
        html.append("        .controls {\n");
        html.append("            position: absolute;\n");
        html.append("            bottom: 10px;\n");
        html.append("            left: 10px;\n");
        html.append("            background: rgba(0,0,0,0.8);\n");
        html.append("            color: white;\n");
        html.append("            padding: 10px;\n");
        html.append("            border-radius: 5px;\n");
        html.append("            font-size: 11px;\n");
        html.append("        }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div id=\"network-map\"></div>\n");
        html.append("    <div class=\"stats\">\n");
        html.append("        <h3>Network Statistics</h3>\n");
        html.append("        <div id=\"stats-content\"></div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"legend\">\n");
        html.append("        <h3>Legend</h3>\n");
        html.append("        <div class=\"legend-item\">\n");
        html.append("            <div class=\"legend-color\" style=\"background: #ff6b6b;\"></div>\n");
        html.append("            <span>Gateway/Router</span>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"legend-item\">\n");
        html.append("            <div class=\"legend-color\" style=\"background: #51cf66;\"></div>\n");
        html.append("            <span>Online Host</span>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"legend-item\">\n");
        html.append("            <div class=\"legend-color\" style=\"background: #868e96;\"></div>\n");
        html.append("            <span>Offline Host</span>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"controls\">\n");
        html.append("        <strong>🎮 Interactive Controls:</strong><br/>\n");
        html.append("        🖱️ <strong>Mouse:</strong> Scroll to zoom, drag to pan<br/>\n");
        html.append("        🖱️ <strong>Click:</strong> Select host (highlights in table)<br/>\n");
        html.append("        🖱️ <strong>Hover:</strong> View host details<br/>\n");
        html.append("        🖱️ <strong>Double-click:</strong> Reset zoom<br/>\n");
        html.append("        ⌨️ <strong>Keyboard:</strong> Press 'R' to reset view<br/>\n");
        html.append("        🔄 <strong>Drag nodes:</strong> Rearrange network (Force layout)\n");
        html.append("    </div>\n");
        html.append("    <div class=\"tooltip\" id=\"tooltip\" style=\"display: none;\"></div>\n");
        html.append("    <script>\n");
        html.append("        const data = ").append(jsonData).append(";\n");
        html.append("        const width = window.innerWidth;\n");
        html.append("        const height = window.innerHeight;\n");
        html.append("        const svg = d3.select(\"#network-map\")\n");
        html.append("            .append(\"svg\")\n");
        html.append("            .attr(\"width\", width)\n");
        html.append("            .attr(\"height\", height);\n");

        html.append("        // Add zoom and pan functionality\n");
        html.append("        const zoom = d3.zoom()\n");
        html.append("            .scaleExtent([0.1, 5])\n");
        html.append("            .on(\"zoom\", (event) => {\n");
        html.append("                g.attr(\"transform\", event.transform);\n");
        html.append("            });\n\n");
        html.append("        svg.call(zoom);\n\n");
        html.append("        const g = svg.append(\"g\"); // Container for zoomable elements\n\n");
        html.append("        const tooltip = d3.select(\"#tooltip\");\n");
        
        html.append("        const links = g.selectAll(\".link\")\n");
        html.append("            .data(data.links)\n");
        html.append("            .enter().append(\"line\")\n");
        html.append("            .attr(\"class\", \"link\");\n");

        html.append("        const nodeGroups = g.selectAll(\".node\")\n");
        html.append("            .data(data.nodes)\n");
        html.append("            .enter().append(\"g\")\n");
        html.append("            .attr(\"class\", \"node\")\n");
        html.append("            .style(\"cursor\", \"pointer\")\n");
        html.append("            .on(\"click\", function(event, d) {\n");
        html.append("                event.stopPropagation();\n");
        html.append("                if (d.type === 'host' && typeof window.javaApp !== 'undefined') {\n");
        html.append("                    window.javaApp.onHostClicked(d.ip);\n");
        html.append("                }\n");
        html.append("            })\n");
        html.append("            .on(\"mouseover\", function(event, d) {\n");
        html.append("                let tooltipContent = `<strong>${d.name}</strong><br/>`;\n");
        html.append("                if (d.type === \"host\") {\n");
        html.append("                    tooltipContent += `IP: ${d.ip}<br/>`;\n");
        html.append("                    if (d.hostname) tooltipContent += `Hostname: ${d.hostname}<br/>`;\n");
        html.append("                    tooltipContent += `Status: ${d.status}<br/>`;\n");
        html.append("                    if (d.responseTime > 0) tooltipContent += `Response Time: ${d.responseTime}ms<br/>`;\n");
        html.append("                    tooltipContent += `Open Ports: ${d.openPorts}<br/>`;\n");
        html.append("                    if (d.os !== \"Unknown\") tooltipContent += `OS: ${d.os}<br/>`;\n");
        html.append("                    if (d.services.length > 0) {\n");
        html.append("                        tooltipContent += `Services: ${d.services.join(\", \")}<br/>`;\n");
        html.append("                    }\n");
        html.append("                    if (d.ports.length > 0) {\n");
        html.append("                        tooltipContent += `Ports: ${d.ports.join(\", \")}`;\n");
        html.append("                    }\n");
        html.append("                } else {\n");
        html.append("                    tooltipContent += \"Network Gateway\";\n");
        html.append("                }\n");
        html.append("                tooltip.html(tooltipContent)\n");
        html.append("                    .style(\"display\", \"block\")\n");
        html.append("                    .style(\"left\", (event.pageX + 10) + \"px\")\n");
        html.append("                    .style(\"top\", (event.pageY + 10) + \"px\");\n");
        html.append("            })\n");
        html.append("            .on(\"mouseout\", function() {\n");
        html.append("                tooltip.style(\"display\", \"none\");\n");
        html.append("            });\n");

        html.append("        nodeGroups.append(\"circle\")\n");
        html.append("            .attr(\"r\", d => d.type === \"gateway\" ? 20 : 12)\n");
        html.append("            .attr(\"class\", d => {\n");
        html.append("                if (d.type === \"gateway\") return \"node-gateway\";\n");
        html.append("                return d.status === \"online\" ? \"node-host-online\" : \"node-host-offline\";\n");
        html.append("            });\n");

        html.append("        nodeGroups.append(\"text\")\n");
        html.append("            .attr(\"class\", \"node-label\")\n");
        html.append("            .attr(\"dy\", \"0.35em\")\n");
        html.append("            .style(\"font-size\", \"12px\")\n");
        html.append("            .text(d => d.icon);\n");

        html.append("        if ('").append(layout).append("' === 'Force') {\n");
        html.append("            const simulation = d3.forceSimulation(data.nodes)\n");
        html.append("                .force(\"link\", d3.forceLink(data.links).id(d => d.id).distance(70))\n");
        html.append("                .force(\"charge\", d3.forceManyBody().strength(-300))\n");
        html.append("                .force(\"center\", d3.forceCenter(width / 2, height / 2))\n");
        html.append("                .force(\"collision\", d3.forceCollide().radius(25));\n");

        html.append("            // Make nodes draggable\n");
        html.append("            nodeGroups.call(d3.drag()\n");
        html.append("                .on(\"start\", (event, d) => {\n");
        html.append("                    if (!event.active) simulation.alphaTarget(0.3).restart();\n");
        html.append("                    d.fx = d.x;\n");
        html.append("                    d.fy = d.y;\n");
        html.append("                })\n");
        html.append("                .on(\"drag\", (event, d) => {\n");
        html.append("                    d.fx = event.x;\n");
        html.append("                    d.fy = event.y;\n");
        html.append("                })\n");
        html.append("                .on(\"end\", (event, d) => {\n");
        html.append("                    if (!event.active) simulation.alphaTarget(0);\n");
        html.append("                    d.fx = null;\n");
        html.append("                    d.fy = null;\n");
        html.append("                }));\n");

        html.append("            simulation.on(\"tick\", () => {\n");
        html.append("                links\n");
        html.append("                    .attr(\"x1\", d => d.source.x)\n");
        html.append("                    .attr(\"y1\", d => d.source.y)\n");
        html.append("                    .attr(\"x2\", d => d.target.x)\n");
        html.append("                    .attr(\"y2\", d => d.target.y);\n");

        html.append("                nodeGroups\n");
        html.append("                    .attr(\"transform\", d => `translate(${d.x}, ${d.y})`);\n");
        html.append("            });\n");
        html.append("        } else {\n");
        html.append("            // Ring layout: positions are pre-calculated\n");
        html.append("             links\n");
        html.append("                .attr(\"x1\", d => data.nodes.find(n => n.id === d.source).x)\n");
        html.append("                .attr(\"y1\", d => data.nodes.find(n => n.id === d.source).y)\n");
        html.append("                .attr(\"x2\", d => data.nodes.find(n => n.id === d.target).x)\n");
        html.append("                .attr(\"y2\", d => data.nodes.find(n => n.id === d.target).y);\n");
        html.append("             nodeGroups.attr(\"transform\", d => `translate(${d.x},${d.y})`);\n");
        html.append("        }\n");

        html.append("        const onlineHosts = data.nodes.filter(n => n.type === \"host\" && n.status === \"online\").length;\n");
        html.append("        const totalHosts = data.nodes.filter(n => n.type === \"host\").length;\n");
        html.append("        const totalPorts = data.nodes.reduce((sum, n) => sum + (n.openPorts || 0), 0);\n");
        html.append("        document.getElementById(\"stats-content\").innerHTML = `\n");
        html.append("            <div>Total Hosts: ${totalHosts}</div>\n");
        html.append("            <div>Online Hosts: ${onlineHosts}</div>\n");
        html.append("            <div>Offline Hosts: ${totalHosts - onlineHosts}</div>\n");
        html.append("            <div>Total Open Ports: ${totalPorts}</div>\n");
        html.append("        `;\n");
        html.append("\n");
        html.append("        function highlightNode(ipAddress) {\n");
        html.append("            nodeGroups.selectAll('circle').classed('node-highlighted', false);\n");
        html.append("            if (ipAddress) {\n");
        html.append("                nodeGroups.filter(d => d.ip === ipAddress)\n");
        html.append("                    .select('circle')\n");
        html.append("                    .classed('node-highlighted', true);\n");
        html.append("            }\n");
        html.append("        }\n\n");
        html.append("        // Add double-click to reset zoom\n");
        html.append("        svg.on(\"dblclick.zoom\", () => {\n");
        html.append("            svg.transition().duration(750).call(\n");
        html.append("                zoom.transform,\n");
        html.append("                d3.zoomIdentity\n");
        html.append("            );\n");
        html.append("        });\n\n");
        html.append("        // Add keyboard shortcuts\n");
        html.append("        d3.select(\"body\").on(\"keydown\", (event) => {\n");
        html.append("            if (event.key === 'r' || event.key === 'R') {\n");
        html.append("                svg.transition().duration(750).call(\n");
        html.append("                    zoom.transform,\n");
        html.append("                    d3.zoomIdentity\n");
        html.append("                );\n");
        html.append("            }\n");
        html.append("        });\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String generateErrorHTML(String errorMessage) {
        return "<html><body><h2>Network Map Error</h2><p>" + errorMessage + "</p></body></html>";
    }
} 