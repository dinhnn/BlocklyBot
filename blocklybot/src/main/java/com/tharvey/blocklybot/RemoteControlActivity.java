package com.tharvey.blocklybot;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class RemoteControlActivity extends RobotActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);
        JoystickView joystickRight = (JoystickView) findViewById(R.id.joystickView_right);
        joystickRight.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(double angle, double strength) {
                joystick(angle,strength);
            }
        });
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
