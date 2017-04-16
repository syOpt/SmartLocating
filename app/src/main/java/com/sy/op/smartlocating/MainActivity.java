package com.sy.op.smartlocating;

import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.STATE_UNINITIALIZED;

public class MainActivity extends AppCompatActivity {
    private audioBuffer buff = new audioBuffer();
    private audioBufferHandler buffHdl = new audioBufferHandler();
    private int fs = 44100;
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSize = AudioRecord.getMinBufferSize(fs, channelConfig, audioEncoding);
    private int bufferReadRes;
    private short[] buffer = new short[bufferSize];
    private AudioRecord recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void startMeasuring(View v) {
        findViewById(R.id.btnStart).setEnabled(false);
        findViewById(R.id.btnStop).setEnabled(true);
        playAudio();
        recAnalyse();
    }

    public void stopMeasuring(View v) {
        findViewById(R.id.btnStart).setEnabled(true);
        findViewById(R.id.btnStop).setEnabled(false);
        Thread stp = new Thread(buff);
        stp.setName("stp");
        stp.start();
    }


    private void playAudio() {

    }

    private void recAnalyse() {
        Thread ana = new Thread(buff);
        ana.setName("ana");
        ana.start();
    }

    protected class audioBufferHandler extends Handler {
        public audioBufferHandler() {}

        @Override
        public void handleMessage(Message msg) {
            TextView xVal = (TextView)findViewById(R.id.txvX);
            xVal.setText(String.valueOf(msg.arg1));
        }
    }

    protected class audioBuffer implements Runnable {


        @Override
        public void run() {


            switch (Thread.currentThread().getName()) {
                case "ana":
                    bufferReadRes = 0;
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, fs, channelConfig, audioEncoding, bufferSize * 2);
                    recorder.startRecording();
                    if (recorder.getState() == STATE_UNINITIALIZED)
                        throw new RuntimeException("Failed to initialize recorder.");
                    else if (recorder.getRecordingState() != RECORDSTATE_RECORDING)
                        throw new RuntimeException("Failed to start recording.");
                    while (bufferReadRes >= 0) {
                        bufferReadRes = recorder.read(buffer, 0, 100);//bufferSize);
                        int avg = 0;
                        for (int i = 0; i < bufferReadRes; ++i) {
                            avg += buffer[i];
                        }
                        if (bufferReadRes != 0) avg /= bufferReadRes;
                        Message msg = new Message();
                        msg.arg1 = avg;
                        MainActivity.this.buffHdl.sendMessage(msg);
                    }
                    break;
                case "stp":
                    try {
                        recorder.stop();
                        recorder.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown handling thread.");
            }
        }
    }
}
