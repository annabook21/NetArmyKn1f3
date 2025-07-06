// QueryType.java
package edu.au.cpsc.module7.models;

/**
 * Enumeration of supported DNS query types
 */
public enum QueryType {
    DIG("dig", "Domain Information Groper - detailed DNS lookup"),
    NSLOOKUP("nslookup", "Name Server Lookup - basic DNS resolution"),
    WHOIS("whois", "Domain registration and ownership information"),
    HOST("host", "Simple hostname lookup utility");

    private final String command;
    private final String description;

    QueryType(String command, String description) {
        this.command = command;
        this.description = description;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return command;
    }
}