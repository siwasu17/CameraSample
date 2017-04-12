package com.game.siwasu17.camerasample;

import android.graphics.SurfaceTexture;
import android.util.Size;
import android.view.Surface;

import android.os.Handler;

public interface CameraInterface {
    SurfaceTexture getSurfaceTextureFromTextureView();
    Size getPreviewSize();
    Handler getBackgroundHandler();
    Surface getImageRenderSurface();
    int getRotation();
}
