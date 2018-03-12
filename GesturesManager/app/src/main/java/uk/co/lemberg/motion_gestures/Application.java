package uk.co.lemberg.motion_gestures;

import android.bluetooth.BluetoothDevice;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;

import uk.co.lemberg.motion_gestures.settings.AppSettings;


public class Application extends android.app.Application {
	private AppSettings settings;
	private ArrayList<BluetoothDevice> bluetoothDevices;
	private boolean isConnectionRequested = false;

	public AppSettings getSettings()
	{
		return settings;
	}

	@Override
	public void onCreate() {
		settings = new AppSettings(PreferenceManager.getDefaultSharedPreferences(this));
		settings.load();

		if (settings.getWorkingDir() == null) {
			File extDir = Environment.getExternalStorageDirectory();
			if (extDir != null) {
				settings.setWorkingDir(extDir.getAbsolutePath());
				settings.saveDeferred();
			}
		}

		super.onCreate();
	}

	public void setBluetoothDevices(ArrayList<BluetoothDevice> bluetoothDevices){
		this.bluetoothDevices = bluetoothDevices;
	}

	public ArrayList<BluetoothDevice> getBluetoothDevices() {
		return this.bluetoothDevices;
	}
	public void setConnectionRequested(boolean request){
		this.isConnectionRequested = request;
	}
	public boolean isConnectionRequested(){
		return isConnectionRequested;
	}
}

