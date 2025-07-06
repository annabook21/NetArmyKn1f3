package edu.au.cpsc.module7.services;

import edu.au.cpsc.module7.models.QueryResult;
import edu.au.cpsc.module7.models.QueryType;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for executing DNS queries
 * Handles process execution and result parsing
 */
public class DNSQueryService {
    private static final Logger LOGGER = Logger.getLogger(DNSQueryService.class.getName());
    private static final int TIMEOUT_SECONDS = 30;

    /**
     * Executes a DNS query of the specified type for the given domain
     * @param domain The domain to query
     * @param queryType The type of query to execute
     * @return QueryResult containing the output and metadata
     * @throws Exception if the query fails
     */
    public QueryResult executeQuery(String domain, QueryType queryType) throws Exception {
        List<String> command = buildCommand(domain, queryType);

        long startTime = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Combine stdout and stderr

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            StringBuilder errorOutput = new StringBuilder();

            // Use separate threads for reading stdout and stderr to avoid blocking
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error reading process output", e);
                }
            });

            Thread errorReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Error reading process error output", e);
                }
            });

            outputReader.start();
            errorReader.start();

            // Wait for process to complete with timeout
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new Exception("Query timed out after " + TIMEOUT_SECONDS + " seconds");
            }

            // Wait for output threads to complete
            outputReader.join(5000);
            errorReader.join(5000);

            int exitCode = process.exitValue();
            long executionTime = System.currentTimeMillis() - startTime;

            String finalOutput = output.toString();
            String finalErrors = errorOutput.toString();

            // If there's no output but there are errors, use errors as output
            if (finalOutput.trim().isEmpty() && !finalErrors.trim().isEmpty()) {
                finalOutput = finalErrors;
            }

            // Create result
            QueryResult result = new QueryResult();
            result.setDomain(domain);
            result.setQueryType(queryType);
            result.setOutput(finalOutput);
            result.setExecutionTime(executionTime);
            result.setExitCode(exitCode);
            result.setSuccess(exitCode == 0 && !finalOutput.trim().isEmpty());

            if (!finalErrors.trim().isEmpty()) {
                result.setErrorOutput(finalErrors);
            }

            LOGGER.info(String.format("Query completed: %s for %s (exit code: %d, time: %dms)",
                    queryType, domain, exitCode, executionTime));

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Query was interrupted", e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing query", e);
            throw new Exception("Failed to execute " + queryType.name().toLowerCase() + " query: " + e.getMessage(), e);
        }
    }

    /**
     * Builds the command array for the specified query type
     * @param domain The domain to query
     * @param queryType The type of query
     * @return List of command components
     */
    private List<String> buildCommand(String domain, QueryType queryType) {
        List<String> command = new ArrayList<>();

        switch (queryType) {
            case DIG:
                command.add("dig");
                command.add(domain);
                command.add("+short");
                break;

            case NSLOOKUP:
                command.add("nslookup");
                command.add(domain);
                break;

            case WHOIS:
                command.add("whois");
                command.add(domain);
                break;

            case HOST:
                command.add("host");
                command.add(domain);
                break;

            default:
                throw new IllegalArgumentException("Unsupported query type: " + queryType);
        }

        return command;
    }

    /**
     * Checks if the required DNS tools are available on the system
     * @return List of missing tools
     */
    public List<String> checkAvailableTools() {
        List<String> missingTools = new ArrayList<>();

        for (QueryType queryType : QueryType.values()) {
            String command = getCommandName(queryType);
            if (!isCommandAvailable(command)) {
                missingTools.add(command);
            }
        }

        return missingTools;
    }

    private String getCommandName(QueryType queryType) {
        switch (queryType) {
            case DIG: return "dig";
            case NSLOOKUP: return "nslookup";
            case WHOIS: return "whois";
            case HOST: return "host";
            default: return "";
        }
    }

    private boolean isCommandAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();

            // Use 'where' on Windows, 'which' on Unix-like systems
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("where", command);
            } else {
                pb.command("which", command);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking command availability: " + command, e);
            return false;
        }
    }

    /**
     * Validates a domain name format
     * @param domain The domain to validate
     * @return true if the domain appears to be valid
     */
    public boolean isValidDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return false;
        }

        // Basic domain validation regex
        String domainPattern = "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$";
        return domain.matches(domainPattern);
    }
}