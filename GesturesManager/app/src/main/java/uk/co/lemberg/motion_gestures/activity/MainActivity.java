package uk.co.lemberg.motion_gestures.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.codekidlabs.storagechooser.StorageChooser;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.location.FusedLocationProviderClient;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.lang.Math;

import uk.co.lemberg.motion_gestures.Application;
import uk.co.lemberg.motion_gestures.R;
import uk.co.lemberg.motion_gestures.adapter.ColorsAdapter;
import uk.co.lemberg.motion_gestures.ble.BluetoothLeService;
import uk.co.lemberg.motion_gestures.ble.SensorTagServicesAPI;
import uk.co.lemberg.motion_gestures.dialogs.DialogResultListener;
import uk.co.lemberg.motion_gestures.dialogs.PromptDialog;
import uk.co.lemberg.motion_gestures.settings.AppSettings;
import uk.co.lemberg.motion_gestures.utils.Label;
import uk.co.lemberg.motion_gestures.utils.TimestampAxisFormatter;
import uk.co.lemberg.motion_gestures.utils.Utils;

public class MainActivity extends AppCompatActivity implements DialogResultListener {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final String SHOW_FILE_NAME_DLG_TAG = "file_name_dialog";
	private static final int SHOW_FILE_NAME_DLG_REQ_CODE = 1000;
	private static final int WRITE_PERMISSIONS_REQ_CODE = 1001;

	private static final int GESTURE_DURATION_MS = 1280000; // 1.28 sec
	private static final int GESTURE_SAMPLES = 128;
	/* REFRESH RATE */
	private static final int SAMPLING_PERIOD = 10000;

	// Use this to call global-like variables set up in GlobalApplication
	private Application mApp;

	private Boolean permissionGranted = false;
	private Boolean bleItemClicked = false;
	private BluetoothLeService mBluetoothLeService;
	private static HashMap<String,Integer> devicesStatus ;
	private TextView devices_connected;
	private int int_devices_connected = 0;
	private static boolean isServiceRegistered;

	// Used for Location Permission (required top enable bluetooth-related features)
	private FusedLocationProviderClient mFusedLocationClient;
	private static final int MY_PERMISSIONS_REQUEST_ACCESS_LOCATION = 100;

	public final static int REQUEST_CODE_BSCAN = 1;

	private AppSettings settings;

	private Spinner spinLabels;
	private LineChart chart;
	private ToggleButton toggleRec;
	private TextView txtStats;
	private EditText moveLabel;
	private EditText userID;

	private SensorManager sensorManager;
	private Sensor accelerometer;
	private Sensor gyroscope;

	private boolean recStarted = false;
	private long firstTimestamp = 0;
	private int selectedEntryIndex = -1;

	private long fileNameTimestamp = -1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// This variable is global and static-like
		// (i.e. all data inside is never reinitialized and accessible from everywhere)
		mApp = ((Application) getApplicationContext());
		mApp.setConnectionRequested(true);
		isServiceRegistered = false;

		setContentView(R.layout.activity_main);

        checkPermissions();

        // Permission is forced by preceding while
		Intent playI = new Intent(MainActivity.this, uk.co.lemberg.motion_gestures.ble.DeviceScanActivity.class); //Start next activity
		startActivityForResult(playI,REQUEST_CODE_BSCAN);

		// Callback on Activity end is triggered in onActivityResult(..) for Async task

		devicesStatus = new HashMap<String, Integer>();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
				mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
				if (!mBluetoothLeService.initialize()) {
					Log.e(TAG, "Unable to initialize Bluetooth");
					finish();
				}
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
				mBluetoothLeService = null;
            }
        };
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        isServiceRegistered = true;
		initViews();
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		checkPermissions();
		if(mApp.isConnectionRequested())
			bReconnect();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem menuSave = menu.findItem(R.id.action_save);
		MenuItem menuSaveAs = menu.findItem(R.id.action_save_as);

		boolean isSampleSelected = isSampleSelected();
		menuSave.setEnabled(isSampleSelected);
		menuSave.getIcon().setAlpha(isSampleSelected ? 255 : 70);

		boolean isDataAvailable = isDataAvailable();
		menuSaveAs.setEnabled(isDataAvailable);
		menuSaveAs.getIcon().setAlpha(isDataAvailable ? 255 : 70);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_working_dir:
				showWorkingDirDlg();
				return true;
			case R.id.action_load:
				showLoadDialog();
				return true;
			case R.id.action_save:
				saveSelectionDataToast(Utils.generateFileName(getCurrentLabel(), System.currentTimeMillis()));
				moveSelectionToNext();
				return true;
			case R.id.action_save_as:
				showSaveDirDlgIfNeeded();
				return true;
			case R.id.action_test:
				launchTestActivity();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	// Callback for Async Bluetooth Connection
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
			case REQUEST_CODE_BSCAN:
				bConnect();

				settings = AppSettings.getAppSettings(this);
				if(mApp.isConnectionRequested()){
					// Connected to SensorTag
				}
				else {
					// Use phone sensors
					sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
					accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
					gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
				}

				fillStatus();
				break;
		}
	}
	// region permissions stuff
	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case WRITE_PERMISSIONS_REQ_CODE: {
				// If request is cancelled, the result arrays are empty.
				if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
					// permission was granted
				} else {
					// permission denied
					showToast(getString(R.string.no_permissions));
					finish();
				}
			}
		}
	}

    // Make a basic Android Alert to request for Location Permission
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(MainActivity.this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_LOCATION);
    }

    private boolean checkPermissions() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSIONS_REQ_CODE);
			return false;
		}

		// Assume thisActivity is the current activity
		final int permissionCheck = ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION);

		// Request Location Permission if application don't already have it
		if (ContextCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			requestLocationPermission();
		} else {
			permissionGranted = true;
		}
		return true;
	}
	// endregion

	@Override
	public void onDialogResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case SHOW_FILE_NAME_DLG_REQ_CODE:
				if (resultCode == Activity.RESULT_OK) {
					String fileName = data.getStringExtra(PromptDialog.VALUE);
					saveAllDataToast(fileName);
				}
				break;
		}
	}

	private void setLineData(LineData lineData) {
		chart.setData(lineData);
	}

	private LineData getLineData() {
		return chart.getLineData();
	}

	private void initPermission(){

	}
	private void initViews() {
		spinLabels = findViewById(R.id.spinLabels);
		chart = findViewById(R.id.chart);
		toggleRec = findViewById(R.id.toggleRec);
		txtStats = findViewById(R.id.txtStats);
		moveLabel = findViewById(R.id.moveLabel);
		userID = findViewById(R.id.userID);

		toggleRec.setOnClickListener(clickListener);

		spinLabels.setAdapter(new ColorsAdapter(this, Arrays.asList(Label.values())));

		//chart.setLogEnabled(true);
		chart.setTouchEnabled(true);
		chart.setOnChartValueSelectedListener(chartValueSelectedListener);
		chart.setData(new LineData());
		getLineData().setValueTextColor(Color.WHITE);

		chart.getDescription().setEnabled(false);
		chart.getLegend().setEnabled(true);
		chart.getLegend().setTextColor(Color.WHITE);

		XAxis xAxis = chart.getXAxis();
		xAxis.setTextColor(Color.WHITE);
		xAxis.setDrawGridLines(true);
		xAxis.setAvoidFirstLastClipping(true);
		xAxis.setEnabled(true);

		xAxis.setValueFormatter(new TimestampAxisFormatter());

		YAxis leftAxis = chart.getAxisLeft();
		leftAxis.setEnabled(false);

		YAxis rightAxis = chart.getAxisRight();
		rightAxis.setTextColor(Color.WHITE);
		rightAxis.setAxisMaximum(10f);
		rightAxis.setAxisMinimum(-10f);
		rightAxis.setDrawGridLines(true);
	}

	private final View.OnClickListener clickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
				case R.id.toggleRec:
					if (recStarted) stopRecInt();
					else startRec();
					break;
			}
		}
	};

	private void startRec() {
		if (startRecInt()) {
			getLineData().clearValues();
		}
		else {
		    if(!mApp.isConnectionRequested()) {
                Toast.makeText(this, R.string.sensor_failed, Toast.LENGTH_SHORT).show();
            }
			toggleRec.setChecked(false);
		}
	}

	private boolean startRecInt() {
		if (!recStarted) {
			firstTimestamp = -1;
			fileNameTimestamp = System.currentTimeMillis();
			chart.highlightValue(null, true);
			if(!mApp.isConnectionRequested()) {
                sensorManager.registerListener(sensorEventListener, accelerometer, SAMPLING_PERIOD);
                recStarted = sensorManager.registerListener(sensorEventListener, gyroscope, SAMPLING_PERIOD);
            }
		}
		return recStarted;
	}

	private void stopRecInt() {
		if (recStarted) {
			sensorManager.unregisterListener(sensorEventListener);
			recStarted = false;
		}
	}

	private Entry previousBound = null;
	private Entry currentBound = null;

	private final OnChartValueSelectedListener chartValueSelectedListener = new OnChartValueSelectedListener() {
		@Override
		public void onValueSelected(Entry e, Highlight h) {
			previousBound = currentBound;
			currentBound = e;
			ILineDataSet set = getLineData().getDataSetByIndex(h.getDataSetIndex());

			/* Keeping the left bound as the start Index */
			if (previousBound == null || currentBound.getX() < previousBound.getX()) {
				selectedEntryIndex = set.getEntryIndex(e);
			}

			supportInvalidateOptionsMenu();
			fillStatus();

			// highlight ending line
			if (previousBound != null) {
				Highlight endHightlight = new Highlight(previousBound.getX(), previousBound.getY(), h.getDataSetIndex());
				chart.highlightValues(new Highlight[]{h, endHightlight});
			}
		}

		@Override
		public void onNothingSelected() {
			selectedEntryIndex = -1;
			supportInvalidateOptionsMenu();
			fillStatus();
		}
	};

	/**
	 *
	 * @return null if not exist
	 */
	private Entry getSelectionEndEntry() {
		int index = selectedEntryIndex + GESTURE_SAMPLES;
		if (index >= chart.getLineData().getDataSetByIndex(0).getEntryCount())
			return null;

		return chart.getLineData().getDataSetByIndex(0).getEntryForIndex(index);
	}

	private void moveSelectionToNext() {
		int current = selectedEntryIndex != -1 ? selectedEntryIndex : 0;
		current += GESTURE_SAMPLES;

		ILineDataSet dataSet = getLineData().getDataSetByIndex(0);
		while (current < dataSet.getEntryCount()) {
			Entry e = dataSet.getEntryForIndex(current);
			if (Math.abs(e.getY()) > 3) break;
			current++;
		}

		if (current == dataSet.getEntryCount())
			current = -1;
		else
		{
			current -= 20;
			if (current < -1) current = -1;
		}

		Entry e = current != -1 ? dataSet.getEntryForIndex(current) : null;
		if (e != null) {
			Highlight h = new Highlight(e.getX(), e.getY(), 0);
			chart.highlightValue(h, true);
		}
		else {
			chart.highlightValue(null, true);
		}

		supportInvalidateOptionsMenu();
		fillStatus();
	}

	private final SensorEventListener sensorEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (firstTimestamp == -1) firstTimestamp = event.timestamp;
			long entryTimestampFixed = event.timestamp - firstTimestamp;

			final float floatTimestampMicros = entryTimestampFixed / 1000000f;
			final float x = event.values[0];
			final float y = event.values[1];
			final float z = event.values[2];

			if(event.sensor.getType() != 11) {
				addPoint(getLineData(), Xa_INDEX, floatTimestampMicros, x);
				addPoint(getLineData(), Ya_INDEX, floatTimestampMicros, y);
				addPoint(getLineData(), Za_INDEX, floatTimestampMicros, z);
			}
			else {
				addPoint(getLineData(), Xg_INDEX, floatTimestampMicros, x);
				addPoint(getLineData(), Yg_INDEX, floatTimestampMicros, y);
			}

			chart.notifyDataSetChanged();
			chart.invalidate();

			supportInvalidateOptionsMenu();
			fillStatus();
		}
	};

	private boolean isDataAvailable() {
		if (getLineData().getDataSetCount() == 0) return false;
		return getLineData().getDataSetByIndex(0).getEntryCount() != 0;
	}

	private boolean isSampleSelected() {
		if (getLineData().getDataSetCount() == 0) return false;
		if (selectedEntryIndex == -1) return false;
		if (getLineData().getDataSetByIndex(0).getEntryCount() - selectedEntryIndex < GESTURE_SAMPLES) return false;
		return true;
	}

	private void fillStatus() {
		txtStats.setText(formatStatsText());
	}

	private String getMoveLabel() {
		return moveLabel.getText().toString();
	}

	private String getUserID() {
		return userID.getText().toString();
	}

	private String formatStatsText() {
		return String.format("Pos: %s/%s s\nSamples: %d", getXLabelAtHighlight(), getXLabelAtEnd(), getSamplesCount());
	}

	private String getXLabelAtHighlight() {
		if ((selectedEntryIndex == -1) || (getLineData().getDataSetCount() == 0)) return "-";
		return String.format("%.03f", getLineData().getDataSetByIndex(0).getEntryForIndex(selectedEntryIndex).getX() / 1000f);
	}

	private String getXLabelAtEnd() {
		if ((getLineData().getDataSetCount() == 0) || (getLineData().getDataSetByIndex(0).getEntryCount() == 0)) return "-";
		return String.format("%.03f", getLineData().getDataSetByIndex(0).getEntryForIndex(getLineData().getDataSetByIndex(0).getEntryCount() - 1).getX() / 1000f);
	}

	private int getSamplesCount() {
		if (getLineData().getDataSetCount() == 0) return 0;
		return getLineData().getDataSetByIndex(0).getEntryCount();
	}

	private void showLoadDialog() {
		StorageChooser chooser = new StorageChooser.Builder()
			.withActivity(MainActivity.this)
			.withFragmentManager(getFragmentManager())
			.allowCustomPath(true)
			.shouldResumeSession(true)
			.setType(StorageChooser.FILE_PICKER)
			.build();

		chooser.show();

		chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
			@Override
			public void onSelect(String path) {
				loadDataToast(path);
			}
		});
	}

	private void showWorkingDirDlg() {
		StorageChooser chooser = new StorageChooser.Builder()
			.withActivity(MainActivity.this)
			.withFragmentManager(getFragmentManager())
			.allowCustomPath(true)
			.allowAddFolder(true)
			.shouldResumeSession(true)
			.setType(StorageChooser.DIRECTORY_CHOOSER)
			.build();

		chooser.show();

		chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
			@Override
			public void onSelect(String path) {
				settings.setWorkingDir(path);
				settings.saveDeferred();
			}
		});
	}

	private void showSaveDirDlgIfNeeded() {
		if (Utils.isWorkingDirDefault(settings)) {
			StorageChooser chooser = new StorageChooser.Builder()
				.withActivity(MainActivity.this)
				.withFragmentManager(getFragmentManager())
				.allowCustomPath(true)
				.setType(StorageChooser.DIRECTORY_CHOOSER)
				.build();

			chooser.show();

			chooser.setOnSelectListener(new StorageChooser.OnSelectListener() {
				@Override
				public void onSelect(String path) {
					settings.setWorkingDir(path);
					settings.saveDeferred();

					showFileNameDlg();
				}
			});

			return;
		}

		showFileNameDlg();
	}

	private void showFileNameDlg() {
		PromptDialog dlg = PromptDialog.newInstance(SHOW_FILE_NAME_DLG_REQ_CODE, getString(R.string.enter_file_name),
			Utils.generateFileName(getCurrentLabel(), fileNameTimestamp), getString(R.string.file_name));
		dlg.show(getSupportFragmentManager(), SHOW_FILE_NAME_DLG_TAG);
	}

	private void showToast(String str) {
		Toast toast = Toast.makeText(this, str, Toast.LENGTH_SHORT);
		toast.show();
	}

	private String getCurrentLabel() {
		Label label = (Label) spinLabels.getSelectedItem();
		if (label == null) return "{null}";
		return label.toString();
	}

	private void saveSelectionDataToast(String fileName) {
		try {
			int dataSetSize = (int) (Math.abs(currentBound.getX() - previousBound.getX()) / (SAMPLING_PERIOD/1000));
			Utils.saveLineData(new File(settings.getWorkingDir(), fileName), getLineData(), selectedEntryIndex, dataSetSize, getUserID(),getMoveLabel());
			showToast(getString(R.string.data_saved));
		}
		catch (IOException e) {
			Log.e(TAG, getString(R.string.failed_to_save), e);
			showToast(getString(R.string.failed_to_save_error) + e);
		}
	}

	private void saveAllDataToast(String fileName) {
		try {
			Utils.saveLineData(new File(settings.getWorkingDir(), fileName), getLineData());
			showToast(getString(R.string.data_saved));
		}
		catch (IOException e) {
			Log.e(TAG, getString(R.string.failed_to_save), e);
			showToast(getString(R.string.failed_to_save_error) + e);
		}
	}

	private void loadDataToast(String file) {
		try {
			Pair<Utils.FileName, LineData> data = loadLineData(file);

			setLineData(data.second);
			chart.notifyDataSetChanged();
			chart.highlightValue(null, true);
			chart.invalidate();

			fileNameTimestamp = data.first.timestap;
			spinLabels.setSelection(data.first.label.ordinal());

			fillStatus();
		}
		catch (Exception e) {
			Log.e(TAG, getString(R.string.failed_to_load), e);
			showToast(getString(R.string.failed_to_load_error) + e);
		}
	}

	private void launchTestActivity() {
		startActivity(new Intent(this, TestActivity.class));
	}

	// region chart helper methods
	private static final String[] LINE_DESCRIPTIONS = {"Xa", "Ya", "Za", "Xg", "Yg", "Zg"};
	private static final int[] LINE_COLORS = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF,0xFFFF0000, 0xFF00FF00, 0xFF0000FF};

	private static final int Xa_INDEX = 0;
	private static final int Ya_INDEX = 1;
	private static final int Za_INDEX = 2;
	private static final int Xg_INDEX = 3;
	private static final int Yg_INDEX = 4;
	private static final int Zg_INDEX = 5;

	private static LineDataSet createLineDataSet(String description, int color) {
		LineDataSet set = new LineDataSet(null, description);
		set.setAxisDependency(YAxis.AxisDependency.RIGHT);
		set.setColor(color);
		set.setDrawCircles(false);
		set.setDrawCircleHole(false);
		set.setLineWidth(1f);
		set.setFillAlpha(65);
		set.setFillColor(ColorTemplate.getHoloBlue());
		set.setHighLightColor(Color.RED);
		set.setValueTextColor(Color.WHITE);
		set.setValueTextSize(9f);
		set.setDrawValues(false);
		set.setDrawHighlightIndicators(true);
		set.setDrawIcons(false);
		set.setDrawHorizontalHighlightIndicator(false);
		set.setDrawFilled(false);
		return set;
	}


	private static void addPoint(LineData data, int dataSetIndex, float x, float y) {
		ILineDataSet set = data.getDataSetByIndex(dataSetIndex);

		if (set == null) {
			set = createLineDataSet(LINE_DESCRIPTIONS[dataSetIndex], LINE_COLORS[dataSetIndex]);
			data.addDataSet(set);
		}

		data.addEntry(new Entry(x, y), dataSetIndex);

		data.notifyDataChanged();
	}

	private static Pair<Utils.FileName, LineData> loadLineData(String strFile) throws Exception {
		Pair<Utils.FileName, List<Utils.FileEntry>> pair = Utils.loadData(strFile);

		LineData lineData = new LineData();
		for (int i = 0; i < LINE_DESCRIPTIONS.length; i++) {
			lineData.addDataSet(createLineDataSet(LINE_DESCRIPTIONS[i], LINE_COLORS[i]));
		}

		for (Utils.FileEntry entry : pair.second) {
			addPoint(lineData, Xa_INDEX, entry.timestamp, entry.x);
			addPoint(lineData, Ya_INDEX, entry.timestamp, entry.y);
			addPoint(lineData, Za_INDEX, entry.timestamp, entry.z);
		}

		return new Pair<>(pair.first, lineData);
	}

	// Bluetooth utils
	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		return intentFilter;
	}

	private void bConnect() {
		if (mApp.getBluetoothDevices() != null && mApp.isConnectionRequested()) {
			for (BluetoothDevice bD : mApp.getBluetoothDevices()) {
				if (mBluetoothLeService.connect(bD.getAddress())) {
					devicesStatus.put(bD.getAddress(), R.string.connected);
					int_devices_connected++;
					// devices_connected.setText(Integer.toString(int_devices_connected));

				} else {
					// Should be useless as value for given key should already be false if this case happen
					devicesStatus.put(bD.getAddress(), R.string.disconnected);
				}
			}
		}
	}

	private void bReconnect() {
		// service not yet initialized
		if(mBluetoothLeService == null){
			return;
		}
		if (mBluetoothLeService.getmConnectionState().containsValue(mBluetoothLeService.STATE_DISCONNECTED)) {
			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which){
						case DialogInterface.BUTTON_POSITIVE:
							for (HashMap.Entry<String, Integer> entry : mBluetoothLeService.getmConnectionState().entrySet()) {
								String key = entry.getKey();
								Integer value = entry.getValue();
								if (value == mBluetoothLeService.STATE_DISCONNECTED) {
									mBluetoothLeService.connect(key);
								}
							}
							break;

						case DialogInterface.BUTTON_NEGATIVE:
							mApp.setConnectionRequested(false);
							break;
					}
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("At least one sensor is disconnected, do you want to try to reconnect ?").setPositiveButton("Yes", dialogClickListener)
					.setNegativeButton("No", dialogClickListener).show();
		}
	}

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
	//                        or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			String address = intent.getStringExtra("address");
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "device " + address + " connected", Toast.LENGTH_LONG).show();

			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "device " + address + " disconnected", Toast.LENGTH_LONG).show();
				bReconnect();
			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
				Toast.makeText(getApplicationContext(), "services discovered for device " + address, Toast.LENGTH_LONG).show();
				try {
					mBluetoothLeService.turnOnSensorTagServices();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
			    if(firstTimestamp != 0) {
                    if (firstTimestamp == -1) firstTimestamp = (System.currentTimeMillis() / 1000);
                    long entryTimestampFixed = System.currentTimeMillis() / 1000 - firstTimestamp;
                    final float floatTimestampMicros = entryTimestampFixed / 1000000f;

				if(intent.getStringExtra("characteristic").equals(SensorTagServicesAPI.accelDataUuid.toString())){
								Log.d("data received", "data " + intent.getStringExtra("characteristic") + " :" +
						" x : " + intent.getByteExtra("x",(byte) 0) +
						" y : " + intent.getByteExtra("y",(byte) 0) +
						" z : " + intent.getByteExtra("z",(byte) 0));
					addPoint(getLineData(), Xa_INDEX, floatTimestampMicros, intent.getByteExtra("x",(byte) 0));
					addPoint(getLineData(), Ya_INDEX, floatTimestampMicros, intent.getByteExtra("y",(byte) 0));
					addPoint(getLineData(), Za_INDEX, floatTimestampMicros, intent.getByteExtra("z",(byte) 0));
				}

				else if (intent.getStringExtra("characteristic").equals(SensorTagServicesAPI.gyroDataUuid.toString())) {
                        Log.d("data received", "data " + intent.getStringExtra("characteristic") + " :" +
                                " x : " + intent.getByteExtra("x", (byte) 0) +
                                " y : " + intent.getByteExtra("y", (byte) 0) +
                                " z : " + intent.getByteExtra("z", (byte) 0));
                        addPoint(getLineData(), Xa_INDEX, floatTimestampMicros, intent.getByteExtra("x", (byte) 0));
                        addPoint(getLineData(), Ya_INDEX, floatTimestampMicros, intent.getByteExtra("y", (byte) 0));
                        addPoint(getLineData(), Za_INDEX, floatTimestampMicros, intent.getByteExtra("z", (byte) 0));
                    }

                    chart.notifyDataSetChanged();
                    chart.invalidate();

                    supportInvalidateOptionsMenu();
                    fillStatus();
                }
			}
		}
	};
	// endregion
}
