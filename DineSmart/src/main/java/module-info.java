package ph.uc.cpe314.dinesmart.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

/** File-system storage for uploaded dish photos and downloaded recipe thumbnails. */
public final class MenuPhotoStorage {
    public static final long MAX_PHOTO_BYTES = 12L * 1024L * 1024L;
    private MenuPhotoStorage() { }

    /**
     * Uses a test-overridable property while keeping the normal per-user folder
     * unchanged for restaurant installations.
     */
    public static Path defaultImageFolder() {
        String configured = System.getProperty("dinesmart.menu.photo.dir");
        if (configured != null && !configured.isBlank()) { return Path.of(configured); }
        return Path.of(System.getProperty("user.home"), ".dinesmart", "menu-images");
    }

    public static Path saveUploadedPhoto(Path source, String itemName, Path imageFolder) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("Choose a valid image file.");
        }
        String fileName = source.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension;
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) { extension = ".jpg"; }
        else if (fileName.endsWith(".png")) { extension = ".png"; }
        else throw new IOException("Only JPG, JPEG, and PNG photos are supported.");

        long size = Files.size(source);
        if (size <= 0 || size > MAX_PHOTO_BYTES) {
            throw new IOException("Photo must be greater than 0 bytes and no larger than 12 MB.");
        }
        Path destination = createDestination(imageFolder, itemName, extension);
        Files.copy(source, destination);
        return destination.toAbsolutePath();
    }

    public static Path saveDownloadedPhoto(byte[] photoBytes, String itemName,
                                           String mealId, String extension,
                                           Path imageFolder) throws IOException {
        if (photoBytes == null || photoBytes.length == 0 || photoBytes.length > MAX_PHOTO_BYTES) {
            throw new IOException("The downloaded recipe photo is empty or exceeds 12 MB.");
        }
        String normalizedExtension = extension == null ? ".jpg" : extension.toLowerCase(Locale.ROOT);
        if (!normalizedExtension.startsWith(".")) { normalizedExtension = "." + normalizedExtension; }
        if (!normalizedExtension.matches("\\.(jpg|jpeg|png|webp)")) {
            throw new IOException("The downloaded recipe photo has an unsupported image format.");
        }
        String safeMealId = mealId == null ? "unknown" : mealId.replaceAll("[^A-Za-z0-9_-]", "");
        Path destination = createDestination(imageFolder, itemName,
                "-themealdb-" + safeMealId + normalizedExtension);
        Files.write(destination, photoBytes);
        return destination.toAbsolutePath();
    }

    private static Path createDestination(Path imageFolder, String itemName, String suffix) throws IOException {
        if (imageFolder == null) { throw new IOException("Menu photo folder is not configured."); }
        Files.createDirectories(imageFolder);
        String safeName = (itemName == null ? "menu-item" : itemName)
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (safeName.isBlank()) { safeName = "menu-item"; }
        return imageFolder.resolve(safeName + "-" + UUID.randomUUID() + suffix);
    }
}
