package com.wasp2.video;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Parcel;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

public class MySurface extends Surface {


    public MySurface(SurfaceTexture surfaceTexture) {

        super(surfaceTexture);
        Log.i("MySurface", "SURFACE CREATED");
    }


    @Override
    protected void finalize() throws Throwable {
        Log.i("MySurface", "2");
        super.finalize();

    }

    @Override
    public void release() {
        Log.i("MySurface", "3");
        super.release();

    }

    @Override
    public boolean isValid() {
        Log.i("MySurface", "4");
        return super.isValid();

    }

    @Override
    public Canvas lockCanvas(Rect inOutDirty) throws IllegalArgumentException, OutOfResourcesException {
        Log.i("MySurface", "5");
        return super.lockCanvas(inOutDirty);
    }

    @Override
    public void unlockCanvasAndPost(Canvas canvas) {
        Log.i("MySurface", "6");
        super.unlockCanvasAndPost(canvas);
    }

    @Override
    public Canvas lockHardwareCanvas() {
        Log.i("MySurface", "7");
        return super.lockHardwareCanvas();
    }

    @Override
    public void unlockCanvas(Canvas canvas) {
        Log.i("MySurface", "8");
        super.unlockCanvas(canvas);
    }

    @Override
    public int describeContents() {
        Log.i("MySurface", "9");
        return super.describeContents();
    }

    @Override
    public void readFromParcel(Parcel source) {
        Log.i("MySurface", "10");
        super.readFromParcel(source);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Log.i("MySurface", "11");
        super.writeToParcel(dest, flags);
    }

    @Override
    public String toString() {
        Log.i("MySurface", "12");
        return super.toString();
    }
}
