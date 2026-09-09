package dev.olegz.vf.api.lambda.deployment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.WrongParameterValueException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

/**
 * Utility class providing methods for extracting and creating archive files in TAR and ZIP formats.
 * This class handles reading archive entries from byte arrays and creating archive byte arrays from collections of files.
 * All methods are static and the class is not meant to be instantiated.
 */
public class LambdaCodeArchive {
    public static final String CONTENT_TYPE_TAR = "application/x-tar";
    public static final String CONTENT_TYPE_ZIP = "application/zip";

    public static HashMap<String, byte[]> extract(byte[] data, String contentType) {
        HashMap<String, byte[]> entries;
        if (CONTENT_TYPE_TAR.equals(contentType)) {
            try (TarArchiveInputStream tis = new TarArchiveInputStream(new ByteArrayInputStream(data))) {
                entries = new HashMap<>(0);
                for (TarArchiveEntry entry = tis.getNextEntry(); entry != null; entry = tis.getNextEntry()) {
                    if (entry.isFile()) {
                        entries.put(entryPath(entry.getName()), readData(tis, entry.getSize()));
                    }
                }
            } catch (IOException e) {
                throw new WrongParameterValueException("Cannot parse TAR archive data");
            }
        } else if (CONTENT_TYPE_ZIP.equals(contentType)) {
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
                entries = new HashMap<>(0);
                for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                    if (!entry.isDirectory()) {
                        entries.put(entryPath(entry.getName()), readData(zis, entry.getSize()));
                    }
                }
            } catch (IOException e) {
                throw new WrongParameterValueException("Cannot parse ZIP archive data");
            }
        } else {
            throw new WrongParameterValueException("Not supported content type " + contentType);
        }

        return entries;
    }

    private static String entryPath(String entryName) {
        return Paths.get(entryName).normalize().toString();
    }

    private static byte[] readData(InputStream in, long contentLength) throws IOException {
        return in.readNBytes(contentLength < 0 ? Integer.MAX_VALUE : (int) contentLength);
    }

    /**
     * Creates a TAR archive containing the provided entries and returns it as a byte array.
     * Each entry in the map is added to the archive with its corresponding name and data.
     * If an IOException occurs during archive creation, it is silently ignored and the
     * partial archive content is returned.
     *
     * @param entries a map where keys are entry names/paths and values are the entry data as byte arrays
     * @return a byte array containing the complete TAR archive, or an empty byte array if the entries map is empty
     */
    public static byte[] tar(Map<String, byte[]> entries) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos)) {
            entries.forEach((name, data) -> createTarEntry(tos, name, data));
            tos.flush();
        } catch (IOException ignore) {
        }

        return bos.toByteArray();
    }

    /**
     * Creates and writes a tar archive entry to the provided tar output stream.
     * If either the name or data parameter is null, no entry is created.
     * The entry is automatically closed after writing the data.
     *
     * @param tos the TarArchiveOutputStream to write the entry to
     * @param name the name/path of the entry within the tar archive
     * @param data the byte array containing the entry's data
     * @throws ApplicationFailureException if an IOException occurs while creating or writing the tar entry
     */
    private static void createTarEntry(TarArchiveOutputStream tos, String name, byte[] data) {
        if ((name != null) && (data != null)) {
            try {
                TarArchiveEntry archiveEntry = new TarArchiveEntry(name);
                archiveEntry.setSize(data.length);
                tos.putArchiveEntry(archiveEntry);
                tos.write(data);
                tos.closeArchiveEntry();
            } catch (IOException e) {
                throw new ApplicationFailureException("Exception in creating a tar entry " + name, e);
            }
        }
    }

    /**
     * Creates a ZIP archive containing a single entry with the specified name and data.
     * The archive is returned as a byte array. If an IOException occurs during archive
     * creation, it is silently ignored and the partial archive content is returned.
     *
     * @param entryName the name/path of the entry within the ZIP archive
     * @param data the byte array containing the entry's data
     * @return a byte array containing the complete ZIP archive
     */
    public static byte[] zip(String entryName, byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            createZipEntry(zos, entryName, data);
            zos.flush();
        } catch (IOException ignore) {
        }

        return bos.toByteArray();
    }

    /**
     * Creates and writes a ZIP archive entry to the provided ZIP output stream.
     * If either the name or data parameter is null, no entry is created.
     * The entry size is set to the length of the data array, and the entry is
     * automatically closed after writing the data.
     *
     * @param zos the ZipOutputStream to write the entry to
     * @param name the name/path of the entry within the ZIP archive
     * @param data the byte array containing the entry's data
     * @throws ApplicationFailureException if an IOException occurs while creating or writing the ZIP entry
     */
    public static void createZipEntry(ZipOutputStream zos, String name, byte[] data) {
        if ((name != null) && (data != null)) {
            try {
                ZipEntry zipEntry = new ZipEntry(name);
                zipEntry.setSize(data.length);
                zos.putNextEntry(zipEntry);
                zos.write(data);
                zos.closeEntry();
            } catch (IOException e) {
                throw new ApplicationFailureException("Exception in creating a zip entry for " + name, e);
            }
        }
    }
}
