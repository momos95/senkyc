package sn.senkyc.api.verification;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;

/** Images synthétiques (bruit aléatoire) pour les tests : aucune image réelle ni donnée personnelle. */
public final class ImageFixtures {

    private ImageFixtures() {}

    public static byte[] image(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Un PNG valide de taille standard (800 x 600). */
    public static byte[] validPng() {
        return image(800, 600, "png");
    }

    public static byte[] validJpeg() {
        return image(800, 600, "jpeg");
    }
}
