package clawdtop;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Things he does with a file you drop on him: makes a smaller copy of a picture (for emailing or texting), or unzips
 * a zip. The original is never changed: the new file or folder goes right next to it.
 */
final class Handy {
    private Handy() {
    }

    /** How big the smaller copy of a picture is, at most (its longest side, in pixels). */
    static final int SMALL_SIDE = 1600;

    static boolean picture(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    static boolean zip(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /** Where a new file goes: next to the original, or (if he can't write there) on your desktop. */
    private static Path beside(Path original, String name) {
        Path folder = original.toAbsolutePath().getParent();
        if (folder == null || !Files.isWritable(folder)) folder = Path.of(System.getProperty("user.home"), "Desktop");
        return Desktop.free(folder.resolve(name));
    }

    /** What shrinking did: the smaller copy, or ALREADY_SMALL (no copy: it couldn't get any smaller), or null (not a picture). */
    static final Path ALREADY_SMALL = Path.of("(already small)");

    /**
     * A smaller copy of a picture: "name (small).jpg", at most SMALL_SIDE pixels across. If that's not actually smaller
     * (some drawings and screenshots squash better as they are), it tries harder, and if it still isn't, no copy is kept.
     */
    static Path shrink(Path picture) throws IOException {
        try (var in = javax.imageio.ImageIO.createImageInputStream(picture.toFile())) { // (how big it is, before opening it all)
            var readers = in == null ? null : javax.imageio.ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(in);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MOST_PIXELS) return null;
            } finally {
                reader.dispose();
            }
        }
        BufferedImage image = javax.imageio.ImageIO.read(picture.toFile());
        if (image == null) return null;
        long before = Files.size(picture);
        Path made = null;
        for (int[] tryThis : new int[][] {{SMALL_SIDE, 85}, {1280, 70}, {960, 60}}) {
            if (made != null) Files.deleteIfExists(made);
            made = shrink(picture, image, tryThis[0], tryThis[1] / 100f);
            if (Files.size(made) < before * 0.9) return made;
        }
        Files.deleteIfExists(made);
        return ALREADY_SMALL;
    }

    private static Path shrink(Path picture, BufferedImage image, int side, float quality) throws IOException {
        double scale = Math.min(1.0, (double) side / Math.max(image.getWidth(), image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale)), h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB); // (JPEG: no see-through, so on white)
        Graphics2D g = small.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, w, h, null);
        g.dispose();
        String name = picture.getFileName().toString();
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        Path to = beside(picture, stem + " (small).jpg");
        var writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
        var param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (var out = javax.imageio.ImageIO.createImageOutputStream(to.toFile())) {
            writer.setOutput(out);
            writer.write(null, new javax.imageio.IIOImage(small, null, null), param);
        } finally {
            writer.dispose();
        }
        return to;
    }

    /** What unzipping found: the folder, and how many files are in it. */
    record Unzipped(Path folder, int files) {
    }

    /** The most a zip may unpack to (so a "zip bomb" can't fill your drive), and the most files. */
    static final long MOST_BYTES = 8L * 1024 * 1024 * 1024;
    static final int MOST_FILES = 50_000;

    /**
     * Unzips into a new folder next to the zip (named after it). Nothing inside can be written outside that folder.
     * If the zip is just one folder, that folder's insides go straight in (no folder-in-a-folder).
     */
    static Unzipped unzip(Path zip) throws IOException {
        String name = zip.getFileName().toString();
        String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        String only = soleFolder(zip); // ("Photos/..." all the way through: unpack Photos' insides)
        Path folder = beside(zip, stem);
        Files.createDirectories(folder);
        Path root = folder.toAbsolutePath().normalize();
        int files = 0;
        long bytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                String inside = e.getName().replace('\\', '/');
                if (inside.startsWith("__MACOSX/") || inside.endsWith(".DS_Store")) continue; // (a Mac's leftovers)
                if (only != null && inside.startsWith(only)) inside = inside.substring(only.length());
                if (inside.isEmpty()) continue;
                Path to = root.resolve(inside).normalize();
                if (!to.startsWith(root)) continue; // (a sneaky "../../" path: skipped)
                if (e.isDirectory()) {
                    Files.createDirectories(to);
                    continue;
                }
                if (++files > MOST_FILES) throw new IOException("too many files");
                Files.createDirectories(to.getParent());
                try (var out = Files.newOutputStream(to)) {
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        bytes += n;
                        if (bytes > MOST_BYTES) throw new IOException("too big");
                        out.write(buffer, 0, n);
                    }
                }
            }
        } catch (IOException | RuntimeException broken) {
            deleteTree(folder); // (no half-unzipped folder left lying about)
            throw broken;
        }
        return new Unzipped(folder, files);
    }

    /** Deletes a folder and everything in it (what he made himself and doesn't need). */
    static void deleteTree(Path folder) {
        if (!Files.exists(folder)) return;
        try (var all = Files.walk(folder)) {
            for (Path p : all.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException | RuntimeException stuck) {
            // whatever's left can stay
        }
    }

    /** The most pixels a picture may have for him to open it (a huge one would use up all his memory). */
    static final long MOST_PIXELS = 150_000_000L;

    /** If everything in the zip is inside one folder, "that folder/" (else null). */
    private static String soleFolder(Path zip) throws IOException {
        String first = null;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                String n = e.getName().replace('\\', '/');
                if (n.startsWith("__MACOSX/")) continue;
                int slash = n.indexOf('/');
                if (slash <= 0) return null; // a file at the top
                String top = n.substring(0, slash + 1);
                if (first == null) first = top;
                else if (!first.equals(top)) return null;
            }
        }
        return first;
    }

    /** How big a file is, nicely ("2.1 MB"). */
    static String size(Path file) {
        try {
            return FindFile.size(Files.size(file));
        } catch (IOException gone) {
            return "?";
        }
    }
}
