/*
 * Button sprite definitions adapted from RuneProfile ManualUpdateButtonManager.java
 * at 2da51cd7a8dcf6a5ed0e827a2df2bb985d0e0550. Layout/collision handling modified.
 * See THIRD_PARTY_NOTICES.md for source and distribution details.
 * BSD 2-Clause License
 * 
 * Copyright (c) 2022, Reinhardt Rijna
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package app.runefolio.sync;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Singleton
final class RuneFolioCollectionLogButton
{
    private static final int COLLECTION_LOG_SETUP_SCRIPT = 7797;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_HEIGHT = 30;
    private static final int BUTTON_RIGHT_OFFSET = 18;
    private static final int BUTTON_BOTTOM_OFFSET = 12;
    private static final int LOGO_WIDTH = 18;
    private static final int COLLISION_PADDING = 3;
    private static final int CORNER_SIZE = 9;
    private static final int LOGO_TEXT = 0xd9b861;
    private static final int INACTIVE_TEXT = 0xd6d6d6;
    private static final int ACTIVE_TEXT = 0xffffff;
    private static final int[] INACTIVE_SPRITES = {
        SpriteID.TRADEBACKING,
        SpriteID.V2StoneButtonOut.A_TOP_LEFT,
        SpriteID.V2StoneButtonOut.A_TOP_RIGHT,
        SpriteID.V2StoneButtonOut.A_BOTTOM_LEFT,
        SpriteID.V2StoneButtonOut.A_BOTTOM_RIGHT,
        SpriteID.V2StoneButtonOut.A_MAP_EDGE_LEFT,
        SpriteID.V2StoneButtonOut.A_MAP_EDGE_TOP,
        SpriteID.V2StoneButtonOut.A_MAP_EDGE_RIGHT,
        SpriteID.V2StoneButtonOut.A_MAP_EDGE_BOTTOM,
    };
    private static final int[] ACTIVE_SPRITES = {
        SpriteID.TRADEBACKING_DARK,
        SpriteID.V2StoneButtonIn.A_TOP_LEFT,
        SpriteID.V2StoneButtonIn.A_TOP_RIGHT,
        SpriteID.V2StoneButtonIn.A_BOTTOM_LEFT,
        SpriteID.V2StoneButtonIn.A_BOTTOM_RIGHT,
        SpriteID.V2StoneButtonIn.A_LEFT,
        SpriteID.V2StoneButtonIn.A_TOP,
        SpriteID.V2StoneButtonIn.A_RIGHT,
        SpriteID.V2StoneButtonIn.A_BOTTOM,
    };

    private final Client client;
    private final ClientThread clientThread;
    private final EventBus eventBus;
    private final RuneFolioConfig config;
    private final List<Widget> buttonParts = new ArrayList<>();
    private Widget buttonParent;
    private Runnable syncAction;

    @Inject
    RuneFolioCollectionLogButton(
        Client client,
        ClientThread clientThread,
        EventBus eventBus,
        RuneFolioConfig config
    )
    {
        this.client = client;
        this.clientThread = clientThread;
        this.eventBus = eventBus;
        this.config = config;
    }

    void startUp(Runnable action)
    {
        syncAction = action;
        eventBus.register(this);
        clientThread.invokeLater(this::setupButton);
    }

    void shutDown()
    {
        eventBus.unregister(this);
        clientThread.invokeLater(this::hideButton);
        syncAction = null;
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.COLLECTION)
        {
            clientThread.invokeLater(() -> clientThread.invokeLater(this::setupButton));
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event)
    {
        if (event.getScriptId() == COLLECTION_LOG_SETUP_SCRIPT)
        {
            clientThread.invokeLater(this::setupButton);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        refreshButtonVisibility();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if ("runefolio".equals(event.getGroup()) && "showCollectionLogSyncButton".equals(event.getKey()))
        {
            clientThread.invokeLater(this::setupButton);
        }
    }

    private void setupButton()
    {
        hideButton();
        if (!config.showCollectionLogSyncButton())
        {
            return;
        }

        Widget parent = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
        if (parent == null || parent.isHidden())
        {
            return;
        }

        buttonParent = parent;
        int yMode = WidgetPositionMode.ABSOLUTE_BOTTOM;
        Widget[] sprites = new Widget[INACTIVE_SPRITES.length];

        sprites[0] = graphic(parent, INACTIVE_SPRITES[0], BUTTON_RIGHT_OFFSET, BUTTON_BOTTOM_OFFSET, BUTTON_WIDTH, BUTTON_HEIGHT, yMode);
        sprites[1] = graphic(parent, INACTIVE_SPRITES[1], BUTTON_RIGHT_OFFSET + BUTTON_WIDTH - CORNER_SIZE, BUTTON_BOTTOM_OFFSET + BUTTON_HEIGHT - CORNER_SIZE, CORNER_SIZE, CORNER_SIZE, yMode);
        sprites[2] = graphic(parent, INACTIVE_SPRITES[2], BUTTON_RIGHT_OFFSET, BUTTON_BOTTOM_OFFSET + BUTTON_HEIGHT - CORNER_SIZE, CORNER_SIZE, CORNER_SIZE, yMode);
        sprites[3] = graphic(parent, INACTIVE_SPRITES[3], BUTTON_RIGHT_OFFSET + BUTTON_WIDTH - CORNER_SIZE, BUTTON_BOTTOM_OFFSET, CORNER_SIZE, CORNER_SIZE, yMode);
        sprites[4] = graphic(parent, INACTIVE_SPRITES[4], BUTTON_RIGHT_OFFSET, BUTTON_BOTTOM_OFFSET, CORNER_SIZE, CORNER_SIZE, yMode);
        sprites[5] = graphic(parent, INACTIVE_SPRITES[5], BUTTON_RIGHT_OFFSET + BUTTON_WIDTH - CORNER_SIZE, BUTTON_BOTTOM_OFFSET + CORNER_SIZE, CORNER_SIZE, BUTTON_HEIGHT - 2 * CORNER_SIZE, yMode);
        sprites[6] = graphic(parent, INACTIVE_SPRITES[6], BUTTON_RIGHT_OFFSET + CORNER_SIZE, BUTTON_BOTTOM_OFFSET + BUTTON_HEIGHT - CORNER_SIZE, BUTTON_WIDTH - 2 * CORNER_SIZE, CORNER_SIZE, yMode);
        sprites[7] = graphic(parent, INACTIVE_SPRITES[7], BUTTON_RIGHT_OFFSET, BUTTON_BOTTOM_OFFSET + CORNER_SIZE, CORNER_SIZE, BUTTON_HEIGHT - 2 * CORNER_SIZE, yMode);
        sprites[8] = graphic(parent, INACTIVE_SPRITES[8], BUTTON_RIGHT_OFFSET + CORNER_SIZE, BUTTON_BOTTOM_OFFSET, BUTTON_WIDTH - 2 * CORNER_SIZE, CORNER_SIZE, yMode);

        Widget logo = parent.createChild(-1, WidgetType.TEXT)
            .setText("R")
            .setTextColor(LOGO_TEXT)
            .setFontId(FontID.BOLD_12)
            .setTextShadowed(true)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setYPositionMode(yMode)
            .setXTextAlignment(WidgetTextAlignment.CENTER)
            .setYTextAlignment(WidgetTextAlignment.CENTER)
            .setPos(BUTTON_RIGHT_OFFSET + BUTTON_WIDTH - LOGO_WIDTH - 5, BUTTON_BOTTOM_OFFSET)
            .setSize(LOGO_WIDTH, BUTTON_HEIGHT);
        logo.revalidate();
        buttonParts.add(logo);

        Widget label = parent.createChild(-1, WidgetType.TEXT)
            .setText("RuneFolio")
            .setTextColor(INACTIVE_TEXT)
            .setFontId(FontID.PLAIN_11)
            .setTextShadowed(true)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setYPositionMode(yMode)
            .setXTextAlignment(WidgetTextAlignment.CENTER)
            .setYTextAlignment(WidgetTextAlignment.CENTER)
            .setPos(BUTTON_RIGHT_OFFSET + 2, BUTTON_BOTTOM_OFFSET)
            .setSize(BUTTON_WIDTH - LOGO_WIDTH - 4, BUTTON_HEIGHT);
        label.revalidate();
        buttonParts.add(label);

        Widget hitbox = parent.createChild(-1, WidgetType.TEXT)
            .setText("")
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setYPositionMode(yMode)
            .setPos(BUTTON_RIGHT_OFFSET, BUTTON_BOTTOM_OFFSET)
            .setSize(BUTTON_WIDTH, BUTTON_HEIGHT)
            .setHasListener(true);
        hitbox.setAction(0, "Sync Collection Log with RuneFolio");
        hitbox.setOnOpListener((JavaScriptCallback) event ->
        {
            if (syncAction != null)
            {
                syncAction.run();
            }
        });
        hitbox.setOnMouseOverListener((JavaScriptCallback) event -> setButtonState(sprites, label, true));
        hitbox.setOnMouseLeaveListener((JavaScriptCallback) event -> setButtonState(sprites, label, false));
        hitbox.revalidate();
        buttonParts.add(hitbox);
        parent.revalidate();
        refreshButtonVisibility();
    }

    private Widget graphic(Widget parent, int spriteId, int x, int y, int width, int height, int yMode)
    {
        Widget widget = parent.createChild(-1, WidgetType.GRAPHIC)
            .setSpriteId(spriteId)
            .setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT)
            .setYPositionMode(yMode)
            .setPos(x, y)
            .setSize(width, height);
        widget.revalidate();
        buttonParts.add(widget);
        return widget;
    }

    private void setButtonState(Widget[] sprites, Widget text, boolean active)
    {
        int[] spriteIds = active ? ACTIVE_SPRITES : INACTIVE_SPRITES;
        for (int index = 0; index < sprites.length; index++)
        {
            sprites[index].setSpriteId(spriteIds[index]);
        }
        text.setTextColor(active ? ACTIVE_TEXT : INACTIVE_TEXT);
    }

    private void refreshButtonVisibility()
    {
        if (buttonParent == null || buttonParts.isEmpty())
        {
            return;
        }

        try
        {
            Rectangle parentBounds = buttonParent.getBounds();
            boolean hidden = buttonParent.isHidden() || parentBounds == null || parentBounds.isEmpty();
            if (!hidden)
            {
                Rectangle buttonBounds = new Rectangle(
                    parentBounds.x + parentBounds.width - BUTTON_RIGHT_OFFSET - BUTTON_WIDTH - COLLISION_PADDING,
                    parentBounds.y + parentBounds.height - BUTTON_BOTTOM_OFFSET - BUTTON_HEIGHT - COLLISION_PADDING,
                    BUTTON_WIDTH + 2 * COLLISION_PADDING,
                    BUTTON_HEIGHT + 2 * COLLISION_PADDING
                );
                Widget[] children = buttonParent.getChildren();
                if (children != null)
                {
                    for (Widget child : children)
                    {
                        if (child == null || child.isHidden() || buttonParts.contains(child) || child.getItemId() <= 0)
                        {
                            continue;
                        }

                        Rectangle itemBounds = child.getBounds();
                        if (itemBounds != null && !itemBounds.isEmpty() && buttonBounds.intersects(itemBounds))
                        {
                            hidden = true;
                            break;
                        }
                    }
                }
            }

            for (Widget part : buttonParts)
            {
                part.setHidden(hidden);
            }
        }
        catch (RuntimeException ignored)
        {
            // Closing or rebuilding the Collection Log invalidates its dynamic children.
            buttonParts.clear();
            buttonParent = null;
        }
    }

    private void hideButton()
    {
        for (Widget part : buttonParts)
        {
            try
            {
                part.setHidden(true);
            }
            catch (RuntimeException ignored)
            {
                // The Collection Log interface may have closed before cleanup runs.
            }
        }
        buttonParts.clear();
        buttonParent = null;
    }
}

