package edu.umich.eecs.april.apriltag;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Draws camera images onto a GLSurfaceView and tag mDetections onto a custom overlay surface.
 */
public class TagView extends SurfaceView implements Camera.PreviewCallback,SurfaceHolder.Callback {
    private static final float[] COORDINATES = new float[]{0,0,1,0,1,1,0,1};
    private List<ApriltagDetection> mDetections= Collections.emptyList();

    String TAG = "TagView";
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    private Executor executor;
    private View overlay;
    public TagView(Context context) {
        super(context);
        this.overlay = overlay;
        getHolder().addCallback(this);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        overlay = new View(context){
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                canvas.drawLine(0,0,100,100, paint);

                for (ApriltagDetection det : mDetections) {
                    float[] points = new float[8];
                    for (int i = 0; i < 4; i += 1) {
                        //double x = 0.5 - (det.p[2*i + 1] / mPreviewSize.height);
                        //double y = 0.5 - (det.p[2*i + 0] / mPreviewSize.width);
                        points[2*i + 0] = (float)det.p[2*i + 1];
                        points[2*i + 1] = (float)det.p[2*i + 0];
                    }
                    canvas.drawLines(points,paint);
                }
            }
        };
    }

    public void setCamera(Camera camera) {
        mCamera = camera;
        if (mCamera != null) {
            mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
            requestLayout();

            // get Camera parameters
            Camera.Parameters params = mCamera.getParameters();

            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                // set the focus mode
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                // set Camera parameters
                mCamera.setParameters(params);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);

        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
    }



    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mCamera.setPreviewDisplay(holder);
            }
            executor = Executors.newSingleThreadExecutor();

        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        executor = null;
    }


    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }
    private ByteBuffer mYuvBuffer;
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if(mCamera != null) {
            mCamera.stopPreview();
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            requestLayout();
            int nbytes = mPreviewSize.width * mPreviewSize.height * 3 / 2; // XXX: What's the 3/2 scaling for?
            if (mYuvBuffer == null || mYuvBuffer.capacity() < nbytes) {
                // Allocate direct byte buffer so native code access won't require a copy
                Log.i(TAG, "Allocating buf of mPreviewSize " + nbytes);
                mYuvBuffer = ByteBuffer.allocateDirect(nbytes);
            }

            // Scale the rectangle on which the image texture is drawn
            // Here, the image is displayed without rescaling
            //Matrix.setIdentityM(mRenderer.M, 0);
            //Matrix.scaleM(mRenderer.M, 0, mPreviewSize.height, mPreviewSize.width, 1.0f);
            mCamera.addCallbackBuffer(mYuvBuffer.array());
            mCamera.setParameters(parameters);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        }
    }

    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if(executor==null){
            camera.addCallbackBuffer(bytes);
            return;
        }
        final int width = mPreviewSize.width;
        final int height = mPreviewSize.height;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                mDetections = ApriltagNative.apriltag_detect_yuv(bytes, width, height);
                float[] coordinates = new float[mDetections.size()*4];
                int i = 0;
                for(ApriltagDetection d:mDetections){
                    coordinates[i] = (float)d.c[0];
                    i++;
                    coordinates[i] = (float)d.c[1];
                    i++;
                    if(d.id<4){
                        coordinates[i] = COORDINATES[d.id*2];
                        i++;
                        coordinates[i] = COORDINATES[d.id*2+1];
                        i++;
                    } else {
                        coordinates[i] = -1;
                        i++;
                        coordinates[i] = -1;
                        i++;
                    }
                }

                if(ApriltagNative.homography_project(coordinates)){
                    i = 0;
                    for(ApriltagDetection d:mDetections){

                        i+=2;
                        final float x = coordinates[i];
                        i++;
                        final float y = coordinates[i];
                        i++;
                        if(d.id>=0){
                            Log.i(TAG,"Object position:"+x+"x"+y);
                        }
                    }
                }
                ((Activity)getContext()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlay.invalidate();
                    }
                });
            }
        });
    }

    public View getOverlayView() {
        return overlay;
    }
}
