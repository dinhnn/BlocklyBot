package com.tharvey.blocklybot;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Bluno extends Mobbob {
	private final static String TAG = Bluno.class.getSimpleName();
	private int mBaudrate=115200;	//set the default baud rate to 115200
	private String mPassword="AT+PASSWOR=DFRobot\r\n";
	private String mBaudrateBuffer = "AT+CURRUART="+mBaudrate+"\r\n";
	private static BluetoothGattCharacteristic mSCharacteristic;
	private static BluetoothGattCharacteristic mModelNumberCharacteristic;
	private static BluetoothGattCharacteristic mSerialPortCharacteristic;
	private static BluetoothGattCharacteristic mCommandCharacteristic;
	BluetoothLeService mBluetoothLeService;
	private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
			new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
	private BluetoothAdapter mBluetoothAdapter;
	private boolean mScanning =false;
	private static final int REQUEST_ENABLE_BT = 1;
	public boolean mConnected = false;

	public Bluno(Activity activity, String name, String address) {
		super(name, address);

		activity.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

		Intent gattServiceIntent = new Intent(activity, BluetoothLeService.class);
		activity.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
	}

	public void onConectionStateChange(connectionStateEnum theConnectionState) {
		switch (theConnectionState) {
			case isConnected:
				System.out.println("Connected");
				break;
			case isConnecting:
				System.out.println("Connecting");
				break;
			case isToScan:
				System.out.println("Scan");
				break;
			case isScanning:
				System.out.println("Scanning");
				break;
			case isDisconnecting:
				System.out.println("isDisconnecting");
				break;
			default:
				break;
		}
	}

	public void serialSend(String theString){
		if (mConnectionState == connectionStateEnum.isConnected) {
			mSCharacteristic.setValue(theString);
			mBluetoothLeService.writeCharacteristic(mSCharacteristic);
		}
	}

	public void serialBegin(int baud){
		mBaudrate=baud;
		mBaudrateBuffer = "AT+CURRUART="+mBaudrate+"\r\n";
	}

    private Runnable mConnectingOverTimeRunnable = new Runnable() {
		@Override
		public void run() {
			if (mConnectionState == connectionStateEnum.isConnecting)
				mConnectionState = connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}
	};

    private Runnable mDisonnectingOverTimeRunnable = new Runnable() {
		@Override
		public void run() {
			if (mConnectionState == connectionStateEnum.isDisconnecting)
				mConnectionState = connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
			mBluetoothLeService.close();
		}
	};

	public static final String SerialPortUUID="0000dfb1-0000-1000-8000-00805f9b34fb";
	public static final String CommandUUID="0000dfb2-0000-1000-8000-00805f9b34fb";
    public static final String ModelNumberStringUUID="00002a24-0000-1000-8000-00805f9b34fb";
	
	// Handles various events fired by the Service:
    //   ACTION_GATT_CONNECTED: connected to a GATT server.
    //   ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    //   ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    //   ACTION_DATA_AVAILABLE: received data from the device (read result or notification)
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			System.out.println("mGattUpdateReceiver->onReceive->action="+action);
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				System.out.println("bluno ACTION_GATT_CONNECTED");
				if (!mConnected) {
					mConnected = true;
					start();
					mHandler.removeCallbacks(mConnectingOverTimeRunnable);
				}
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				System.out.println("bluno ACTION_GATT_DISCONNECTED");
				if (mConnected) {
					mConnected = false;
					mConnectionState = connectionStateEnum.isToScan;
					onConectionStateChange(mConnectionState);
					mHandler.removeCallbacks(mDisonnectingOverTimeRunnable);
					mBluetoothLeService.close();
				}
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				// Show all the supported services and characteristics on the user interface.
				for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
			  		System.out.println("bluno ACTION_GATT_SERVICES_DISCOVERED  "+
						gattService.getUuid().toString());
				}
				getGattServices(mBluetoothLeService.getSupportedGattServices());
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				if (mSCharacteristic == mModelNumberCharacteristic) {
					if (intent.getStringExtra(BluetoothLeService.EXTRA_DATA).toUpperCase().startsWith("DF BLUNO")) {
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, false);
						mSCharacteristic=mCommandCharacteristic;
						mSCharacteristic.setValue(mPassword);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);
						mSCharacteristic.setValue(mBaudrateBuffer);
						mBluetoothLeService.writeCharacteristic(mSCharacteristic);
						mSCharacteristic=mSerialPortCharacteristic;
						mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
						mConnectionState = connectionStateEnum.isConnected;
						onConectionStateChange(mConnectionState);
					} else {
						mConnectionState = connectionStateEnum.isToScan;
						onConectionStateChange(mConnectionState);
					}
				}
				else if (mSCharacteristic==mSerialPortCharacteristic) {
					onSerialReceived(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
				}
			}
		}
	    };

	// Code to manage Service lifecycle.
	ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			System.out.println("mServiceConnection onServiceConnected");
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
			}
			mBluetoothLeService.connect(getAddress());
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			System.out.println("mServiceConnection onServiceDisconnected");
			mBluetoothLeService.disconnect();
			mBluetoothLeService = null;
		}
    };

    private void getGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null)
			return;
		String uuid;
		mModelNumberCharacteristic = null;
		mSerialPortCharacteristic = null;
		mCommandCharacteristic = null;
		mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			System.out.println("displayGattServices + uuid=" + uuid);

			List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
			ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

			// Loops through available Characteristics.
			for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
				charas.add(gattCharacteristic);
				uuid = gattCharacteristic.getUuid().toString();
				if (uuid.equals(ModelNumberStringUUID)) {
					mModelNumberCharacteristic=gattCharacteristic;
				} else if (uuid.equals(SerialPortUUID)) {
					mSerialPortCharacteristic = gattCharacteristic;
				}
				else if (uuid.equals(CommandUUID)) {
					mCommandCharacteristic = gattCharacteristic;
				}
			}
			mGattCharacteristics.add(charas);
		}

		if (mModelNumberCharacteristic==null || mSerialPortCharacteristic==null || mCommandCharacteristic==null) {
			mConnectionState = connectionStateEnum.isToScan;
			onConectionStateChange(mConnectionState);
		} else {
			mSCharacteristic=mModelNumberCharacteristic;
			mBluetoothLeService.setCharacteristicNotification(mSCharacteristic, true);
			mBluetoothLeService.readCharacteristic(mSCharacteristic);
		}
    }
    
    private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
    }
}
