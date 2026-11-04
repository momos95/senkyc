package sn.senkyc.api.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import sn.senkyc.api.verification.ImageFixtures;
import sn.senkyc.api.verification.application.InvalidImageException.Reason;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator(ImagePolicy.STANDARD);
    private final byte[] png = ImageFixtures.validPng();
    private final byte[] jpeg = ImageFixtures.validJpeg();

    private void assertRejected(byte[] content, String declared, Reason reason) {
        assertThatThrownBy(() -> validator.validate(content, declared))
                .isInstanceOfSatisfying(InvalidImageException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void acceptsAValidJpegAndPngAndReportsTheirRealType() {
        assertThat(validator.validate(jpeg, "image/jpeg").contentType()).isEqualTo("image/jpeg");
        assertThat(validator.validate(png, null).contentType()).isEqualTo("image/png");
        assertThat(validator.validate(png, "image/png").width()).isEqualTo(800);
        assertThat(validator.validate(png, "image/png").height()).isEqualTo(600);
    }

    @Test
    void genericOrMissingDeclaredTypeRelyOnTheContent() {
        validator.validate(png, "application/octet-stream");
        validator.validate(png, "");
        validator.validate(jpeg, "image/jpg");
        validator.validate(jpeg, "IMAGE/JPEG; charset=binary");
    }

    @Test
    void rejectsEmptyTooSmallAndTooLargeFiles() {
        assertRejected(new byte[0], null, Reason.EMPTY);
        assertRejected(new byte[100], null, Reason.TOO_SMALL);
        assertRejected(new byte[ImagePolicy.STANDARD.maxBytes() + 1], null, Reason.TOO_LARGE);
    }

    @Test
    void rejectsOtherFormatsEvenWhenTheyClaimToBeImages() {
        byte[] gif = new byte[30_000];
        System.arraycopy("GIF89a".getBytes(), 0, gif, 0, 6);
        byte[] html = new byte[30_000];
        System.arraycopy("<html><script>".getBytes(), 0, html, 0, 14);
        byte[] pdf = new byte[30_000];
        System.arraycopy("%PDF-1.7".getBytes(), 0, pdf, 0, 8);

        assertRejected(gif, "image/gif", Reason.UNSUPPORTED_FORMAT);
        assertRejected(html, "image/png", Reason.UNSUPPORTED_FORMAT);
        assertRejected(pdf, "application/pdf", Reason.UNSUPPORTED_FORMAT);
    }

    @Test
    void rejectsADeclaredTypeThatContradictsTheContent() {
        assertRejected(png, "image/jpeg", Reason.CONTENT_TYPE_MISMATCH);
        assertRejected(jpeg, "text/html", Reason.CONTENT_TYPE_MISMATCH);
    }

    @Test
    void rejectsTruncatedFiles() {
        assertRejected(Arrays.copyOf(jpeg, jpeg.length - 5_000), null, Reason.CORRUPTED);
        assertRejected(Arrays.copyOf(png, png.length - 5_000), null, Reason.CORRUPTED);
    }

    @Test
    void rejectsImagesTooSmallInPixelsOrWithAbsurdProportions() {
        assertRejected(ImageFixtures.image(300, 900, "png"), null, Reason.DIMENSIONS_TOO_SMALL);
        assertRejected(ImageFixtures.image(2400, 500, "png"), null, Reason.ASPECT_RATIO);
    }

    @Test
    void rejectsImagesAnnouncingTooManyPixelsWithoutDecodingThem() {
        byte[] bomb = ImageFixtures.image(600, 600, "png");
        // En-tête IHDR (octets 16 à 23) falsifié : 30000 x 30000 pixels (900 millions) ; seul l'en-tête est lu, l'image n'est jamais décodée.
        bomb[18] = 0x75;
        bomb[19] = 0x30;
        bomb[22] = 0x75;
        bomb[23] = 0x30;

        assertThatThrownBy(() -> validator.validate(bomb, null))
                .isInstanceOfSatisfying(
                        InvalidImageException.class, e -> assertThat(e.reason()).isEqualTo(Reason.DIMENSIONS_TOO_LARGE));
    }
}
