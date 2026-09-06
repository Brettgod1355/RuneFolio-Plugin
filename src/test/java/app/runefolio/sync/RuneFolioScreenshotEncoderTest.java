package app.runefolio.sync;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RuneFolioScreenshotEncoderTest
{
    @Test
    public void encodesJpegAndConstrainsDimensions() throws Exception
    {
        BufferedImage source = new BufferedImage(2400, 1400, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();

        byte[] encoded = RuneFolioScreenshotEncoder.encode(source);

        assertTrue(encoded.length > 2);
        assertEquals(0xff, encoded[0] & 0xff);
        assertEquals(0xd8, encoded[1] & 0xff);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded));
        assertNotNull(decoded);
        assertTrue(decoded.getWidth() <= RuneFolioScreenshotEncoder.MAX_WIDTH);
        assertTrue(decoded.getHeight() <= RuneFolioScreenshotEncoder.MAX_HEIGHT);
        double sourceRatio = (double) source.getWidth() / source.getHeight();
        double decodedRatio = (double) decoded.getWidth() / decoded.getHeight();
        assertTrue(Math.abs(sourceRatio - decodedRatio) < 0.002);
    }
}
