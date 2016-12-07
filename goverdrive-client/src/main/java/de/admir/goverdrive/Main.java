package de.admir.goverdrive;

import de.admir.goverdrive.core.GoverdriveService;
import de.admir.goverdrive.core.GoverdriveServiceImpl;

public class Main {
    private static final GoverdriveService gds = new GoverdriveServiceImpl();

    public static void main(String[] args) {
        gds.getFilePath("/test_folder/inner_folder1/inner_folder2");
        gds.getFilePath("/");
    }
}
