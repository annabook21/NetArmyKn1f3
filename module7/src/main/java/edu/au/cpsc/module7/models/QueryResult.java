// QueryResult.java
package edu.au.cpsc.module7.models;

import java.time.LocalDateTime;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.time.format.DateTimeFormatter;

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
    private StringProperty timestamp;
    private StringProperty command;
    private StringProperty result;

    public QueryResult() {
        this(null, null);
    }

    public QueryResult(String command, String result) {
        this.timestamp = new SimpleStringProperty(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        this.command = new SimpleStringProperty(command);
        this.result = new SimpleStringProperty(result);
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

    public String getTimestamp() {
        return timestamp.get();
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp.set(timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    public StringProperty getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command.set(command);
    }

    public StringProperty getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result.set(result);
    }

    @Override
    public String toString() {
        return String.format("QueryResult{domain='%s', queryType=%s, success=%s, exitCode=%d, executionTime=%dms}",
                domain, queryType, success, exitCode, executionTime);
    }
}