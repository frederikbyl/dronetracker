package com.wasp2.video;

import android.graphics.Bitmap;
import android.graphics.Rect;

public interface ImageReadyCallback {

    void drawImage(Bitmap bitmap, Rect rect);
}
