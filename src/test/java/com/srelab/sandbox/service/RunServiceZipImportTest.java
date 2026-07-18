package com.srelab.sandbox.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a real bug: a zip created via macOS Finder's
 * "Compress" (or the `zip` CLI on macOS) includes a __MACOSX/ sibling tree
 * of AppleDouble resource-fork files (._Foo.java next to every real
 * Foo.java). Uploading such a zip previously failed with
 * "file name '...' is too long ( > 100 bytes)" because the combined
 * __MACOSX/<deep path>/._File.java path exceeded the classic tar format's
 * 100-byte filename limit -- a confusing error that had nothing to do with
 * the user's actual code.
 */
class RunServiceZipImportTest {

    @Test
    void convertZipToTarGzSkipsMacMetadataAndDoesNotThrow() throws Exception {
        byte[] zipBytes = buildMacFinderStyleZip();

        byte[] tarGz = assertDoesNotThrow(() -> RunService.convertZipToTarGz(zipBytes),
            "convertZipToTarGz should not throw on a macOS-Finder-style zip containing __MACOSX/._* entries");

        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(tarGz));
             var tar = new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(gzip)) {
            boolean foundRealFile = false;
            boolean foundMacJunk = false;
            org.apache.commons.compress.archivers.ArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.getName().contains("OrderRepository.java") && !entry.getName().contains("__MACOSX")) {
                    foundRealFile = true;
                }
                if (entry.getName().contains("__MACOSX") || entry.getName().contains("._")) {
                    foundMacJunk = true;
                }
            }
            assertTrue(foundRealFile, "the real OrderRepository.java should be present in the tar");
            assertFalse(foundMacJunk, "__MACOSX/._* entries should have been filtered out");
        }
    }

    /**
     * Builds an in-memory zip reproducing the exact structure macOS
     * Finder/zip produces: the real file at a deeply nested path, plus a
     * __MACOSX/ mirror containing an AppleDouble file whose combined path
     * exceeds 100 bytes -- the same shape that triggered the original bug.
     */
    private byte[] buildMacFinderStyleZip() throws Exception {
        String deepPath = "test-apps/inventory-app/src/main/java/com/srelab/inventory/repository/";
        String realFile = deepPath + "OrderRepository.java";
        String macJunkFile = "__MACOSX/" + deepPath + "._OrderRepository.java";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry(realFile));
            zos.write("public interface OrderRepository {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(macJunkFile));
            zos.write(new byte[]{0x00, 0x05, 0x16, 0x07, 0x00, 0x02, 0x00, 0x00}); // AppleDouble magic header
            zos.closeEntry();
        }
        return out.toByteArray();
    }
}
