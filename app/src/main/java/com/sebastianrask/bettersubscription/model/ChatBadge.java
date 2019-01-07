package com.sebastianrask.bettersubscription.model;

import android.graphics.Bitmap;

public class ChatBadge {

    private Bitmap badgeBitmap;

    public ChatBadge(Bitmap bitmap) {
        this.badgeBitmap = bitmap;
    }

    public Bitmap getBitmap() {
        return badgeBitmap;
    }
}
