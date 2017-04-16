package com.sy.op.smartlocating;

import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.STATE_UNINITIALIZED;

public class MainActivity extends AppCompatActivity {
    private recAudioBuffer recBuff = new recAudioBuffer();
    private plyAudioBuffer plyBuff = new plyAudioBuffer();
    private recAudioBufferHandler recBuffHdl = new recAudioBufferHandler();
    private int recFs = 44100, plyFs = 44100;
    private int recChannelConfig = AudioFormat.CHANNEL_IN_MONO, plyChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
    private int recAudioEncoding = AudioFormat.ENCODING_PCM_16BIT, plyAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private int recBufferSize = AudioRecord.getMinBufferSize(recFs, recChannelConfig, recAudioEncoding);
    private int plyBufferSize = AudioTrack.getMinBufferSize(plyFs, plyChannelConfig, plyAudioEncoding);
    private int recBufferReadRes, plyBufferWriteRes;
    private short[] recBuffer = new short[recBufferSize];
    private short[] plyBuffer = new short[plyBufferSize * 10];
    private AudioRecord recorder;
    private AudioTrack player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        Thread recStp = new Thread(recBuff);
        recStp.setName("recStp");
        recStp.start();
        Thread plyStp = new Thread(plyBuff);
        plyStp.setName("plyStp");
        plyStp.start();
    }


    private void playAudio() {
        Thread plyr = new Thread(plyBuff);
        plyr.setName("ply");
        plyr.start();
    }

    private void recAnalyse() {
        Thread ana = new Thread(recBuff);
        ana.setName("ana");
        ana.start();
    }

    protected class recAudioBufferHandler extends Handler {
        public recAudioBufferHandler() {}

        @Override
        public void handleMessage(Message msg) {
            // update distance value
            TextView xVal = (TextView)findViewById(R.id.txvX);
            xVal.setText(String.valueOf(msg.arg1));
        }
    }

    protected class plyAudioBuffer implements Runnable {

        @Override
        public void run() {
            switch (Thread.currentThread().getName()) {
                case "ply":
                    try {
                        // initialize player
                        player = new AudioTrack(STREAM_MUSIC, plyFs, plyChannelConfig, plyAudioEncoding, plyBufferSize, AudioTrack.MODE_STREAM);
                        if (player.getState() == STATE_UNINITIALIZED)
                            throw new RuntimeException("Failed to initialize player.");

                        // generate audio data, x = A*sin(2*pi*f*t) -> xi = A*sin(2*pi*f*(1/plyFs*i))
                        double A = 500, f = 15000;
                        for (int i = 0; i < plyBufferSize * 10; ++i) {
                            plyBuffer[i] = (short) (A * Math.sin(2.0 * Math.PI * f * (1.0 / (double)plyFs * (double)i)));
                        }

                        // start playing
                        plyBufferWriteRes = player.write(plyBuffer, 0, plyBufferSize * 10);
                        player.play();
                        while (plyBufferSize >= 0) {
                            plyBufferWriteRes = player.write(plyBuffer, 0, plyBufferSize * 10);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "plyStp":
                    try {
                        player.stop();
                        player.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown handling thread met in Player.");
            }
        }
    }

    protected class recAudioBuffer implements Runnable {

        @Override
        public void run() {
            switch (Thread.currentThread().getName()) {
                case "ana":
                    // initialize recorder
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, recFs, recChannelConfig, recAudioEncoding, recBufferSize * 2);
                    if (recorder.getState() == STATE_UNINITIALIZED)
                        throw new RuntimeException("Failed to initialize recorder.");

                    // start recording
                    recorder.startRecording();
                    if (recorder.getRecordingState() != RECORDSTATE_RECORDING)
                        throw new RuntimeException("Failed to start recording.");

                    // analyze
                    try {
                        // initialize
                        int r = 0;
                        recBufferReadRes = 0;
                        String filePath = "sdcard/com.sy.op.SmartLocating";
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
                        Date curDate = new Date(System.currentTimeMillis());
                        String fileName = formatter.format(curDate) + ".csv";
                        File fl = new File(filePath);
                        if (!fl.exists()) {
                            fl.mkdirs();
                        }
                        fl = new File(filePath + "/" + fileName);
                        if (!fl.exists()) {
                            fl.createNewFile();
                        }
                        FileWriter fOStr = new FileWriter(filePath + "/" + fileName);

                        while (recBufferReadRes >= 0) {
                            recBufferReadRes = recorder.read(recBuffer, 0, recBufferSize / 10);
                            // analyse data
                            int avg = 0;
                            for (int i = 0; i < recBufferReadRes; ++i) {
                                avg += recBuffer[i];
                            }
                            if (recBufferReadRes != 0) avg /= recBufferReadRes;

                            // send result to UI thread
                            Message msg = new Message();
                            msg.arg1 = avg;
                            MainActivity.this.recBuffHdl.sendMessage(msg);

                            // save data in file
                            if (r < 10 * recFs) {  // save first 10s' data
                                for (int i = 0; i < recBufferReadRes; ++i) {
                                    ++r;
                                    fOStr.write(String.valueOf(recBuffer[i]) + ", ");
                                }
                            } else
                                fOStr.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "recStp":
                    try {
                        recorder.stop();
                        recorder.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown handling thread met in recorder.");
            }
        }
    }
}
