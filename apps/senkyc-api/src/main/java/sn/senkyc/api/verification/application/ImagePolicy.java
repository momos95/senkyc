package sn.senkyc.api.verification.application;

/**
 * Limites d'une image de document ou de selfie. Les valeurs par défaut correspondent à une photo de
 * smartphone ordinaire ; elles sont réglables par configuration (senkyc.verification.documents).
 *
 * @param maxBytes poids maximal (doit rester inférieur à la limite multipart du serveur)
 * @param minBytes poids minimal : en dessous, l'image ne peut pas être lisible
 * @param minShortSidePx plus petit côté minimal, en pixels
 * @param maxPixels nombre maximal de pixels (garde-fou contre les images « bombes »)
 * @param maxAspectRatio rapport maximal plus grand côté / plus petit côté
 */
public record ImagePolicy(int maxBytes, int minBytes, int minShortSidePx, long maxPixels, double maxAspectRatio) {

    public static final ImagePolicy STANDARD = new ImagePolicy(8 * 1024 * 1024, 20 * 1024, 480, 40_000_000L, 4.0);

    public ImagePolicy {
        if (minBytes < 1 || maxBytes < minBytes) {
            throw new IllegalArgumentException("Invalid byte limits");
        }
        if (minShortSidePx < 1 || maxPixels < 1 || maxAspectRatio < 1.0) {
            throw new IllegalArgumentException("Invalid dimension limits");
        }
    }
}
