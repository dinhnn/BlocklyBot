package com.tharvey.blocklybot;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Random;

import edu.umich.eecs.april.apriltag.ApriltagNative;
import edu.umich.eecs.april.apriltag.TagView;

public class RemoteControlActivity extends RobotActivity {
    private final static String TAG = "RemoteControl";
    private Camera camera;
    private TagView tagView;

    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 77;
    private int has_camera_permissions = 0;

    private void verifyPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        int nthreads = Integer.parseInt(sharedPreferences.getString("nthreads_value", "0"));
        if (nthreads <= 0) {
            int nproc = Runtime.getRuntime().availableProcessors();
            if (nproc <= 0) {
                nproc = 1;
            }
            Log.i(TAG, "available processors: " + nproc);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString("nthreads_value", Integer.toString(nproc)).apply();
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure we have permission to use the camera (Permission Requesting for Android 6.0/SDK 23 and higher)
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Assume user knows enough about the app to know why we need the camera, just ask for permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        } else {
            this.has_camera_permissions = 1;
        }

        setContentView(R.layout.activity_remote_control);
        JoystickView joystickRight = (JoystickView) findViewById(R.id.joystick);
        joystickRight.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(double angle, double strength) {
                joystick(angle,strength);
            }
        });
        tagView = new TagView(this);
        tagView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        tagView.setKeepScreenOn(true);
        View overlay = tagView.getOverlayView();
        overlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout layout = (FrameLayout) findViewById(R.id.remote_frame);
        layout.addView(tagView,0);
        layout.addView(overlay,1);
    }

    /** Release the camera when application focus is lost */
    protected void onPause() {
        super.onPause();
        //tagView.onPause();

        //Log.i(TAG, "Pause");
        // TODO move camera management to TagView class

        if (camera != null) {
            tagView.setCamera(null);
            camera.release();
            camera = null;
        }
    }

    /** (Re-)initialize the camera */
    protected void onResume() {
        super.onResume();
        //tagView.onResume();

        if (this.has_camera_permissions == 0) {
            return;
        }

        //Log.i(TAG, "Resume");

        // Re-initialize the Apriltag detector as settings may have changed
        verifyPreferences();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        double decimation = Double.parseDouble(sharedPreferences.getString("decimation_list", "4"));
        double sigma = Double.parseDouble(sharedPreferences.getString("sigma_value", "0"));
        int nthreads = Integer.parseInt(sharedPreferences.getString("nthreads_value", "0"));
        String tagFamily = sharedPreferences.getString("tag_family_list", "tag36h11");
        boolean useRear = (sharedPreferences.getString("device_settings_camera_facing", "1").equals("1")) ? true : false;
        Log.i(TAG, String.format("decimation: %f | sigma: %f | nthreads: %d | tagFamily: %s | useRear: %b",
                decimation, sigma, nthreads, tagFamily, useRear));
        ApriltagNative.apriltag_init(tagFamily, 2, decimation, sigma, nthreads);

        // Find the camera index of front or rear camera
        int camidx = 0;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i += 1) {
            Camera.getCameraInfo(i, info);
            int desiredFacing = useRear ? Camera.CameraInfo.CAMERA_FACING_BACK :
                    Camera.CameraInfo.CAMERA_FACING_FRONT;
            if (info.facing == desiredFacing) {
                camidx = i;
                break;
            }
        }
        //info.orientation=90;
        Camera.getCameraInfo(camidx, info);
        Log.i(TAG, "using camera " + camidx);
        Log.i(TAG, "camera rotation: " + info.orientation);
        try {
            camera = Camera.open(camidx);
        } catch (Exception e) {
            Log.d(TAG, "Couldn't open camera: " + e.getMessage());
            return;
        }
        Log.i(TAG, "supported resolutions:");
        Camera.Parameters params = camera.getParameters();
        for (Camera.Size s : params.getSupportedPreviewSizes()) {
            Log.i(TAG, " " + s.width + "x" + s.height);
        }
        //setCameraDisplayOrientation(this,info,camera);
        tagView.setCamera(camera);
    }
    public static void setCameraDisplayOrientation(Activity activity,
                                                   Camera.CameraInfo info , android.hardware.Camera camera) {
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "App GRANTED camera permissions");

                    // Set flag
                    this.has_camera_permissions = 1;
                    tagView = new TagView(this);
                    tagView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                    tagView.setKeepScreenOn(true);
                    View overlay = tagView.getOverlayView();
                    overlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
                    FrameLayout layout = (FrameLayout) findViewById(R.id.remote_frame);
                    layout.addView(tagView,0);
                    layout.addView(overlay,1);

                    // Restart the camera
                    onPause();
                    onResume();
                } else {
                    Log.i(TAG, "App DENIED camera permissions");
                    this.has_camera_permissions = 0;
                }
                return;
            }
        }
    }


    @Override
    public void setEmotion(int emotion) {

    }

    @Override
    public void setSpeaking(boolean speaking) {

    }

    @Override
    protected void control(int leftspeed, int rightspeed) {
        super.control(leftspeed, rightspeed);

    }
    private void joystick(double t, double r){
        t -= Math.PI / 4;
        double left = r * Math.cos(t);
        double right = r * Math.sin(t);
        left = left * Math.sqrt(2);
        right = right * Math.sqrt(2);
        left = Math.max(-1, Math.min(left, 1));
        right = Math.max(-1, Math.min(right, 1));
        control((int)(left*255),(int)(right*255));
    }
}
