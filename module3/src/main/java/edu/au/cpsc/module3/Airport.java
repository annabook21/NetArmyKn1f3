package edu.au.cpsc.module3;

// Airport.java - Domain Model Class
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Airport {
    private String ident;
    private String type;
    private String name;
    private Integer elevationFt;
    private String continent;
    private String isoCountry;
    private String isoRegion;
    private String municipality;
    private String gpsCode;
    private String iataCode;
    private String localCode;
    private Double longitude;
    private Double latitude;

    // Constructor
    public Airport() {}

    // Getters and Setters
    public String getIdent() { return ident; }
    public void setIdent(String ident) { this.ident = ident; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getElevationFt() { return elevationFt; }
    public void setElevationFt(Integer elevationFt) { this.elevationFt = elevationFt; }

    public String getContinent() { return continent; }
    public void setContinent(String continent) { this.continent = continent; }

    public String getIsoCountry() { return isoCountry; }
    public void setIsoCountry(String isoCountry) { this.isoCountry = isoCountry; }

    public String getIsoRegion() { return isoRegion; }
    public void setIsoRegion(String isoRegion) { this.isoRegion = isoRegion; }

    public String getMunicipality() { return municipality; }
    public void setMunicipality(String municipality) { this.municipality = municipality; }

    public String getGpsCode() { return gpsCode; }
    public void setGpsCode(String gpsCode) { this.gpsCode = gpsCode; }

    public String getIataCode() { return iataCode; }
    public void setIataCode(String iataCode) { this.iataCode = iataCode; }

    public String getLocalCode() { return localCode; }
    public void setLocalCode(String localCode) { this.localCode = localCode; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    // Static method to read all airports from CSV
    public static List<Airport> readAll() throws IOException {
        List<Airport> airports = new ArrayList<>();

        // Read the CSV file from resources
        InputStream inputStream = Airport.class.getResourceAsStream("airport-codes.csv");
        if (inputStream == null) {
            throw new IOException("Could not find airport-codes.csv in resources");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line = reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                Airport airport = parseLine(line);
                if (airport != null) {
                    airports.add(airport);
                }
            }
        }

        return airports;
    }

    private static Airport parseLine(String line) {
        // Simple CSV parser - handles basic cases
        // For production, consider using a proper CSV library like OpenCSV
        String[] fields = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");

        if (fields.length < 13) {
            return null; // Skip malformed lines
        }

        Airport airport = new Airport();

        airport.setIdent(cleanField(fields[0]));
        airport.setType(cleanField(fields[1]));
        airport.setName(cleanField(fields[2]));
        airport.setElevationFt(parseInteger(fields[3]));
        airport.setContinent(cleanField(fields[4]));
        airport.setIsoCountry(cleanField(fields[5]));
        airport.setIsoRegion(cleanField(fields[6]));
        airport.setMunicipality(cleanField(fields[7]));
        airport.setGpsCode(cleanField(fields[8]));
        airport.setIataCode(cleanField(fields[9]));
        airport.setLocalCode(cleanField(fields[10]));
        airport.setLongitude(parseDouble(fields[11]));
        airport.setLatitude(parseDouble(fields[12]));

        return airport;
    }

    private static String cleanField(String field) {
        if (field == null || field.trim().isEmpty()) {
            return null;
        }
        field = field.trim();
        if (field.startsWith("\"") && field.endsWith("\"")) {
            field = field.substring(1, field.length() - 1);
        }
        return field.isEmpty() ? null : field;
    }

    private static Integer parseInteger(String value) {
        try {
            String cleaned = cleanField(value);
            return cleaned == null ? null : Integer.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(String value) {
        try {
            String cleaned = cleanField(value);
            return cleaned == null ? null : Double.valueOf(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Search methods for the UI
    public static Airport findByIdent(List<Airport> airports, String ident) {
        return airports.stream()
                .filter(a -> ident != null && ident.equalsIgnoreCase(a.getIdent()))
                .findFirst()
                .orElse(null);
    }

    public static Airport findByIataCode(List<Airport> airports, String iataCode) {
        return airports.stream()
                .filter(a -> iataCode != null && iataCode.equalsIgnoreCase(a.getIataCode()))
                .findFirst()
                .orElse(null);
    }

    public static Airport findByLocalCode(List<Airport> airports, String localCode) {
        return airports.stream()
                .filter(a -> localCode != null && localCode.equalsIgnoreCase(a.getLocalCode()))
                .findFirst()
                .orElse(null);
    }
}