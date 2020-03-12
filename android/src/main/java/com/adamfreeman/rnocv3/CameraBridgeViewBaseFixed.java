package com.adamfreeman.rnocv3;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;

import java.util.List;

public abstract class CameraBridgeViewBaseFixed extends CameraBridgeViewBase implements SurfaceHolder.Callback  {
    private static final int MAX_UNSPECIFIED = -1;

    private static final String TAG = "CameraBridge";

    private Bitmap mCacheBitmap;
    private CvCameraViewListener2Fixed mListener;

    private final Matrix mMatrix = new Matrix();

    public CameraBridgeViewBaseFixed(Context context, int cameraId) {
        super(context, cameraId);
    }

    public CameraBridgeViewBaseFixed(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private void updateMatrix() {
        float mw = this.getWidth();
        float mh = this.getHeight();

        float hw = this.getWidth() / 2.0f;
        float hh = this.getHeight() / 2.0f;

        float cw  = (float) Resources.getSystem().getDisplayMetrics().widthPixels; //Make sure to import Resources package
        float ch  = (float)Resources.getSystem().getDisplayMetrics().heightPixels;

        float scale = cw / (float)mh;
        float scale2 = ch / (float)mw;
        if(scale2 > scale){
            scale = scale2;
        }

        boolean isFrontCamera = mCameraIndex == CAMERA_ID_FRONT;

        mMatrix.reset();
        if (isFrontCamera) {
            mMatrix.preScale(-1, 1, hw, hh); //MH - this will mirror the camera
        }
        mMatrix.preTranslate(hw, hh);
        if (isFrontCamera){
            mMatrix.preRotate(270);
        } else {
            mMatrix.preRotate(90);
        }
        mMatrix.preTranslate(-hw, -hh);
        mMatrix.preScale(scale,scale,hw,hh);
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        updateMatrix();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateMatrix();
    }

    public void setCvCameraViewListener(CvCameraViewListener2Fixed listener) {
        mListener = listener;
    }

    /**
     * This method shall be called by the subclasses when they have valid
     * object and want it to be delivered to external client (via callback) and
     * then displayed on the screen.
     * @param frame - the current frame to be delivered
     */
    protected void deliverAndDrawFrame(CameraBridgeViewBaseFixed.CvCameraViewFrame frame) { //replaces existing deliverAndDrawFrame
        Mat modified;

        if (mListener != null) {
            modified = mListener.onCameraFrame(frame);
        } else {
            modified = frame.rgba();
        }

        boolean bmpValid = true;
        if (modified != null) {
            try {
                Utils.matToBitmap(modified, mCacheBitmap);
            } catch(Exception e) {
                Log.e(TAG, "Mat type: " + modified);
                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }

        if (bmpValid && mCacheBitmap != null) {
            Canvas canvas = getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                int saveCount = canvas.save();
                canvas.setMatrix(mMatrix);

                if (mScale != 0) {
                    canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
                            new Rect((int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2),
                                    (int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2),
                                    (int)((canvas.getWidth() - mScale*mCacheBitmap.getWidth()) / 2 + mScale*mCacheBitmap.getWidth()),
                                    (int)((canvas.getHeight() - mScale*mCacheBitmap.getHeight()) / 2 + mScale*mCacheBitmap.getHeight())), null);
                } else {
                    canvas.drawBitmap(mCacheBitmap, new Rect(0,0,mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
                            new Rect((canvas.getWidth() - mCacheBitmap.getWidth()) / 2,
                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2,
                                    (canvas.getWidth() - mCacheBitmap.getWidth()) / 2 + mCacheBitmap.getWidth(),
                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2 + mCacheBitmap.getHeight()), null);
                }

                //Restore canvas after draw bitmap
                canvas.restoreToCount(saveCount);

                if (mFpsMeter != null) {
                    mFpsMeter.measure();
                    mFpsMeter.draw(canvas, 20, 30);
                }
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    protected class CvCameraViewListenerAdapter implements CvCameraViewListener2Fixed  {
        public CvCameraViewListenerAdapter(CvCameraViewListener oldStypeListener) {
            mOldStyleListener = oldStypeListener;
        }

        public void onCameraViewStarted(int width, int height) {
            mOldStyleListener.onCameraViewStarted(width, height);
        }

        public void onCameraViewStopped() {
            mOldStyleListener.onCameraViewStopped();
        }

        public Mat onCameraFrame(CameraBridgeViewBaseFixed.CvCameraViewFrame inputFrame) {
            Mat result = null;
            switch (mPreviewFormat) {
                case RGBA:
                    result = mOldStyleListener.onCameraFrame(inputFrame.rgba());
                    break;
                case GRAY:
                    result = mOldStyleListener.onCameraFrame(inputFrame.gray());
                    break;
                default:
                    Log.e(TAG, "Invalid frame format! Only RGBA and Gray Scale are supported!");
            };

            return result;
        }

        public void setFrameFormat(int format) {
            mPreviewFormat = format;
        }

        private int mPreviewFormat = RGBA;
        private CvCameraViewListener mOldStyleListener;
    };

    // NOTE: On Android 4.1.x the function must be called before SurfaceTexture constructor!
    /**
     * This method is invoked shall perform concrete operation to initialize the camera.
     * CONTRACT: as a result of this method variables mFrameWidth and mFrameHeight MUST be
     * initialized with the size of the Camera frames that will be delivered to external processor.
     * @param width - the width of this SurfaceView
     * @param height - the height of this SurfaceView
     */
    protected abstract boolean connectCamera(int width, int height);

    /**
     * Disconnects and release the particular camera object being connected to this surface view.
     * Called when syncObject lock is held
     */
    protected abstract void disconnectCamera();

    // NOTE: On Android 4.1.x the function must be called before SurfaceTexture constructor!
    protected void AllocateCache()
    {
        mCacheBitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);
    }

    public interface ListItemAccessor {
        public int getWidth(Object obj);
        public int getHeight(Object obj);
    };

    /**
     * This helper method can be called by subclasses to select camera preview size.
     * It goes over the list of the supported preview sizes and selects the maximum one which
     * fits both values set via setMaxFrameSize() and surface frame allocated for this view
     * @param supportedSizes
     * @param surfaceWidth
     * @param surfaceHeight
     * @return optimal frame size
     */
    protected Size calculateCameraFrameSize(List<?> supportedSizes, CameraBridgeViewBaseFixed.ListItemAccessor accessor, int surfaceWidth, int surfaceHeight) {
        int calcWidth = 0;
        int calcHeight = 0;

        int maxAllowedWidth = (mMaxWidth != MAX_UNSPECIFIED && mMaxWidth < surfaceWidth)? mMaxWidth : surfaceWidth;
        int maxAllowedHeight = (mMaxHeight != MAX_UNSPECIFIED && mMaxHeight < surfaceHeight)? mMaxHeight : surfaceHeight;

        for (Object size : supportedSizes) {
            int width = accessor.getWidth(size);
            int height = accessor.getHeight(size);

            if (width <= maxAllowedWidth && height <= maxAllowedHeight) {
                if (width >= calcWidth && height >= calcHeight) {
                    calcWidth = (int) width;
                    calcHeight = (int) height;
                }
            }
        }

        return new Size(calcWidth, calcHeight);
    }
}
