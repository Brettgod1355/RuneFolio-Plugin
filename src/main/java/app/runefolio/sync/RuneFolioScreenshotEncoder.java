package app.runefolio.sync;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

final class RuneFolioScreenshotEncoder
{
    static final int MAX_WIDTH = 1920;
    static final int MAX_HEIGHT = 1080;
    static final float JPEG_QUALITY = 0.82f;

    private RuneFolioScreenshotEncoder()
    {
    }

    static byte[] encode(Image source) throws IOException
    {
        BufferedImage input = toBufferedImage(source);
        double scale = Math.min(1.0, Math.min(
            (double) MAX_WIDTH / input.getWidth(),
            (double) MAX_HEIGHT / input.getHeight()
        ));
        int width = Math.max(1, (int) Math.round(input.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(input.getHeight() * scale));

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(input, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext())
        {
            throw new IOException("No JPEG encoder is available.");
        }

        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream stream = ImageIO.createImageOutputStream(bytes))
        {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(output, null, null), params);
            stream.flush();
            return bytes.toByteArray();
        }
        finally
        {
            writer.dispose();
        }
    }

    private static BufferedImage toBufferedImage(Image source)
    {
        if (source instanceof BufferedImage)
        {
            return (BufferedImage) source;
        }
        BufferedImage buffered = new BufferedImage(
            Math.max(1, source.getWidth(null)),
            Math.max(1, source.getHeight(null)),
            BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = buffered.createGraphics();
        try
        {
            graphics.drawImage(source, 0, 0, null);
        }
        finally
        {
            graphics.dispose();
        }
        return buffered;
    }
}
