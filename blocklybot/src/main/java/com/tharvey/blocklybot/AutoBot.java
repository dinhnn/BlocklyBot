package com.tharvey.blocklybot;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

/**
 * Created by dinhnn on 11/20/17.
 */

public interface AutoBot extends IFunction{
    void setListener(IEventListener callback);
    void showMessage(final String msg, final int len);
    void setEmotion(int emotion);
    void setSpeaking(boolean speaking);
    void rotate(int angle,int speed);
    void forward(int distance,int speed);
    void backward(int distance,int speed);
}
