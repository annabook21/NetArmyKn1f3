package edu.au.cpsc.part1;

import java.io.*;

public class AirlineDatabaseIO {

    /**
     * Saves the entire AirlineDatabase object to a file.
     * @param ad The AirlineDatabase to save.
     * @param strm The OutputStream to write to (e.g., a FileOutputStream).
     */
    public static void save(AirlineDatabase ad, OutputStream strm) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(strm)) {
            oos.writeObject(ad);
        }
    }

    /**
     * Loads an AirlineDatabase object from a file.
     * @param strm The InputStream to read from (e.g., a FileInputStream).
     * @return The loaded AirlineDatabase.
     */
    public static AirlineDatabase load(InputStream strm) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(strm)) {
            return (AirlineDatabase) ois.readObject();
        }
    }
}

