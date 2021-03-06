package com.sy.op.smartlocating;

import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
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
    private double A = 500, f = 14000, updateTime = 0.1;
    private static int ti = 0;
    private int currentDistance = 0, distanceOffset = 0;
    private int recChannelConfig = AudioFormat.CHANNEL_IN_MONO, plyChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
    private int recAudioEncoding = AudioFormat.ENCODING_PCM_16BIT, plyAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private int recBufferSize = AudioRecord.getMinBufferSize(recFs, recChannelConfig, recAudioEncoding);
    private int plyBufferSize = AudioTrack.getMinBufferSize(plyFs, plyChannelConfig, plyAudioEncoding);
    private int recBufferReadRes, plyBufferWriteRes;
    private short[] recBuffer = new short[(int)(recFs * updateTime) + 1];
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
        findViewById(R.id.btnCalibration).setEnabled(true);
        findViewById(R.id.btnStop).setEnabled(true);
        playAudio();
        recAnalyse();
    }

    public void onCalibration(View v) {
        calibrate();
    }

    public void stopMeasuring(View v) {
        findViewById(R.id.btnStart).setEnabled(true);
        findViewById(R.id.btnStop).setEnabled(false);
        findViewById(R.id.btnCalibration).setEnabled(false);
        doStop();
    }

    private void doStop() {
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

    private void calibrate() {
        distanceOffset = currentDistance;
    }


    private class recAudioBufferHandler extends Handler {
        public recAudioBufferHandler() {}

        @Override
        public void handleMessage(Message msg) {
            // update distance value
            TextView xVal = (TextView)findViewById(R.id.txvX);
            xVal.setText(String.valueOf(msg.arg1) + "mm");
        }
    }

    private class plyAudioBuffer implements Runnable {

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

    private class recAudioBuffer implements Runnable {

        @Override
        public void run() {
            switch (Thread.currentThread().getName()) {
                case "ana":
                    ti = 0;
                    // initialize recorder
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, recFs, recChannelConfig, recAudioEncoding, recBufferSize * 2);

                    if (recorder.getState() == STATE_UNINITIALIZED)
                        throw new RuntimeException("Failed to initialize recorder.");
                    else {
                        recFs = recorder.getSampleRate();
                    }
                    // start recording
                    recorder.startRecording();
                    if (recorder.getRecordingState() != RECORDSTATE_RECORDING)
                        throw new RuntimeException("Failed to start recording.");

                    // analyze
                    try {
                        // initialize
                        recBufferReadRes = 0;
                        double[][] P = new double[2][2];
                        P[0][0] = 1; P[0][1] = 0; P[1][0] = 0; P[1][1] = 1;
                        double Micro = 0.95;
                        double[] K = new double[2];
                        K[0] = 1; K[1] = 0;
                        double[][] theta = new double[2][3];
                        theta[0][0] = 0; theta[1][0] = 1; theta[0][1] = 0; theta[1][1] = 0; theta[0][2] = 0; theta[1][2] = 0;
                        double[] staticInPhase = new double[3];
                        staticInPhase[0] = 0; staticInPhase[1] = 0; staticInPhase[2] = 0;
                        double[] staticOrth = new double[3];
                        staticOrth[0] = 0; staticOrth[1] = 0; staticOrth[2] = 0;
                        double[] dynamicInPhase = new double[3];
                        dynamicInPhase[0] = 0; dynamicInPhase[1] = 0; dynamicInPhase[2] = 0;
                        double[] dynamicOrth = new double[3];
                        dynamicOrth[0] = 0; dynamicOrth[1] = 0; dynamicOrth[2] = 0;
                        double lastExtreDataInPhase = 0, extreDataInPhase = 0, extreIDInPhase=1;
                        double lastExtreDataOrth = 0, extreDataOrth = 0, extreIDOrth = 1;
                        double threshold = 1.9;
                        double[] angle = new double[3];
                        angle[0] = 0; angle[1] = 0;
                        double[] distance = new double[3];
                        distance[0] = 0; distance[1] = 0; distance[2] = 0;
                        double temperature = 26.0, voiceVelocity = Math.sqrt(1.4 * 287 * (temperature + 273.15));
                        double k = 2 * Math.PI * voiceVelocity / f;
                        recBuffer[0] = 0;
                        /*String filePath = "sdcard/com.sy.op.SmartLocating";
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
                        FileWriter fOStr = new FileWriter(filePath + "/" + fileName);*/

                        while (recBufferReadRes >= 0) {
                            recBufferReadRes = recorder.read(recBuffer, 1, (int)(recFs * updateTime));
                            /* extract component of frequency f;
                            FFT mf = new FFT();
                            Complex[] recBufferComplex = new Complex[recBufferReadRes];
                            for(int i = 0; i < recBufferReadRes; ++i) {
                                recBufferComplex[i] = new Complex(recBuffer[i + 1], 0);
                            }
                            recBufferComplex = mf.changedLow(recBufferComplex, recBufferReadRes);
                            recBufferComplex = mf.fft_2(recBufferComplex, recBufferReadRes, 1);
                            for (int i = 0; i < recBufferReadRes; ++i) {
                                    recBufferComplex[i].r *= (20 / (Math.pow(i - f * recBufferReadRes / recFs, 2) + 20));
                                    recBufferComplex[i].i *= (20 / (Math.pow(i - f * recBufferReadRes / recFs, 2) + 20));
                            }
                            recBufferComplex = mf.changedLow(recBufferComplex, recBufferReadRes);
                            recBufferComplex = mf.fft_2(recBufferComplex, recBufferReadRes, -1);
                            for (int i = 0; i < recBufferReadRes; ++i) {
                                recBuffer[i + 1] = (short)recBufferComplex[i].r;
                            }*/
                            // analyse data
                            for (int i = 0; i < recBufferReadRes; ++i) {
                                ++ti;
                                double[] hk = new double[2];
                                hk[0] = (short)(A * Math.sin(2 * Math.PI * f * (ti + 1) / recFs));
                                hk[1] = (short)(A * Math.cos(2 * Math.PI * f * (ti + 1) / recFs));
                                double temp = ((hk[0] * P[0][0] + hk[1] * P[1][0]) * hk[0] + (hk[0] * P[0][1] + hk[1] * P[1][1]) * hk[1]) + Micro;
                                K[0] = (P[0][0] * hk[0] + P[0][1] * hk[1]) / temp;
                                K[1] = (P[1][0] * hk[0] + P[1][1] * hk[1]) / temp;
                                double tempA = P[0][0], tempB = P[0][1], tempC = P[1][0], tempD = P[1][1];
                                P[0][0] = (tempA * (1 - K[0] * hk[0]) - tempC * K[0] * hk[1]) / Micro;
                                P[0][1] = (tempB * (1 - K[0] * hk[0]) - tempD * K[0] * hk[1]) / Micro;
                                P[1][0] = (-tempA * K[1] * hk[0] + tempC * (1 - K[1] * hk[1])) / Micro;
                                P[1][1] = (-tempB * K[1] * hk[0] + tempD * (1 - K[1] * hk[1])) / Micro;
                                temp = recBuffer[i + 1] - hk[0] * theta[0][1] - hk[1] * theta[1][1];
                                theta[0][2] = theta[0][1] + K[0] * temp;
                                theta[1][2] = theta[1][1] + K[1] * temp;
                                double newExtreDataInPhase = 0, newExtreIDInPhase = 0, newExtreDataOrth = 0, newExtreIDOrth = 0;
                                if((theta[0][1] >= theta[0][0]) && (theta[0][1] >= theta[0][2])) {
                                    newExtreDataInPhase = theta[0][1];
                                    newExtreIDInPhase = 1;
                                }
                                if((theta[0][1] <= theta[0][0]) && (theta[0][1] <= theta[0][2])) {
                                    newExtreDataInPhase = theta[0][1];
                                    newExtreIDInPhase = -1;
                                }
                                if((theta[0][1] - theta[0][0]) * (theta[0][2] - theta[0][1]) >= 0) {
                                    staticInPhase[1] = staticInPhase[0];
                                } else if (((extreIDInPhase > 0) && (newExtreIDInPhase > 0) && (newExtreDataInPhase > extreDataInPhase)) || ((extreIDInPhase < 0) && (newExtreIDInPhase) < 0 && (newExtreDataInPhase < extreDataInPhase))) {
                                    extreDataInPhase = newExtreDataInPhase;
                                    extreIDInPhase = newExtreIDInPhase;
                                } else if ((extreIDInPhase * newExtreIDInPhase < 0) && (Math.abs(newExtreDataInPhase - extreDataInPhase) > threshold)) {
                                    lastExtreDataInPhase = extreDataInPhase;
                                    extreDataInPhase = newExtreDataInPhase;
                                    extreIDInPhase = newExtreIDInPhase;
                                    staticInPhase[2] = 0.9 * staticInPhase[1] + 0.1 * (lastExtreDataInPhase + extreDataInPhase) / 2;
                                }
                                dynamicInPhase[2]=theta[0][2]-staticInPhase[2];
                                if ((theta[1][1] >= theta[1][0]) && (theta[1][1] >= theta[1][2])) {
                                    newExtreDataOrth = theta[1][1];
                                    newExtreIDOrth = 1;
                                }
                                if ((theta[1][1] <= theta[1][0]) && (theta[1][1] <= theta[1][2])) {
                                    newExtreDataOrth = theta[1][1];
                                    newExtreIDOrth = -1;
                                }
                                if ((theta[1][1] - theta[1][0]) * (theta[1][2] - theta[1][1]) >= 0) {
                                    staticOrth[1] = staticOrth[0];
                                } else if (((extreIDOrth > 0) && (newExtreIDOrth > 0) && (newExtreDataOrth > extreDataOrth)) || ((extreIDOrth < 0) && (newExtreIDOrth < 0) && (newExtreDataOrth < extreDataOrth))) {
                                    extreDataOrth = newExtreDataOrth;
                                    extreIDOrth = newExtreIDOrth;
                                } else if ((extreIDOrth * newExtreIDOrth < 0) && (Math.abs(newExtreDataOrth - extreDataOrth) > threshold)) {
                                    lastExtreDataOrth = extreDataOrth;
                                    extreDataOrth = newExtreDataOrth;
                                    extreIDOrth = newExtreIDOrth;
                                    staticOrth[2] = 0.9 * staticOrth[1] + 0.1 * (lastExtreDataOrth + extreDataOrth) / 2;
                                }
                                dynamicOrth[2] = theta[1][2] - staticOrth[2];
                                angle[2] = Math.atan(dynamicOrth[2] / dynamicInPhase[2]);
                                if((Math.abs(angle[2] - angle[1])) < 1) {
                                    distance[2] = distance[1] + (angle[2] - angle[1]) / k;
                                } else {
                                    distance[2] = distance[1];
                                }

                                theta[0][0] = theta[0][1]; theta[0][1] = theta[0][2]; theta[0][2] = 0.0;
                                theta[1][0] = theta[1][1]; theta[1][1] = theta[1][2]; theta[1][2] = 0.0;
                                staticInPhase[0] = staticInPhase[1]; staticInPhase[1] = staticInPhase[2]; staticInPhase[2] = 0;
                                staticOrth[0] = staticOrth[1]; staticOrth[1] = staticOrth[2]; staticOrth[2] = 0;
                                dynamicInPhase[0] = dynamicInPhase[1]; dynamicInPhase[1] = dynamicInPhase[2]; dynamicInPhase[2] = 0;
                                dynamicOrth[0]= dynamicOrth[1]; dynamicOrth[1] = dynamicOrth[2]; dynamicOrth[2] = 0;
                                angle[1] = angle[2]; angle[2] = 0;
                                distance[1] = distance[2]; distance[2] = 0;
                            }

                            // send result to UI thread
                            if (Math.abs(distance[0] - distance[1]) < 5) {
                                distance[0] = distance[1];  // real movement
                            } else {
                                distance[1] = distance[0];  // noise
                            }
                            if (ti % (int)(recFs * updateTime) == 0) {
                                currentDistance = -(int)(distance[0] * voiceVelocity / 1000);
                                Message msg = new Message();
                                msg.arg1 = currentDistance - distanceOffset;
                                MainActivity.this.recBuffHdl.sendMessage(msg);
                            }
                            /*// save data in file
                            if (r < 15 * recFs) {  // save first 10s' data
                                for (int i = 0; i < recBufferReadRes; ++i) {
                                    ++r;
                                    fOStr.write(String.valueOf(recBuffer[i]) + "\n");
                                }
                            } else
                                fOStr.close();*/
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

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            doStop();
            finish();
            System.exit(0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
