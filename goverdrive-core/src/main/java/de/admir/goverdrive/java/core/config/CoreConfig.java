package de.admir.goverdrive.java.core.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class CoreConfig {
    public static final Config CONFIG = ConfigFactory.load();

    public static String getDbFolder() {
        return CONFIG.getString("goverdrive.db.folder");
    }

    public static String getDbFilePath() {
        return CONFIG.getString("goverdrive.db.folder") + "/" + CONFIG.getString("goverdrive.db.schema") + ".db";
    }
}
