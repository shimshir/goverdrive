package de.admir.goverdrive;

import de.admir.goverdrive.core.GoverdriveService;
import de.admir.goverdrive.core.GoverdriveServiceImpl;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class Main {

   private static final GoverdriveService gds = new GoverdriveServiceImpl();

   public static void main(String[] args) {

      gds.getFileStream("/ch1.png").mapRight(outputStream -> {
         try(OutputStream fileStream = new FileOutputStream("/home/amemic/ch1.png")) {
            outputStream.writeTo(fileStream);
         } catch (Exception e) {
            e.printStackTrace();
         }
         return null;
      });
   }
}
