package de.admir.goverdrive;

import de.admir.goverdrive.core.GoverdriveService;
import de.admir.goverdrive.core.GoverdriveServiceImpl;

public class Main {
    private static final GoverdriveService gds = new GoverdriveServiceImpl();

    public static void main(String[] args) {
		 Object result = gds.createFile("/home/amemic/projects/goverdrive/goverdrive-core/src/main/java/de/admir/goverdrive/core/GoverdriveServiceImpl.java", "/GoverdriveServiceImpl.java");
		 System.out.println(result);
    }
}
