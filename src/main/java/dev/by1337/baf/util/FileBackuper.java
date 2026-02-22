package dev.by1337.baf.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;

public class FileBackuper {

    private static final Logger log = LoggerFactory.getLogger(FileBackuper.class);

    public static File backup(File who, File folder) {
        if (who == null || !who.exists()) {
            log.error("Cannot backup null or non-existent file: {}", who);
            return null;
        }
        File saveTo = new File(folder, who.getName());
        String name = who.getName();
        int dotIndex = name.lastIndexOf('.');
        String baseName = name.substring(0, dotIndex);
        String extension = name.substring(dotIndex);
        for (int i = 0; saveTo.exists(); i++) {
            if (dotIndex > 0) {
                saveTo = new File(folder, baseName + "_" + i + extension);
            } else {
                saveTo = new File(folder, name + "_" + i);
            }
        }
        try {
            Files.copy(who.toPath(), saveTo.toPath());
        } catch (Exception e) {
            log.error("Failed to create backup!", e);
            return null;
        }
        return saveTo;
    }
}
