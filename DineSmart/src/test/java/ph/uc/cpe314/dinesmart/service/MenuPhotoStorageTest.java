package ph.uc.cpe314.dinesmart.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MenuPhotoStorageTest {
    @TempDir Path tempDir;

    @Test void uploadedJpegIsCopiedToPersistentFolderWithDishSpecificName() throws Exception {
        byte[] content = new byte[] {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
        Path source = tempDir.resolve("phone-photo.jpeg");
        Path destinationFolder = tempDir.resolve("menu-images");
        Files.write(source, content);

        Path saved = MenuPhotoStorage.saveUploadedPhoto(source, "Spicy Arrabiata Penne", destinationFolder);

        assertTrue(Files.isRegularFile(saved));
        assertTrue(saved.getFileName().toString().startsWith("Spicy-Arrabiata-Penne-"));
        assertTrue(saved.getFileName().toString().endsWith(".jpg"));
        assertTrue(Arrays.equals(content, Files.readAllBytes(saved)));
        assertNotEquals(source.toAbsolutePath(), saved);
    }

    @Test void uploadedPngKeepsPngExtension() throws Exception {
        Path source = tempDir.resolve("dish.png");
        Files.write(source, new byte[] {1, 2, 3});
        Path saved = MenuPhotoStorage.saveUploadedPhoto(source, "Garlic Bread", tempDir.resolve("photos"));
        assertTrue(saved.getFileName().toString().endsWith(".png"));
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(saved));
    }

    @Test void uploadedPhotoRejectsUnsupportedMissingAndEmptyFiles() throws Exception {
        Path gif = tempDir.resolve("dish.gif");
        Files.write(gif, new byte[] {1, 2});
        Path empty = tempDir.resolve("empty.jpg");
        Files.createFile(empty);
        Path missing = tempDir.resolve("missing.jpg");
        Path folder = tempDir.resolve("photos");

        assertThrows(IOException.class, () -> MenuPhotoStorage.saveUploadedPhoto(gif, "Dish", folder));
        assertThrows(IOException.class, () -> MenuPhotoStorage.saveUploadedPhoto(missing, "Dish", folder));
        assertThrows(IOException.class, () -> MenuPhotoStorage.saveUploadedPhoto(empty, "Dish", folder));
    }

    @Test void uploadedPhotoRejectsFilesLargerThanTwelveMegabytes() throws Exception {
        Path source = tempDir.resolve("too-large.jpg");
        try (var channel = java.nio.channels.FileChannel.open(source,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(MenuPhotoStorage.MAX_PHOTO_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
        }
        assertThrows(IOException.class,
                () -> MenuPhotoStorage.saveUploadedPhoto(source, "Large Dish", tempDir.resolve("photos")));
    }

    @Test void downloadedMealDbPhotoIncludesVerifiedMealIdInLocalFilename() throws Exception {
        byte[] bytes = new byte[] {10, 20, 30, 40};
        Path saved = MenuPhotoStorage.saveDownloadedPhoto(bytes, "Spicy Arrabiata Penne",
                "52771", ".jpg", tempDir.resolve("recipe-photos"));
        assertTrue(saved.getFileName().toString().contains("themealdb-52771"));
        assertTrue(Files.isRegularFile(saved));
        assertArrayEquals(bytes, Files.readAllBytes(saved));
    }

    @Test void downloadedPhotoRejectsEmptyPayloadAndUnsupportedExtension() {
        Path folder = tempDir.resolve("photos");
        assertThrows(IOException.class,
                () -> MenuPhotoStorage.saveDownloadedPhoto(new byte[0], "Dish", "1", ".jpg", folder));
        assertThrows(IOException.class,
                () -> MenuPhotoStorage.saveDownloadedPhoto(new byte[] {1}, "Dish", "1", ".gif", folder));
    }
}
