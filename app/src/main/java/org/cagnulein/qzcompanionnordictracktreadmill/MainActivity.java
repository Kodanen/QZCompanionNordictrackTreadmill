package org.cagnulein.qzcompanionnordictracktreadmill;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import java.util.logging.Logger;

import static android.content.ContentValues.TAG;

import com.cgutman.androidremotedebugger.AdbUtils;
import com.cgutman.androidremotedebugger.console.ConsoleBuffer;
import com.cgutman.androidremotedebugger.devconn.DeviceConnection;
import com.cgutman.androidremotedebugger.devconn.DeviceConnectionListener;
import com.cgutman.androidremotedebugger.service.ShellService;
import com.cgutman.adblib.AdbCrypto;

public class MainActivity extends AppCompatActivity  implements DeviceConnectionListener {
    private ShellService.ShellServiceBinder binder;
    private static DeviceConnection connection;
    private Intent service;
    private static final String LOG_TAG = "QZ:AdbRemote";
    private static String lastCommand = "";
    private static boolean ADBConnected = false;


    @Override
    public void notifyConnectionEstablished(DeviceConnection devConn) {
        ADBConnected = true;
        Log.i(LOG_TAG, "notifyConnectionEstablished" + lastCommand);
    }

    @Override
    public void notifyConnectionFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamFailed(DeviceConnection devConn, Exception e) {
        ADBConnected = false;
        Log.e(LOG_TAG, e.getMessage());
    }

    @Override
    public void notifyStreamClosed(DeviceConnection devConn) {
        ADBConnected = false;
        Log.e(LOG_TAG, "notifyStreamClosed");
    }

    @Override
    public AdbCrypto loadAdbCrypto(DeviceConnection devConn) {
        return AdbUtils.readCryptoConfig(getFilesDir());
    }

    @Override
    public boolean canReceiveData() {
        return true;
    }

    @Override
    public void receivedData(DeviceConnection devConn, byte[] data, int offset, int length) {
        Log.i(LOG_TAG, data.toString());
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public void consoleUpdated(DeviceConnection devConn, ConsoleBuffer console) {

    }


    private DeviceConnection startConnection(String host, int port) {
        /* Create the connection object */
        DeviceConnection conn = binder.createConnection(host, port);

        /* Add this activity as a connection listener */
        binder.addListener(conn, this);

        /* Begin the async connection process */
        conn.startConnect();

        return conn;
    }

    private DeviceConnection connectOrLookupConnection(String host, int port) {
        DeviceConnection conn = binder.findConnection(host, port);
        if (conn == null) {
            /* No existing connection, so start the connection process */
            conn = startConnection(host, port);
        }
        else {
            /* Add ourselves as a new listener of this connection */
            binder.addListener(conn, this);
        }
        return conn;
    }

    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            binder = (ShellService.ShellServiceBinder)arg1;
            if (connection != null) {
                binder.removeListener(connection, MainActivity.this);
            }
            connection = connectOrLookupConnection("127.0.0.1", 5555);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            binder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(getApplicationContext(), TcpServerService.class));
        } else {
            startService(new Intent(getApplicationContext(), TcpServerService.class));
        }

        AlarmReceiver alarm = new AlarmReceiver();
        alarm.setAlarm(this);

        /* If we have old RSA keys, just use them */
        AdbCrypto crypto = AdbUtils.readCryptoConfig(getFilesDir());
        if (crypto == null)
        {
            /* We need to make a new pair */
            Log.i(LOG_TAG,
                    "This will only be done once.");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    AdbCrypto crypto;

                    crypto = AdbUtils.writeNewCryptoConfig(getFilesDir());

                    if (crypto == null)
                    {
                        Log.e(LOG_TAG,
                                "Unable to generate and save RSA key pair");
                        return;
                    }

                }
            }).start();
        }

        if (binder == null) {
            service = new Intent(this, ShellService.class);

            /* Bind the service if we're not bound already. After binding, the callback will
             * perform the initial connection. */
            getApplicationContext().bindService(service, serviceConn, Service.BIND_AUTO_CREATE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service);
            }
            else {
                startService(service);
            }
        }
    }

    static public void sendCommand(String command) {
        if(ADBConnected) {
            StringBuilder commandBuffer = new StringBuilder();

            commandBuffer.append(command);

            /* Append a newline since it's not included in the command itself */
            commandBuffer.append('\n');

            /* Send it to the device */
            connection.queueCommand(commandBuffer.toString());
        } else {
            Log.e(LOG_TAG, "sendCommand ADB is not connected!");
        }
    }

}