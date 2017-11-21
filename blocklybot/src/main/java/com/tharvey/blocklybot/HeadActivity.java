package com.tharvey.blocklybot;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;
import com.tharvey.blocklybot.vison.FaceGraphic;
import com.tharvey.blocklybot.vison.camera.CameraSourcePreview;
import com.tharvey.blocklybot.vison.camera.GraphicOverlay;

import java.io.IOException;

public class HeadActivity extends RobotActivity implements SensorEventListener {
    private static final String TAG = "ROBOT_HEAD";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.display);
        faceState = 0;
        arrowView = (ImageView)findViewById(R.id.compass);
        compass = new Compass(this);

        compass.setListener(new Compass.CompassListener() {
            @Override
            public void onChange(final float azimuth) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        float to = -azimuth-45;
                        Animation an = new RotateAnimation(currectAzimuth,to,
                                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                                0.5f);
                        currectAzimuth = to;
                        an.setDuration(500);
                        an.setRepeatCount(0);
                        an.setFillAfter(true);

                        arrowView.startAnimation(an);
                    }
                });

//				if (mEventListener != null)
//					mEventListener.onEvent("azimuth", Float.toString(azimuth));
            }
        });




        mSpeaking = false;

        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

        webview = (WebView) findViewById(R.id.faceId);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setLoadWithOverviewMode(true);
        webview.getSettings().setUseWideViewPort(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.addJavascriptInterface(this,"robot");
        webview.loadUrl("file:///android_asset/face/index.html");
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        FaceDetector detector = new FaceDetector.Builder(this)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setTrackingEnabled(true)
                .build();

        detector.setProcessor(
                new LargestFaceFocusingProcessor.Builder(detector,new GraphicFaceTracker(mGraphicOverlay))
                        .build());

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(this, detector)
                .setRequestedPreviewSize(640, 480)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedFps(30.0f)
                .build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        compass.start();
        startCameraSource();
        SensorManager mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        actived = true;
        mSensorManager.registerListener(this, mSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    private CameraSource mCameraSource = null;

    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;

    private ImageView arrowView = null;
    private boolean mSpeaking;
    private Compass compass;
    private float currectAzimuth = 0;
    private WebView webview;
    private int faceState;
    private final static String [] emojs= new String[]{"standardFace","winkFace","happyFace","ragingFace"};


    private boolean onEvent(String elem, MotionEvent event) {
        Log.i(TAG, "onEvent: touch " + elem);
        if (mEventListener != null)
            mEventListener.onEvent("touch", elem);
        return true;
    }

    public void showFace(String style) {


    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    boolean actived;
    long lastProximity = 0;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] == 0) {
            if(System.currentTimeMillis()<lastProximity+2000)return;
            lastProximity = System.currentTimeMillis();
            if(actived){
                if(mEventListener!=null){
                    mEventListener.onEvent("robot","sleep");
                }
                mPreview.stop();
                PowerManager manager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
                manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "tag").acquire();
            } else {
                if(mEventListener!=null){
                    mEventListener.onEvent("robot","weakup");
                }
                mPreview.resume();
                PowerManager manager = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
                manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag").release();
            }
            actived = !actived;
        }
    }
    @JavascriptInterface
    public void onFaceClicked(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                faceState = (faceState+1)%emojs.length;
                webview.evaluateJavascript("window."+emojs[faceState]+"();",null);
                onEvent("face", null);
            }
        });
    }
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, 9001);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource,mGraphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }
    public void setEmotion(final int emotion){
        runOnUiThread(new Runnable() {
            public void run() {
                webview.evaluateJavascript("window."+emojs[emotion]+"();",null);
            }
        });
    }
    public void setSpeaking(boolean speaking) {
        mSpeaking = speaking;
        if (speaking) {
            Log.i(TAG, "speaking");
            runOnUiThread(new Runnable() {
                public void run() {
                    //TODO
                }
            });
        } else {
            Log.i(TAG, "done speaking");
            runOnUiThread(new Runnable() {
                public void run() {
                    //TODO
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "hideFace");
        if(compass!=null){
            compass.stop();
            compass = null;
        }

        mPreview.stop();
        if (mCameraSource != null) {
            mCameraSource.release();
            mCameraSource = null;
        }
        SensorManager mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.unregisterListener(this);
    }


    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }
        boolean smile = false;
        String facePos = null;
        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            if(face.getIsSmilingProbability()>0.68){
                if(!smile&&mEventListener != null) {
                    mEventListener.onEvent("face","smile");
                }
                smile = true;
            } else {
                smile = false;
            }
            String pos = mFaceGraphic.updateFace(face);
            if(facePos==null||!facePos.equals(pos)){
                facePos = pos;
                if(mEventListener != null) {
                    mEventListener.onEvent("face",pos);
                }
            }
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            smile = false;
            facePos = null;
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }
}
