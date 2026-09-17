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
    private static final Color DISCORD_BLURPLE = new Color(88, 101, 242);
    private static final Color GITHUB_INK = new Color(36, 41, 46);
    private static final Color WHITE = new Color(240, 240, 240);

    private RuneFolioBrand()
    {
    }

    static BufferedImage createIcon(int requestedSize)
    {
        return createBadge(requestedSize, GOLD, INK, "R", 0.68f);
    }

    // A generic rounded-square link badge, not the actual Discord/GitHub marks:
    // this plugin bundles no external artwork or third-party brand assets.
    static BufferedImage createDiscordIcon(int requestedSize)
    {
        return createBadge(requestedSize, DISCORD_BLURPLE, WHITE, "D", 0.62f);
    }

    static BufferedImage createGithubIcon(int requestedSize)
    {
        return createBadge(requestedSize, GITHUB_INK, WHITE, "GH", 0.44f);
    }

    private static BufferedImage createBadge(int requestedSize, Color background, Color foreground, String glyph, float glyphScale)
    {
        int size = Math.max(12, requestedSize);
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(background);
        int inset = Math.max(1, size / 16);
        int arc = Math.max(4, size / 4);
        graphics.fillRoundRect(inset, inset, size - inset * 2, size - inset * 2, arc, arc);

        graphics.setColor(foreground);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, Math.round(size * glyphScale))));
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (size - metrics.stringWidth(glyph)) / 2;
        int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(glyph, x, y);
        graphics.dispose();
        return image;
    }
}
