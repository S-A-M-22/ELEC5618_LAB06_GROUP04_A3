/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2024 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.RecoveryManager;
import com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.StartScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.Game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Lists recovery saves (deleted game slots that were preserved by {@link RecoveryManager})
 * and lets the player restore one back into a game slot.
 */
public class WndRecoverySave extends Window {

    private static final int WIDTH      = 120;
    private static final int BTN_HEIGHT = 16;
    private static final int GAP        = 4;

    public WndRecoverySave() {

        IconTitle title = new IconTitle();
        title.icon(Icons.get(Icons.REPEAT));
        title.label(Messages.get(this, "title").toUpperCase(Locale.ENGLISH));
        title.color(Window.TITLE_COLOR);
        title.setRect(0, 0, WIDTH, 0);
        add(title);

        float pos = title.bottom() + GAP;

        RenderedTextBlock body = PixelScene.renderTextBlock(Messages.get(this, "body"), 6);
        body.maxWidth(WIDTH);
        body.setPos(0, pos);
        PixelScene.align(body);
        add(body);
        pos = body.bottom() + GAP + 2;

        // Show newest entries first.
        ArrayList<String> entries = RecoveryManager.listRecoveryEntries();
        Collections.reverse(entries);

        for (final String entryName : entries) {
            final int targetSlot = RecoveryManager.slotFromEntryName(entryName);
            if (targetSlot == -1) continue;

            long ts = timestampFromEntry(entryName);
            String age = ts > 0 ? formatAge(ts) : "?";
            String rowLabel = Messages.get(this, "slot", targetSlot) + "  ·  " + age;

            RenderedTextBlock slotText = PixelScene.renderTextBlock(rowLabel, 7);
            slotText.maxWidth(WIDTH - 44);
            slotText.setPos(1, pos + (BTN_HEIGHT - slotText.height()) / 2f);
            PixelScene.align(slotText);
            add(slotText);

            RedButton btnRestore = new RedButton(Messages.get(this, "restore")) {
                @Override
                protected void onClick() {
                    onRestoreClicked(entryName, targetSlot);
                }
            };
            btnRestore.setRect(WIDTH - 42, pos, 42, BTN_HEIGHT);
            add(btnRestore);

            pos = btnRestore.bottom() + GAP;
        }

        resize(WIDTH, (int) pos);
    }

    private void onRestoreClicked(final String entryName, final int targetSlot) {
        if (GamesInProgress.check(targetSlot) != null) {
            Game.scene().add(new WndOptions(
                    Icons.get(Icons.WARNING),
                    Messages.get(this, "confirm_title"),
                    Messages.get(this, "confirm_body", targetSlot),
                    Messages.get(this, "confirm_yes"),
                    Messages.get(this, "confirm_no")) {
                @Override
                protected void onSelect(int index) {
                    if (index == 0) doRestore(entryName, targetSlot);
                }
            });
        } else {
            doRestore(entryName, targetSlot);
        }
    }

    private void doRestore(String entryName, int targetSlot) {
        RecoveryManager.restoreEntry(entryName, targetSlot);
        ShatteredPixelDungeon.switchNoFade(StartScene.class);
    }

    private static long timestampFromEntry(String entryName) {
        String[] parts = entryName.split("_", 2);
        if (parts.length < 1) return 0;
        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatAge(long epochMs) {
        long diff = System.currentTimeMillis() - epochMs;
        if (diff < 60_000) {
            return Messages.get(this, "just_now");
        } else if (diff < 2L * 60 * 60_000) {
            return Messages.get(this, "minutes_ago", diff / 60_000);
        } else if (diff < 2L * 24 * 60 * 60_000) {
            return Messages.get(this, "hours_ago", diff / (60 * 60_000));
        } else {
            return Messages.get(this, "days_ago", diff / (24 * 60 * 60_000));
        }
    }
}
