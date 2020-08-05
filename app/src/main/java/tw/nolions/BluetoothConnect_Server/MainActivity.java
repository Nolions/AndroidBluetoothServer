package tw.nolions.BluetoothConnect_Server;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "BluetoothConnect_Server";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private AcceptThread at;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler handler = new MainHandler(this);
    private EditText mMsgEditText;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //收到bluetooth狀態改變
            if (intent.getAction() != null) {
                Log.d(TAG, "MainActivity::mReceiver::onReceive(), action:" + intent.getAction());
                // TODO 接收到不同狀態後要做的事情
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        Log.d(TAG, "MainActivity::mReceiver::onReceive(), action:BluetoothDevice.ACTION_ACL_CONNECTED");
                        Message msg = new Message();
                        msg.obj = "連線建立。";
                        handler.sendMessage(msg);
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED:
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMsgEditText = findViewById(R.id.msgEditText);
        init();
    }

    private void init() {
        registerBroadcastReceiver();
        initBluetooth();
    }

    private void initBluetooth() {
        Log.d(TAG, "initBluetooth...");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // check support Bluetooth
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "The device no support bluetooth", Toast.LENGTH_LONG).show();
        }

        // check Bluetooth enable
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            int REQUEST_ENABLE_BT = 1;
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                at = new AcceptThread();
                Message msg = new Message();
                handler.sendMessage(msg);
                at.run();
            }
        }).start();
    }

    /**
     * 註冊BroadcastReceiver事件
     * ============================
     * 1. 連線狀態改變
     * 2. 連線建立
     * 3. 中斷連線請求
     * 4. 中斷連線
     */
    private void registerBroadcastReceiver() {
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_ACL_CONNECTED); // 連線建立
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED); // 中斷連線請求，並中斷連線
        intent.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED); // 中斷連線
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); // 連線狀態改變
        registerReceiver(mReceiver, intent);
    }

    public void send(View view) {
        Log.d(TAG, "on click Send...");
        String msg = mMsgEditText.getText().toString();

        if (msg.equals("")) {
            // 沒有輸入訊息
            Log.e(TAG, "message is empty");
            return;
        }

        if (at == null) {
            // 尚未建立藍牙連線
            Log.e(TAG, "no connection");
            return;
        }

        // 清空輸入訊息輸入欄
        mMsgEditText.setText("");
        at.write(msg);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private BluetoothSocket socket;
        private InputStream mInputStream;
        private OutputStream mOutputStream;

        private AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(TAG, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            mmServerSocket = tmp;
        }

        public void run() {
            try {
                socket = mmServerSocket.accept();
                mInputStream = socket.getInputStream();
                mOutputStream = socket.getOutputStream();
                read();
            } catch (IOException e) {
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }

        /**
         * 關閉藍芽連線
         */
        private void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel error: " + e.getMessage());
            }
        }

        private void read() {
            while (true) {
                try {
                    byte[] buffer = new byte[128];
                    Message msg = new Message();
                    msg.obj = new String(buffer, 0, mInputStream.read(buffer), StandardCharsets.UTF_8);
                    Log.d(TAG, "AcceptThread::read(), msg:" + msg.obj);
                    handler.sendMessage(msg);
                } catch (IOException e) {
                    Log.e(TAG, "AcceptThread::read(), error:" + e.getMessage());
                    break;
                }
            }
        }

        private void write(String data) {
            try {
                if (mOutputStream != null) {
                    Log.d(TAG, "AcceptThread::write(), message:" + data);
                    // 以位元把使用utf-8的格式進行藍芽傳送
                    mOutputStream.write(data.getBytes(StandardCharsets.UTF_8));

                    // 成功發送訊息後，顯示發送成功訊息。(可省略)
                    Message msg = new Message();
                    msg.obj = "Send Message success";
                    handler.sendMessage(msg);
                }
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread::write(), error:" + e.getMessage());
            }
        }
    }

    private static class MainHandler extends Handler {

        private Activity mActivity;

        private MainHandler(Activity activity) {
            mActivity = activity;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.e("BluetoothConnect_Server", "MainHandler::handleMessage(), msg:" + msg.obj);
            Toast.makeText(mActivity, "訊息:" + msg.obj, Toast.LENGTH_SHORT).show();
        }
    }
}

