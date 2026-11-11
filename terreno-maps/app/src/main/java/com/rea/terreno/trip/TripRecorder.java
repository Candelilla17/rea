package com.rea.terreno.trip;

import android.location.Location;
import android.location.LocationListener;

import androidx.annotation.NonNull;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * TripMaster: graba un recorrido a partir de las actualizaciones de GPS.
 * Acumula puntos, calcula distancia total y duración.
 */
public class TripRecorder implements LocationListener {

    public interface Listener {
        void onPoint(GeoPoint point, double totalMeters);
    }

    private final List<GeoPoint> points = new ArrayList<>();
    private boolean recording = false;
    private long startTime = 0L;
    private double totalMeters = 0d;
    private Location last = null;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    public void start() {
        points.clear();
        totalMeters = 0d;
        last = null;
        startTime = System.currentTimeMillis();
        recording = true;
    }

    public void stop() { recording = false; }

    public boolean isRecording() { return recording; }

    public List<GeoPoint> getPoints() { return points; }

    public double getTotalMeters() { return totalMeters; }

    public long getElapsedMillis() {
        return startTime == 0 ? 0 : System.currentTimeMillis() - startTime;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (!recording) return;
        if (last != null) {
            totalMeters += last.distanceTo(location);
        }
        last = location;
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude(),
                location.getAltitude());
        points.add(gp);
        if (listener != null) listener.onPoint(gp, totalMeters);
    }

    // Overrides vacíos para compatibilidad con versiones antiguas de la API.
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) {}
    @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}
}
