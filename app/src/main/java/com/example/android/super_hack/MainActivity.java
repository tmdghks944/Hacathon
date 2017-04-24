package com.example.android.super_hack;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.icu.util.Output;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private Set<BluetoothDevice> mDevices;
    private int mPairedDeviceCount;
    private BluetoothDevice mRemoteDevice;
    private BluetoothSocket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private Thread mWorkerThread;
    private byte mDelimiter = '=';
    private byte[] readBuffer;
    private int readBufferPosition;
    private TextView mainTitle, numbertext;
    private LinearLayout mainLinear;
    String result = "";
    int select;
    Handler mHandler;
    ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainTitle = (TextView) findViewById(R.id.mainTitle);
        numbertext = (TextView) findViewById(R.id.numbertext);
        mainLinear = (LinearLayout) findViewById(R.id.mainLinear);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            //장치가 블루투스를 지원하지 않는 경우.
        } else {
            // 장치가 블루투스를 지원하는 경우.
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                // 페어링 된 장치가 있는 경우.
                selectDevice();
            } else {
                // 페어링 된 장치가 없는 경우.
            }
        }
    }

    void selectDevice() {
        mDevices = mBluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();

        if (mPairedDeviceCount == 0) {
            //  페어링 된 장치가 없는 경우
            finish();    // 어플리케이션 종료
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");


        // 페어링 된 블루투스 장치의 이름 목록 작성
        List<String> listItems = new ArrayList<String>();
        for (BluetoothDevice device : mDevices)
            listItems.add(device.getName());

        listItems.add("취소");    // 취소 항목 추가

        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if (item == mPairedDeviceCount) {
                    // 연결할 장치를 선택하지 않고 '취소'를 누른 경우
                    finish();
                } else {
                    // 연결할 장치를 선택한 경우
                    // 선택한 장치와 연결을 시도함
                    connectToSelectdDevice(items[item].toString());
                }
            }
        });

        builder.setCancelable(false);    // 뒤로 가기 버튼 사용 금지
        AlertDialog alert = builder.create();
        alert.show();
    }

    BluetoothDevice getDeviceFromBondedList(String name) {
        BluetoothDevice selectedDevice = null;

        for (BluetoothDevice device : mDevices) {
            if (name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }

        return selectedDevice;
    }

    void connectToSelectdDevice(String selectedDeviceName) {
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

        try {
            // 소켓 생성
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);
            // RFCOMM 채널을 통한 연결
            mSocket.connect();

            // 데이터 송수신을 위한 스트림 열기
            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();
            // 데이터 수신 준비
            beginListenForData();
        } catch (Exception e) {
            // 블루투스 연결 중 오류 발생
            Log.e("err", String.valueOf(e));
            finish();   // 어플 종료
        }
    }

    void sendData(String msg) {
        //msg += mDelimiter;    // 문자열 종료 표시

        try {
            mOutputStream.write(msg.getBytes());    // 문자열 전송
        } catch (Exception e) {
            // 문자열 전송 도중 오류가 발생한 경우.
            finish();    //  APP 종료
        }
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        readBuffer = new byte[1024];  //  수신 버퍼
        readBufferPosition = 0;        //   버퍼 내 수신 문자 저장 위치

        // 문자열 수신 쓰레드
        mWorkerThread = new Thread(new Runnable() {
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        int bytesAvailable = mInputStream.available();    // 수신 데이터 확인
                        if (bytesAvailable > 0) {                     // 데이터가 수신된 경우
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);
                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if (b == mDelimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        public void run() {
                                            // 수신된 문자열 데이터에 대한 처리 작업
                                            Log.e("data", data);
                                            result += data;
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    numbertext.setText(result);
                                    result = "";
                                }
                            });
                        }
                    } catch (IOException ex) {
                        // 데이터 수신 중 오류 발생.
                        finish();
                    }
                }
            }
        });
        mWorkerThread.start();
    }

ㄴ
    public void clickMain(View view) {
        switch (view.getId()) {
            case R.id.main1:
                select = 1;
                mainTitle.setText("습도");
                sendData("1");
                break;
            case R.id.main2:
                select = 2;
                mainTitle.setText("일조량");
                sendData("2");
                break;
        }

        mainLinear.setVisibility(View.GONE);
        mainTitle.setVisibility(View.VISIBLE);
        numbertext.setVisibility(View.VISIBLE);
    }

    public void waterSupply(View view) {

        mHandler = new Handler();
        mProgressDialog = ProgressDialog.show(MainActivity.this, "",
                "수위를 확인합니다.", true);
        sendData("4");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        sendData("1");
                        mProgressDialog = ProgressDialog.show(MainActivity.this, "",
                                "물을 공급 중입니다.", true);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                                        mProgressDialog.dismiss();
                                        mProgressDialog = ProgressDialog.show(MainActivity.this, "",
                                                "꽃이 물을 흡수하고 있습니다.", true);
                                        mHandler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    if (mProgressDialog != null && mProgressDialog.isShowing()) {
                                                        mProgressDialog.dismiss();
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }, 7000);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }, 10000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 7000);
        // dialog
    }

    @Override
    public void onBackPressed() {

        if (mainLinear.getVisibility() == View.VISIBLE) {
            finish();
            return;
        }

        //mainLinear.setVisibility(View.VISIBLE);
        //mainTitle.setVisibility(View.GONE);
        //numbertext.setVisibility(View.GONE);
    }
}