// QueryResult.java
package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;

/**
 * Represents the result of a DNS query execution
 */
public class QueryResult {
    private String domain;
    private QueryType queryType;
    private String output;
    private String errorOutput;
    private boolean success;
    private int exitCode;
    private long executionTime;
    private LocalDateTime timestamp;

    public QueryResult() {
        this.timestamp = LocalDateTime.now();
    }

    // Getters and setters
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public void setQueryType(QueryType queryType) {
        this.queryType = queryType;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getErrorOutput() {
        return errorOutput;
    }

    public void setErrorOutput(String errorOutput) {
        this.errorOutput = errorOutput;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("QueryResult{domain='%s', queryType=%s, success=%s, exitCode=%d, executionTime=%dms}",
                domain, queryType, success, exitCode, executionTime);
    }
}