package app.runefolio.sync;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
final class RuneFolioDiarySyncOverlay extends Overlay
{
    private static final int HEIGHT = 28;
    private static final int WIDTH = 380;
    private static final long SUCCESS_DURATION_MILLIS = 4_000L;
    private static final long FAILURE_DURATION_MILLIS = 6_000L;
    private static final Color BACKGROUND = new Color(18, 18, 15, 225);
    private static final Color BORDER = new Color(217, 184, 97, 210);
    private static final Color SYNCING_TEXT = new Color(236, 221, 180);
    private static final Color SUCCESS_TEXT = new Color(100, 205, 88);
    private static final Color FAILURE_TEXT = new Color(214, 121, 102);

    private volatile String message;
    private volatile Color textColor = SYNCING_TEXT;
    private volatile long visibleUntil = Long.MAX_VALUE;

    @Inject
    RuneFolioDiarySyncOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    void showSyncing(Point location, String areaName)
    {
        setPreferredLocation(location);
        message = "Syncing " + areaName + " diary with RuneFolio...";
        textColor = SYNCING_TEXT;
        visibleUntil = Long.MAX_VALUE;
    }

    void showSuccess(String areaName)
    {
        message = areaName + " diary has been synced successfully";
        textColor = SUCCESS_TEXT;
        visibleUntil = System.currentTimeMillis() + SUCCESS_DURATION_MILLIS;
    }

    void showFailure(String areaName)
    {
        message = areaName + " diary could not be synced yet";
        textColor = FAILURE_TEXT;
        visibleUntil = System.currentTimeMillis() + FAILURE_DURATION_MILLIS;
    }

    void clear()
    {
        message = null;
        visibleUntil = 0L;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        String currentMessage = message;
        if (currentMessage == null || System.currentTimeMillis() > visibleUntil)
        {
            message = null;
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.setColor(BACKGROUND);
        graphics.fillRoundRect(0, 0, WIDTH, HEIGHT, 8, 8);
        graphics.setColor(BORDER);
        graphics.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 8, 8);
        graphics.setColor(textColor);
        int textX = Math.max(8, (WIDTH - metrics.stringWidth(currentMessage)) / 2);
        graphics.drawString(currentMessage, textX, (HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent());
        return new Dimension(WIDTH, HEIGHT);
    }
}
