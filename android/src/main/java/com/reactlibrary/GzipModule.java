package com.reactlibrary;

import android.os.Build;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.kamranzafar.jtar.TarInputStream;

public class GzipModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;

    public GzipModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "Gzip";
    }

    @ReactMethod
    public void unTar(String source, String target, Boolean force, Promise promise) {
        File sourceFile = new File(source);
        File targetFile = new File(target);
        if(!checkDir(sourceFile, targetFile, force)){
            promise.reject("-2", "error");
            return;
        }

        ArchiveInputStream inputStream = null;
        FileInputStream fileInputStream;

        try{
            fileInputStream = FileUtils.openInputStream(sourceFile);
            inputStream = new ArchiveStreamFactory()
                    .createArchiveInputStream(ArchiveStreamFactory.TAR, fileInputStream);

            ArchiveEntry archiveEntry = inputStream.getNextEntry();

            while (archiveEntry != null) {
                File destFile = new File(targetFile, archiveEntry.getName());
                if (archiveEntry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    final FileOutputStream outputStream = FileUtils.openOutputStream(destFile);
                    IOUtils.copy(inputStream, outputStream);
                    outputStream.close();
                }
                archiveEntry = inputStream.getNextEntry();
            }

            WritableMap map = Arguments.createMap();
            map.putString("path", targetFile.getAbsolutePath());
            promise.resolve(map);
        } catch (ArchiveException | IOException  e) {
            e.printStackTrace();
            promise.reject("-2", "untar error");
        }
    }

    @ReactMethod
    public void unGzip(String source, String target, Boolean force, Promise promise) {
        File sourceFile = new File(source);
        File targetFile = new File(target);
        if(!checkDir(sourceFile, targetFile, force)){
            promise.reject("-2", "error");
            return;
        }

        FileInputStream fileInputStream;

        try{
            fileInputStream = FileUtils.openInputStream(sourceFile);
            final CompressorInputStream compressorInputStream = new CompressorStreamFactory()
                    .createCompressorInputStream(CompressorStreamFactory.GZIP, fileInputStream);

            final FileOutputStream outputStream = FileUtils.openOutputStream(targetFile);
            IOUtils.copy(compressorInputStream, outputStream);
            outputStream.close();

            WritableMap map = Arguments.createMap();
            map.putString("path", targetFile.getAbsolutePath());
            promise.resolve(map);
        } catch (IOException | CompressorException e) {
            e.printStackTrace();
            promise.reject("-2", "ungzip error");
        }
    }

    @ReactMethod
    public void unGzipTar(String source, String target, Boolean force, Promise promise) {
        File sourceFile = new File(source);
        File targetFile = new File(target);
        if (!checkDir(sourceFile, targetFile, force)) {
            promise.reject("-2", "error while checking directories for unGzipTar");
            return;
        }

        try {
            // Include all versions below API level 26
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // Manual gzip and tar extraction for Android versions below 8.0 (API level 26)
                FileInputStream fis = new FileInputStream(sourceFile);
                GZIPInputStream gis = new GZIPInputStream(fis);
                untar2(gis, targetFile);
            } else {
                // Use Apache Commons Compress for Android 8.0 (Oreo, API level 26) and above
                FileInputStream fileInputStream = FileUtils.openInputStream(sourceFile);
                final CompressorInputStream compressorInputStream = new CompressorStreamFactory()
                        .createCompressorInputStream(CompressorStreamFactory.GZIP, fileInputStream);
                ArchiveInputStream inputStream = new ArchiveStreamFactory()
                        .createArchiveInputStream(ArchiveStreamFactory.TAR, compressorInputStream);
                extractFromArchiveInputStream(inputStream, targetFile);
            }

            WritableMap map = Arguments.createMap();
            map.putString("path", targetFile.getAbsolutePath());
            promise.resolve(map);
        } catch (IOException | CompressorException | ArchiveException e) {
            e.printStackTrace();
            promise.reject("-2", "ungzip error");
        }
    }

    private void extractFromArchiveInputStream(ArchiveInputStream inputStream, File targetFile) throws IOException, ArchiveException {
        ArchiveEntry archiveEntry;
        while ((archiveEntry = inputStream.getNextEntry()) != null) {
            File destFile = new File(targetFile, archiveEntry.getName());
            if (archiveEntry.isDirectory()) {
                destFile.mkdirs();
            } else {
                try (final FileOutputStream outputStream = FileUtils.openOutputStream(destFile)) {
                    IOUtils.copy(inputStream, outputStream);
                }
            }
        }
    }

    private void untar2(GZIPInputStream gis, File targetFile) throws IOException {
        // Create a TarInputStream from the GZIPInputStream
        TarInputStream tis = new TarInputStream(gis);
        org.kamranzafar.jtar.TarEntry entry;

        // Iterate through the entries in the TAR stream
        while ((entry = tis.getNextEntry()) != null) {
            File outputFile = new File(targetFile, entry.getName());

            // Check if the entry is a directory
            if (entry.isDirectory()) {
                outputFile.mkdirs();
            } else {
                // Ensure parent directories exist
                outputFile.getParentFile().mkdirs();

                // Write the entry to file
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    int count;
                    byte data[] = new byte[2048];

                    // Read data from the entry and write it to the output file
                    while ((count = tis.read(data)) != -1) {
                        bos.write(data, 0, count);
                    }
                }
            }
        }
        tis.close();
    }

    private Boolean checkDir(File sourceFile, File targetFile, Boolean force) {
        if (!sourceFile.exists()) {
            return false;
        }

        if (targetFile.exists()) {
            if (!force) {
                return false;
            }

            try {
                deleteRecursively(targetFile);
                targetFile.mkdirs();
            } catch (IOException ex) {
                return false;
            }
        }
        return true;
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            // List all the directory contents
            File[] files = file.listFiles();
            if (files != null) {  // Some JVMs return null for empty directories
                for (File child : files) {
                    // Recursive delete
                    deleteRecursively(child);
                }
            }
        }

        // Check if file actually exists before deletion
        if (!file.delete()) {
            throw new IOException("Failed to delete file: " + file.getAbsolutePath());
        }
    }
}

