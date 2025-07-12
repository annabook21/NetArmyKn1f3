package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.QueryResult;
import javafx.concurrent.Task;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DNSQueryService extends Task<List<QueryResult>> {

    private static final Logger LOGGER = Logger.getLogger(DNSQueryService.class.getName());

    public enum QueryType {
        DIG,
        NSLOOKUP,
        WHOIS,
        HOST
    }

    private String domain;
    private List<QueryType> queryTypes;

    // Default constructor for Guice
    public DNSQueryService() {
    }

    public void configure(String domain, List<QueryType> queryTypes) {
        this.domain = domain;
        this.queryTypes = queryTypes;
    }

    @Override
    protected List<QueryResult> call() throws Exception {
        List<QueryResult> results = new ArrayList<>();
        for (QueryType queryType : queryTypes) {
            if (isCancelled()) {
                break;
            }
            results.add(executeQuery(queryType));
        }
        return results;
    }

    private QueryResult executeQuery(QueryType queryType) {
        String command = queryType.name().toLowerCase();
        ProcessBuilder processBuilder = new ProcessBuilder(command, domain);
        processBuilder.redirectErrorStream(true);

        StringBuilder output = new StringBuilder();
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing " + command, e);
            output.append("Error executing ").append(command).append(": ").append(e.getMessage());
        }
        return new QueryResult(command, output.toString());
    }
}