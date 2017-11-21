/*
 * Copyright 2016 Tim Harvey <harvey.tim@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tharvey.blocklybot;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.PopupWindow;
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

public class Display implements SensorEventListener{
	private final static String TAG = Display.class.getSimpleName();

	private Activity mActivity;

	private CameraSource mCameraSource = null;

	private CameraSourcePreview mPreview;
	private GraphicOverlay mGraphicOverlay;

	private PopupWindow mPopup;
	private View mLayout;
	private ImageView arrowView = null;
	private boolean mSpeaking;
	private IEventListener mEventListener;
	private Toast mToast;
	private Compass compass;
	private float currectAzimuth = 0;
	private WebView webview;
	private int faceState;
	private final static String [] emojs= new String[]{"standardFace","winkFace","happyFace","ragingFace"};
	public Display(Activity activity)
	{
		mActivity = activity;
	}

	public void setListener(IEventListener callback) {
		mEventListener = callback;
	}

	private boolean onEvent(String elem, MotionEvent event) {
		Log.i(TAG, "onEvent: touch " + elem);
		if (mEventListener != null)
			mEventListener.onEvent("touch", elem);
		return true;
	}

	public void showFace(String style) {
		Log.i(TAG, "showFace:" + style);
		faceState = 0;
		mLayout = mActivity.getLayoutInflater().inflate(R.layout.display, null);
		ViewGroup rootView = (ViewGroup) ((ViewGroup) mActivity
				.findViewById(android.R.id.content)).getChildAt(0);
		for(int i = 0;i<rootView.getChildCount();i++){
			rootView.getChildAt(i).setVisibility(View.INVISIBLE);
		}
		rootView.addView(mLayout);
		arrowView = (ImageView)mLayout.findViewById(R.id.compass);
		compass = new Compass(mActivity);

		compass.setListener(new Compass.CompassListener() {
			@Override
			public void onChange(final float azimuth) {
				mLayout.post(new Runnable() {
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
		compass.start();



		mSpeaking = false;


		//mPopup.setBackgroundDrawable(new BitmapDrawable());
		View view = new View(mActivity);
		view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		view.setBackgroundColor(Color.BLACK);
		int[] location = new int[2];
		rootView.getLocationInWindow(location);
		int y = location[1];
		mPopup  = new PopupWindow(view, WindowManager.LayoutParams.MATCH_PARENT,
				location[1],false);
		mPopup.setFocusable(false); // allows event to reach activity below
		mPopup.showAtLocation(view, Gravity.NO_GRAVITY, 0, 0);

		mPreview = (CameraSourcePreview) mLayout.findViewById(R.id.preview);
		mGraphicOverlay = (GraphicOverlay) mLayout.findViewById(R.id.faceOverlay);

		webview = (WebView) mLayout.findViewById(R.id.faceId);
		webview.getSettings().setJavaScriptEnabled(true);
		webview.getSettings().setLoadWithOverviewMode(true);
		webview.getSettings().setUseWideViewPort(true);
		webview.getSettings().setDomStorageEnabled(true);
		webview.addJavascriptInterface(this,"robot");
		webview.loadUrl("file:///android_asset/face/index.html");
		int rc = ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA);
		if (rc != PackageManager.PERMISSION_GRANTED) {
			return;
		}
		FaceDetector detector = new FaceDetector.Builder(mActivity)
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

		mCameraSource = new CameraSource.Builder(mActivity, detector)
				.setRequestedPreviewSize(640, 480)
				.setFacing(CameraSource.CAMERA_FACING_FRONT)
				.setRequestedFps(30.0f)
				.build();
		startCameraSource();
		SensorManager mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
		Sensor mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
		actived = true;
		mSensorManager.registerListener(this, mSensor,SensorManager.SENSOR_DELAY_NORMAL);

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
				PowerManager manager = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
				manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "tag").acquire();
			} else {
				if(mEventListener!=null){
					mEventListener.onEvent("robot","weakup");
				}
				mPreview.resume();
				PowerManager manager = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
				manager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tag").release();
			}
			actived = !actived;
		}
	}
	@JavascriptInterface
	public void onFaceClicked(){
		mLayout.post(new Runnable() {
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
		int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mActivity);
		if (code != ConnectionResult.SUCCESS) {
			Dialog dlg =
					GoogleApiAvailability.getInstance().getErrorDialog(mActivity, code, 9001);
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
		mLayout.post(new Runnable() {
			public void run() {
				webview.evaluateJavascript("window."+emojs[emotion]+"();",null);
			}
		});
	}
	public void setSpeaking(boolean speaking) {
		mSpeaking = speaking;
		if (speaking) {
			Log.i(TAG, "speaking");
			mLayout.post(new Runnable() {
				public void run() {
					//TODO
				}
			});
		} else {
			Log.i(TAG, "done speaking");
			mLayout.post(new Runnable() {
				public void run() {
					//TODO
				}
			});
		}
	}

	public boolean isVisible() {
		return (mPopup.isShowing());
	}

	public void hideFace() {
		Log.i(TAG, "hideFace");

		mLayout.post(new Runnable() {
			public void run() {
				if(compass!=null){
					compass.stop();
					compass = null;
				}
				if (mToast != null)
					mToast.cancel();
				mPreview.stop();
				if (mCameraSource != null) {
					mCameraSource.release();
					mCameraSource = null;
				}
				mPopup.dismiss();
				ViewGroup rootView = (ViewGroup) ((ViewGroup) mActivity
						.findViewById(android.R.id.content)).getChildAt(0);
				rootView.removeView(mLayout);
				for(int i = 0;i<rootView.getChildCount();i++){
					rootView.getChildAt(i).setVisibility(View.VISIBLE);
				}
				SensorManager mSensorManager = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
				mSensorManager.unregisterListener(Display.this);
			}
		});
	}

	public void showMessage(final String msg, final int len) {
		mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (mToast != null)
					mToast.cancel();
				mToast = Toast.makeText(mActivity, msg, len);
				mToast.show();
			}
		});
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