package com.tharvey.blocklybot;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

public abstract class RobotActivity extends AppCompatActivity implements AutoBot {
    private String[] variables = {
            "apple",
            "orange",
            "bannana",
            "coconut",
            "carrot",
    };
    protected IEventListener mEventListener;
    private Toast mToast;

    public void setListener(IEventListener callback) {
        mEventListener = callback;
    }

    private ScriptEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String script = getIntent().getExtras().getString("script");
        engine = new ScriptEngine(this, this);
        engine.parseCode(script, variables);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mToast != null)
            mToast.cancel();
    }

    @Override
    public void showMessage(final String msg, final int len) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null)
                    mToast.cancel();
                mToast = Toast.makeText(RobotActivity.this, msg, len);
                mToast.show();
            }
        });
    }

    @Override
    public boolean doFunction(String cmd, int param2, int param3) {
        if (cmd == null || isBusy()) return false;
        switch (cmd) {
            case "MOVEFORWARD":
                forward(param2, param3);
                return true;
            case "MOVEBACKWARD":
                backward(param2, param3);
                return true;
            case "TURNRIGHT":
                rotate(90, param2);
                return true;
            case "TURNLEFT":
                rotate(-90, param2);
                return true;
            case "ROTATE":
                rotate(param2, param3);
                return true;
            default:
                return false;
        }
    }

    private double x;
    private double y;
    private double srcX;
    private double srcY;
    private double srcDir;
    private double destX;
    private double destY;
    private double destDir;
    private double dir;
    private double lastDistance2;
    private boolean moving;
    private int rotating;

    protected void setLocation(double x, double y, double dir) {
        this.x = x;
        this.y = y;
        this.dir = dir;
        if (moving) {
            double dx = destX - x;
            double dy = destY - y;
            double d2 = dx * dx + dy * dy;
            if (d2 < lastDistance2) {
                stopMoving();
            }
        }
        if (rotating != 0) {
            double da = destDir - dir;
            if (da < -Math.PI) da += 2 * Math.PI;
            else if (da > Math.PI) da -= 2 * Math.PI;
            if ((rotating > 0 && da <= 0) || (rotating < 0 && da >= 0)) {
                stopMoving();
            }
        }
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    protected void control(int leftspeed, int rightspeed) {
    }

    @Override
    public void forward(int distance, int speed) {
        srcX = x;
        srcY = y;
        srcDir = dir;
        destX = x + Math.sin(dir) * distance;
        destY = y + Math.cos(dir) * distance;
        control(speed, speed);
    }

    @Override
    public void backward(int distance, int speed) {
        forward(-distance, -speed);
    }

    @Override
    public void rotate(int angle, int speed) {
        srcX = x;
        srcY = y;
        srcDir = dir;
        destDir = dir + angle;
        while (destDir > Math.PI) {
            destDir -= 2 * Math.PI;
        }
        while (destDir < -Math.PI) {
            destDir += 2 * Math.PI;
        }
        if (angle > 0) {
            rotating = 1;
            control(-speed, speed);
        } else if (angle < 0) {
            rotating = -1;
            control(speed, -speed);
        }
    }

    private void stopMoving() {
        moving = false;
        rotating = 0;
        control(0, 0);
    }
}
