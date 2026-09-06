package app.runefolio.sync;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class RuneFolioBrand
{
    private static final Color GOLD = new Color(217, 184, 97);
    private static final Color INK = new Color(20, 25, 19);

    private RuneFolioBrand()
    {
    }

    static BufferedImage createIcon(int requestedSize)
    {
        int size = Math.max(12, requestedSize);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(GOLD);
        int inset = Math.max(1, size / 16);
        int arc = Math.max(4, size / 4);
        graphics.fillRoundRect(inset, inset, size - inset * 2, size - inset * 2, arc, arc);

        graphics.setColor(INK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, Math.round(size * 0.68f))));
        FontMetrics metrics = graphics.getFontMetrics();
        String letter = "R";
        int x = (size - metrics.stringWidth(letter)) / 2;
        int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(letter, x, y);
        graphics.dispose();
        return image;
    }
}
