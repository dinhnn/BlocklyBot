package edu.umich.eecs.april.apriltag;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.tharvey.blocklybot.LocationListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
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
    private LocationListener listener;
    String TAG = "TagView";
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
    Camera mCamera;
    private Executor executor;
    private SurfaceView overlay;
    private int worldWidth=1;
    private int worldHeight=1;
    public TagView(Context context,LocationListener listener) {
        super(context);
        this.listener = listener;
        getHolder().addCallback(this);
        setSecure(true);
        //getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        overlay = new SurfaceView (context);/*{
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
        };*/
        overlay.setZOrderMediaOverlay(true);
        overlay.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        overlay.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                executor = Executors.newSingleThreadExecutor();
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                executor = null;
            }
        });
    }
    public void setWorldSize(int worldWidth,int worldHeight){
        COORDINATES[2] = COORDINATES[4] = this.worldWidth = worldWidth;
        COORDINATES[5] = COORDINATES[7] = this.worldHeight = worldHeight;
        this.objX = this.worldWidth/3;
        this.objY = this.worldHeight/3;
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
            //executor = Executors.newSingleThreadExecutor();

        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        //executor = null;
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
    float x0,y0,x1,y1;
    double objX,objY,objDir;
    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        if(executor==null){
            camera.addCallbackBuffer(bytes);
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final float scaleX = (float)getWidth()/mPreviewSize.width;
                final float scaleY = (float)getHeight()/mPreviewSize.height;
                mDetections = ApriltagNative.apriltag_detect_yuv(bytes, mPreviewSize.width,mPreviewSize.height);
                int size = mDetections.size();
                if(size>4) {
                    float[] coordinates = new float[24];
                    ApriltagDetection obj=null;
                    for (ApriltagDetection d : mDetections) {
                        int ofs = d.id*4;
                        coordinates[ofs] = (float)d.c[0];
                        ofs++;
                        coordinates[ofs] = (float)d.c[1];
                        ofs++;
                        if(d.id<4){
                            coordinates[ofs] = COORDINATES[d.id*2];
                            ofs++;
                            coordinates[ofs] = COORDINATES[d.id*2+1];
                            ofs++;
                        } else {
                            obj = d;
                            coordinates[ofs] = -1;
                            ofs++;
                            coordinates[ofs] = -1;
                            ofs++;
                            coordinates[ofs] = (float)d.p[0];
                            ofs++;
                            coordinates[ofs] = (float)d.p[1];
                            ofs++;
                            coordinates[ofs] = -1;
                            ofs++;
                            coordinates[ofs] = -1;
                        }
                    }

                    if (obj!=null && ApriltagNative.homography_project(coordinates)) {
                        int ofs = obj.id*4+2;
                        objX = coordinates[ofs];
                        ofs++;
                        objY = coordinates[ofs];
                        ofs+=3;
                        float x0 = coordinates[ofs];
                        ofs++;
                        float y0 = coordinates[ofs];
                        listener.onLocationChanged(objX,objX,objDir = (float)Math.atan2(x0-objX,y0-objY));
                    }
                }
                SurfaceHolder sh = overlay.getHolder();
                Canvas canvas = sh.lockCanvas(null);
                synchronized (sh){
                    canvas.drawColor(0, PorterDuff.Mode.CLEAR);
                    Paint paint = new Paint();
                    paint.setColor(Color.GREEN);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3);
                    float width = getWidth();
                    float height = getHeight();
                    float cell = Math.min(width/worldWidth,height/worldHeight);
                    x0 = (width-cell*worldWidth)/2;
                    y0 = (height-cell*worldHeight)/2;
                    x1 = x0+cell*worldWidth;
                    y1 = y0+cell*worldHeight;
                    float x = x0;
                    for(int i = 0;i<=worldWidth;i++){
                        canvas.drawLine(x,y0,x,y1, paint);
                        x+=cell;
                    }
                    float y = y0;
                    for(int i = 0;i<=worldWidth;i++){
                        canvas.drawLine(x0,y,x1,y, paint);
                        y+=cell;
                    }
                    paint.setColor(Color.BLUE);
                    for (ApriltagDetection det : mDetections) {
                        float x0 = (float)det.p[0]*scaleX;
                        float y0 = (float)det.p[1]*scaleY;
                        float x1 = (float)det.p[2]*scaleX;
                        float y1 = (float)det.p[3]*scaleY;
                        float x2 = (float)det.p[4]*scaleX;
                        float y2 = (float)det.p[5]*scaleY;
                        float x3 = (float)det.p[6]*scaleX;
                        float y3 = (float)det.p[7]*scaleY;
                        canvas.drawLine(x0,y0,x1,y1,paint);
                        canvas.drawLine(x1,y1,x2,y2,paint);
                        canvas.drawLine(x2,y2,x3,y3,paint);
                        canvas.drawLine(x3,y3,x0,y0,paint);
                    }
                    float ox = (float)(x0+objX*cell)*scaleX;
                    float oy = (float)(y1-objY*cell)*scaleY;
                    paint.setColor(Color.RED);
                    canvas.drawLine(ox,oy,ox+(float)Math.sin(objDir)*cell,oy-(float)Math.cos(objDir)*cell,paint);
                }
                sh.unlockCanvasAndPost(canvas);
                camera.addCallbackBuffer(bytes);
            }
        });
    }

    public View getOverlayView() {
        return overlay;
    }
}
