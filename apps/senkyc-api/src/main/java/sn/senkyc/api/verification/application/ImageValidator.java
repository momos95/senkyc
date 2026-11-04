package sn.senkyc.api.verification.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import sn.senkyc.api.verification.application.InvalidImageException.Reason;

/**
 * Contrôle d'une image soumise avant tout stockage : seuls JPEG et PNG, identifiés par leur CONTENU (octets
 * magiques) et non par le nom ou l'en-tête déclaré, poids et dimensions raisonnables, structure intacte.
 *
 * <p>Seul l'en-tête est lu pour obtenir les dimensions (l'image n'est pas décodée entièrement : pas de
 * saturation mémoire sur une image « bombe »). Ce contrôle ne remplace ni un antivirus ni la détection
 * de falsification ; il écarte les fichiers manifestement invalides.
 */
public class ImageValidator {

    /** Résultat : le type réel détecté et les dimensions. */
    public record ValidatedImage(String contentType, int width, int height) {}

    private enum Format {
        JPEG("image/jpeg", "jpeg"),
        PNG("image/png", "png");

        final String contentType;
        final String readerName;

        Format(String contentType, String readerName) {
            this.contentType = contentType;
            this.readerName = readerName;
        }
    }

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PNG_IEND = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};

    static {
        // Pas de fichier temporaire sur disque pour les flux d'image.
        ImageIO.setUseCache(false);
    }

    private final ImagePolicy policy;

    public ImageValidator(ImagePolicy policy) {
        this.policy = policy;
    }

    public ImagePolicy policy() {
        return policy;
    }

    /**
     * @param declaredContentType type annoncé par le client (peut être absent ou générique)
     * @throws InvalidImageException si l'image est refusée
     */
    public ValidatedImage validate(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException(Reason.EMPTY);
        }
        if (content.length > policy.maxBytes()) {
            throw new InvalidImageException(Reason.TOO_LARGE);
        }
        if (content.length < policy.minBytes()) {
            throw new InvalidImageException(Reason.TOO_SMALL);
        }
        Format format = detect(content);
        checkDeclaredType(declaredContentType, format);
        if (!hasIntactEnding(content, format)) {
            throw new InvalidImageException(Reason.CORRUPTED);
        }
        int[] size = readSize(content, format);
        int width = size[0];
        int height = size[1];
        if (width < 1 || height < 1) {
            throw new InvalidImageException(Reason.CORRUPTED);
        }
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        if (shortSide < policy.minShortSidePx()) {
            throw new InvalidImageException(Reason.DIMENSIONS_TOO_SMALL);
        }
        if ((long) width * height > policy.maxPixels()) {
            throw new InvalidImageException(Reason.DIMENSIONS_TOO_LARGE);
        }
        if ((double) longSide / shortSide > policy.maxAspectRatio()) {
            throw new InvalidImageException(Reason.ASPECT_RATIO);
        }
        return new ValidatedImage(format.contentType, width, height);
    }

    private static Format detect(byte[] c) {
        if (c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF) {
            return Format.JPEG;
        }
        if (c.length >= PNG_MAGIC.length && Arrays.equals(Arrays.copyOf(c, PNG_MAGIC.length), PNG_MAGIC)) {
            return Format.PNG;
        }
        throw new InvalidImageException(Reason.UNSUPPORTED_FORMAT);
    }

    /** Un type annoncé qui contredit le contenu est refusé ; absent ou générique, on se fie au contenu. */
    private static void checkDeclaredType(String declared, Format actual) {
        if (declared == null || declared.isBlank()) {
            return;
        }
        String base = declared.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (base.equals("application/octet-stream")) {
            return;
        }
        if (base.equals("image/jpg")) {
            base = "image/jpeg";
        }
        if (!base.equals(actual.contentType)) {
            throw new InvalidImageException(Reason.CONTENT_TYPE_MISMATCH);
        }
    }

    private static boolean hasIntactEnding(byte[] c, Format format) {
        if (format == Format.PNG) {
            return c.length >= PNG_IEND.length
                    && Arrays.equals(Arrays.copyOfRange(c, c.length - PNG_IEND.length, c.length), PNG_IEND);
        }
        int end = c.length - 1;
        while (end > 0 && c[end] == 0) {
            end--; // tolérance : octets de remplissage nuls
        }
        return end >= 1 && (c[end] & 0xFF) == 0xD9 && (c[end - 1] & 0xFF) == 0xFF;
    }

    private static int[] readSize(byte[] content, Format format) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new InvalidImageException(Reason.CORRUPTED);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                if (!reader.getFormatName().toLowerCase(Locale.ROOT).startsWith(format.readerName)) {
                    reader.dispose();
                    continue;
                }
                try {
                    reader.setInput(input, true, true);
                    return new int[] {reader.getWidth(0), reader.getHeight(0)};
                } finally {
                    reader.dispose();
                }
            }
            throw new InvalidImageException(Reason.CORRUPTED);
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw new InvalidImageException(Reason.CORRUPTED);
        }
    }
}
