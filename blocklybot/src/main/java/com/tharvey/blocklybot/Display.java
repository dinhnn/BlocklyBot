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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.Toast;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class Display {
	private final static String TAG = Display.class.getSimpleName();

	private Activity mActivity;
	private PopupWindow mPopup;
	private View mLayout;
	private ImageView mEyeL;
	private ImageView mEyeR;
	private ImageView mMouth;
	private ImageView arrowView = null;
	private ObjectAnimator mSpeakAnimation;
	private boolean mSpeaking;
	private IEventListener mEventListener;
	private Toast mToast;
	private Compass compass;
	private float currectAzimuth = 0;
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

		mLayout = mActivity.getLayoutInflater().inflate(R.layout.display, null);
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



		mEyeL = (ImageView) mLayout.findViewById(R.id.eyeLeft);
		mEyeR = (ImageView) mLayout.findViewById(R.id.eyeRight);
		mMouth = (ImageView) mLayout.findViewById(R.id.mouth);
		mSpeaking = false;

		mPopup  = new PopupWindow(mLayout, WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,true);
		mPopup.setFocusable(false); // allows event to reach activity below
		//mPopup.setBackgroundDrawable(new BitmapDrawable());
		mPopup.showAtLocation(mLayout, Gravity.NO_GRAVITY, 0, 0);
		mEyeL.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return onEvent("eye", event);
			}
		});
		mEyeR.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return onEvent("eye", event);
			}
		});
		mMouth.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return onEvent("mouth", event);
			}
		});
		mLayout.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return onEvent("face", event);
			}
		});

		/* gentle mouth bobbing */
		mLayout.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					public void onGlobalLayout() {
						Log.i(TAG, "mouth:" + mMouth.getX() + "," + mMouth.getY() + ":" + mMouth.getHeight());
						final ObjectAnimator breathe = ObjectAnimator.ofFloat(mMouth, "y",
								mMouth.getY(), mMouth.getY() - mMouth.getHeight() / 4);
						breathe.addListener(new Animator.AnimatorListener() {

							@Override
							public void onAnimationEnd(Animator animation) {
								breathe.setStartDelay(1000);
								breathe.start();
							}

							@Override
							public void onAnimationStart(Animator animation) { }

							@Override
							public void onAnimationCancel(Animator animation) { }

							@Override
							public void onAnimationRepeat(Animator animation) { }
						});
						breathe.setRepeatCount(1);
						breathe.setRepeatMode(ObjectAnimator.REVERSE);
						breathe.setStartDelay(1000);
						breathe.setInterpolator(new AccelerateDecelerateInterpolator());
						breathe.start();
					}
				}
		);

		/* occasional eye blink */
		final ObjectAnimator blinkL = ObjectAnimator.ofFloat(mEyeL, "scaleY", 1f, 0.1f);
		final ObjectAnimator blinkR = ObjectAnimator.ofFloat(mEyeR, "scaleY", 1f, 0.1f);
		final AnimatorSet blink = new AnimatorSet();
		blinkL.setDuration(200);
		blinkL.setRepeatCount(1);
		blinkL.setRepeatMode(ObjectAnimator.REVERSE);
		blinkL.setInterpolator(new AccelerateDecelerateInterpolator());
		blinkR.setDuration(200);
		blinkR.setRepeatCount(1);
		blinkR.setRepeatMode(ObjectAnimator.REVERSE);
		blinkR.setInterpolator(new AccelerateDecelerateInterpolator());
		blink.playTogether(blinkL, blinkR);
		blink.setStartDelay(1000);
		blink.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationEnd(Animator animation) {
				blink.setStartDelay(new Random().nextInt(4000 - 2000) + 2000); // 2 to 4 secs
				blink.start();
			}

			@Override
			public void onAnimationStart(Animator animation) { }

			@Override
			public void onAnimationCancel(Animator animation) { }

			@Override
			public void onAnimationRepeat(Animator animation) { }
		});
		//blink.start();

		/* Speaking animation */
		mSpeakAnimation = ObjectAnimator.ofFloat(mMouth, "scaleY", 1f, 1.75f);
		mSpeakAnimation.setRepeatCount(ObjectAnimator.INFINITE);
		mSpeakAnimation.setRepeatMode(ObjectAnimator.REVERSE);
		final AnimatedVectorDrawable a = (AnimatedVectorDrawable)mActivity.getDrawable(R.drawable.standard_vector);
		ImageView imageView = (ImageView)mLayout.findViewById(R.id.imageView2);
		imageView.setVisibility(View.VISIBLE);
		imageView.setImageDrawable(a);
		new Timer().scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				mActivity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						a.start();
					}
				});

			}
		},1000,5000);

	}


	public void setSpeaking(boolean speaking) {
		mSpeaking = speaking;
		if (speaking) {
			Log.i(TAG, "speaking");
			mLayout.post(new Runnable() {
				public void run() {
					mSpeakAnimation.start();
				}
			});
		} else {
			Log.i(TAG, "done speaking");
			mLayout.post(new Runnable() {
				public void run() {
					mSpeakAnimation.cancel();
					mMouth.clearAnimation();
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
				mPopup.dismiss();
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
}