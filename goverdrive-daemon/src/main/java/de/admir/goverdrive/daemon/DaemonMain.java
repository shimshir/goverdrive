package de.admir.goverdrive.daemon;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.sql.*;

public class DaemonMain {

    private static final Config CONFIG = ConfigFactory.load();

    private static final String DB_FOLDER = CONFIG.getString("goverdrive.db.folder");
    private static final String DB_URL = CONFIG.getString("goverdrive.db.url");

    public static void main(String args[]) {
        File dbFile = new File(DB_FOLDER);
        if (!dbFile.exists() && !dbFile.mkdirs()) {
            System.exit(1);
        }

        try (Connection connection = DriverManager.getConnection(DB_URL); Statement stmt = connection.createStatement()) {
            System.out.println("Opened database successfully");
        } catch (Exception e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
