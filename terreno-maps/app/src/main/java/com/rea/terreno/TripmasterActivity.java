package com.rea.terreno;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Locale;

/** Panel Tripmaster: velocidad, brújula, coordenadas, altitud, tiempo y sol. */
public class TripmasterActivity extends AppCompatActivity
        implements LocationListener, SensorEventListener {

    private TextView tvSpeed, tvAvg, tvMax, tvOdo, tvLatLon, tvHeading,
            tvAcc, tvAlt, tvTime, tvSun;

    private LocationManager lm;
    private SensorManager sm;
    private Sensor accel, magnet;
    private final float[] gravity = new float[3];
    private final float[] geomag = new float[3];
    private boolean haveG = false, haveM = false;

    private Location last;
    private double odometer = 0;
    private double maxSpeed = 0;
    private long startTime = 0;
    private double lastLat = 0, lastLon = 0;
    private boolean haveFix = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_tripmaster);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setNavigationOnClickListener(v -> finish());

        tvSpeed = findViewById(R.id.tvSpeed);
        tvAvg = findViewById(R.id.tvAvg);
        tvMax = findViewById(R.id.tvMax);
        tvOdo = findViewById(R.id.tvOdo);
        tvLatLon = findViewById(R.id.tvLatLon);
        tvHeading = findViewById(R.id.tvHeading);
        tvAcc = findViewById(R.id.tvAcc);
        tvAlt = findViewById(R.id.tvAlt);
        tvTime = findViewById(R.id.tvTime);
        tvSun = findViewById(R.id.tvSun);

        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        startTime = System.currentTimeMillis();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        } else {
            startLocation();
        }
        if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        if (magnet != null) sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_UI);
    }

    private void startLocation() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        } catch (SecurityException ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { lm.removeUpdates(this); } catch (SecurityException ignored) {}
        sm.unregisterListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    // ---------------- GPS ----------------

    @Override
    public void onLocationChanged(@NonNull Location loc) {
        double kmh = loc.getSpeed() * 3.6;
        if (kmh > maxSpeed) maxSpeed = kmh;
        if (last != null) odometer += last.distanceTo(loc);
        last = loc;
        lastLat = loc.getLatitude();
        lastLon = loc.getLongitude();
        haveFix = true;

        double elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0;
        double avg = elapsedSec > 0 ? (odometer / elapsedSec) * 3.6 : 0;

        tvSpeed.setText(String.format(Locale.US, "%.1f", kmh));
        tvMax.setText(String.format(Locale.US, "%.1f", maxSpeed));
        tvAvg.setText(String.format(Locale.US, "%.1f", avg));
        tvOdo.setText(String.format(Locale.US, "%.0f", odometer));
        tvLatLon.setText(String.format(Locale.US, "%.5f\n%.5f", lastLat, lastLon));
        tvAcc.setText(String.format(Locale.US, "%.0f", loc.getAccuracy()));
        tvAlt.setText(String.format(Locale.US, "%.0f", loc.getAltitude()));
        tvSun.setText(com.rea.terreno.trip.SunTimes.format(lastLat, lastLon));
        updateTime();
    }

    private void updateTime() {
        long s = (System.currentTimeMillis() - startTime) / 1000;
        tvTime.setText(String.format(Locale.US, "%02d:%02d", s / 60, s % 60));
    }

    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String p, int s, Bundle e) {}

    // ---------------- Brújula ----------------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            haveG = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, geomag, 0, 3);
            haveM = true;
        }
        if (haveG && haveM) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                float[] orient = new float[3];
                SensorManager.getOrientation(R, orient);
                double az = Math.toDegrees(orient[0]);
                if (az < 0) az += 360;
                tvHeading.setText(String.format(Locale.US, "%.0f\n%s", az, cardinal(az)));
                if (haveFix) updateTime();
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private static String cardinal(double deg) {
        String[] dirs = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSO", "SO", "OSO", "O", "ONO", "NO", "NNO"};
        return dirs[(int) Math.round(deg / 22.5) % 16];
    }
}
