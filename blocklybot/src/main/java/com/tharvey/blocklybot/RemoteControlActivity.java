package com.tharvey.blocklybot;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class RemoteControlActivity extends RobotActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote_control);
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
}
