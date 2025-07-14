package edu.au.cpsc.module7.models;

import java.util.List;

/**
 * Model class representing subnet calculation results and configuration.
 */
public class SubnetCalculation {
    private String baseIp;
    private String originalCidr;
    private String suggestedIp;
    private String subnetMask;
    private int prefixLength;
    private int requestedSubnets;
    private List<String> generatedSubnets;
    private String explanation;
    private boolean isValid;
    private String errorMessage;

    public SubnetCalculation() {
        this.isValid = false;
    }

    // Getters and Setters
    public String getBaseIp() {
        return baseIp;
    }

    public void setBaseIp(String baseIp) {
        this.baseIp = baseIp;
    }

    public String getOriginalCidr() {
        return originalCidr;
    }

    public void setOriginalCidr(String originalCidr) {
        this.originalCidr = originalCidr;
    }

    public String getSuggestedIp() {
        return suggestedIp;
    }

    public void setSuggestedIp(String suggestedIp) {
        this.suggestedIp = suggestedIp;
    }

    public String getSubnetMask() {
        return subnetMask;
    }

    public void setSubnetMask(String subnetMask) {
        this.subnetMask = subnetMask;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public int getRequestedSubnets() {
        return requestedSubnets;
    }

    public void setRequestedSubnets(int requestedSubnets) {
        this.requestedSubnets = requestedSubnets;
    }

    public List<String> getGeneratedSubnets() {
        return generatedSubnets;
    }

    public void setGeneratedSubnets(List<String> generatedSubnets) {
        this.generatedSubnets = generatedSubnets;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "SubnetCalculation{" +
                "baseIp='" + baseIp + '\'' +
                ", suggestedIp='" + suggestedIp + '\'' +
                ", subnetMask='" + subnetMask + '\'' +
                ", prefixLength=" + prefixLength +
                ", requestedSubnets=" + requestedSubnets +
                ", generatedSubnets=" + generatedSubnets +
                ", explanation='" + explanation + '\'' +
                ", isValid=" + isValid +
                '}';
    }
} 