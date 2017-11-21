package com.tharvey.blocklybot;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Toast;

public class RobotActivity extends AppCompatActivity {
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
        engine = new ScriptEngine(this);
        engine.parseCode(Mobbob.getMobob(),script,variables);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mToast != null)
            mToast.cancel();
    }
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
    public void setEmotion(int emotion){
    }
    public void setSpeaking(boolean speaking){

    }
}
