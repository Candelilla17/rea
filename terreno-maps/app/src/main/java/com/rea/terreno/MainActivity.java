package com.rea.terreno;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.rea.terreno.data.Store;
import com.rea.terreno.map.Coords;
import com.rea.terreno.map.TileSources;
import com.rea.terreno.trip.GpxExporter;
import com.rea.terreno.trip.TripRecorder;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private enum Mode { NONE, ROUTE, SHAPE, MESSAGE }

    private MapView map;
    private TextView refPanel, statusPanel;
    private final Store store = new Store();
    private final TripRecorder recorder = new TripRecorder();

    private MyLocationNewOverlay myLocation;
    private ScaleBarOverlay scaleBar;
    private CompassOverlay compass;

    private Polyline liveTrack;              // recorrido en grabación
    private Store.NamedPath editingPath;     // ruta/shape en edición
    private Mode mode = Mode.NONE;

    // visibilidad de capas
    private boolean showWaypoints = true, showPaths = true,
            showMessages = true, showRefs = true;

    private List<ITileSource> sources;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);
        refPanel = findViewById(R.id.refPanel);
        statusPanel = findViewById(R.id.statusPanel);

        sources = TileSources.all();
        map.setTileSource(sources.get(0));
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(8.5380, -80.7821)); // Panamá

        setupOverlays();
        wireButtons();
        store.load(this);
        redrawAll();
        requestPerms();
        updateRefPanel();
    }

    private void setupOverlays() {
        // Mi ubicación
        myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocation.enableMyLocation();
        map.getOverlays().add(myLocation);

        // Escala
        scaleBar = new ScaleBarOverlay(map);
        scaleBar.setScaleBarOffset(160, 20);
        map.getOverlays().add(scaleBar);

        // Brújula
        compass = new CompassOverlay(this,
                new InternalCompassOrientationProvider(this), map);
        compass.enableCompass();
        map.getOverlays().add(compass);

        // Recepción de toques para rutas/shapes/mensajes
        MapEventsOverlay events = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                return onMapTap(p);
            }
            @Override public boolean longPressHelper(GeoPoint p) {
                addWaypointAt(p);
                return true;
            }
        });
        map.getOverlays().add(0, events);

        // Actualizar referencias al mover el mapa
        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { updateRefPanel(); return false; }
            @Override public boolean onZoom(ZoomEvent e) { updateRefPanel(); return false; }
        });
    }

    private void wireButtons() {
        Button rec = findViewById(R.id.btnRecord);
        rec.setOnClickListener(v -> toggleRecording());
        ((Button) findViewById(R.id.btnWaypoint))
                .setOnClickListener(v -> addWaypointAt((GeoPoint) map.getMapCenter()));
        ((Button) findViewById(R.id.btnRoute))
                .setOnClickListener(v -> toggleEditPath("route"));
        ((Button) findViewById(R.id.btnShape))
                .setOnClickListener(v -> toggleEditPath("shape"));
        ((Button) findViewById(R.id.btnLocation))
                .setOnClickListener(v -> centerOnLocation());
        FloatingActionButton fab = findViewById(R.id.fabMenu);
        fab.setOnClickListener(this::showMenu);
    }

    // ----------------------- Grabación de recorridos -----------------------

    private void toggleRecording() {
        if (!hasLocationPerm()) { requestPerms(); return; }
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!recorder.isRecording()) {
            recorder.start();
            recorder.setListener((point, meters) -> runOnUiThread(() -> {
                liveTrack.addPoint(point);
                map.invalidate();
                showStatus(String.format(Locale.US, "● REC  %.0f m  %s",
                        meters, fmtTime(recorder.getElapsedMillis())));
            }));
            liveTrack = new Polyline(map);
            liveTrack.getOutlinePaint().setColor(0xFFFF3D00);
            liveTrack.getOutlinePaint().setStrokeWidth(8f);
            map.getOverlays().add(liveTrack);
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, recorder);
            } catch (SecurityException ignored) {}
            showStatus("● REC iniciado");
        } else {
            recorder.stop();
            try { lm.removeUpdates(recorder); } catch (SecurityException ignored) {}
            if (recorder.getPoints().size() > 1) {
                Store.NamedPath p = new Store.NamedPath(
                        "Recorrido " + (count("track") + 1), "track");
                p.points.addAll(recorder.getPoints());
                store.paths.add(p);
                store.save(this);
            }
            // retirar el trazo en vivo para no duplicarlo con el guardado
            if (liveTrack != null) {
                map.getOverlays().remove(liveTrack);
                liveTrack = null;
            }
            statusPanel.setVisibility(TextView.GONE);
            redrawAll();
            Toast.makeText(this, "Recorrido guardado", Toast.LENGTH_SHORT).show();
        }
    }

    // ----------------------- Waypoints -----------------------

    private void addWaypointAt(GeoPoint p) {
        final EditText in = new EditText(this);
        in.setHint("Nombre del waypoint");
        in.setText("WPT " + (store.waypoints.size() + 1));
        new AlertDialog.Builder(this)
                .setTitle("Nuevo waypoint")
                .setView(in)
                .setPositiveButton("Guardar", (d, w) -> {
                    Store.Waypoint wp = new Store.Waypoint(
                            in.getText().toString(), p.getLatitude(), p.getLongitude());
                    wp.alt = p.getAltitude();
                    store.waypoints.add(wp);
                    store.save(this);
                    redrawAll();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ----------------------- Rutas y shapes -----------------------

    private void toggleEditPath(String kind) {
        if (editingPath != null && kind.equals(editingPath.kind)) {
            // finalizar
            if (editingPath.points.size() > 1) {
                store.paths.add(editingPath);
                store.save(this);
                Toast.makeText(this, editingPath.name + " guardada", Toast.LENGTH_SHORT).show();
            }
            editingPath = null;
            mode = Mode.NONE;
            redrawAll();
            return;
        }
        String label = "route".equals(kind) ? "Ruta" : "Shape";
        editingPath = new Store.NamedPath(label + " " + (count(kind) + 1), kind);
        mode = "route".equals(kind) ? Mode.ROUTE : Mode.SHAPE;
        showStatus("Toca el mapa para añadir vértices. Pulsa "
                + ("route".equals(kind) ? "RUTA" : "SHAPE") + " para cerrar.");
    }

    private boolean onMapTap(GeoPoint p) {
        if (mode == Mode.ROUTE || mode == Mode.SHAPE) {
            editingPath.points.add(p);
            redrawAll();
            return true;
        } else if (mode == Mode.MESSAGE) {
            addMessageAt(p);
            mode = Mode.NONE;
            return true;
        }
        return false;
    }

    // ----------------------- Mensajes -----------------------

    private void addMessageAt(GeoPoint p) {
        final EditText in = new EditText(this);
        in.setHint("Mensaje / nota de campo");
        new AlertDialog.Builder(this)
                .setTitle("Mensaje en ubicación")
                .setView(in)
                .setPositiveButton("Guardar", (d, w) -> {
                    store.messages.add(new Store.Message(
                            in.getText().toString(), p.getLatitude(), p.getLongitude()));
                    store.save(this);
                    redrawAll();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showMessages() {
        List<String> items = new ArrayList<>();
        for (Store.Message m : store.messages) {
            items.add(String.format(Locale.US, "%s  (%.4f, %.4f)", m.text, m.lat, m.lon));
        }
        if (items.isEmpty()) items.add("(sin mensajes)");
        new AlertDialog.Builder(this)
                .setTitle("Mensajes")
                .setItems(items.toArray(new String[0]), (d, i) -> {
                    if (i < store.messages.size()) {
                        Store.Message m = store.messages.get(i);
                        map.getController().animateTo(new GeoPoint(m.lat, m.lon));
                    }
                })
                .setPositiveButton("Nuevo en el centro", (d, w) ->
                        addMessageAt((GeoPoint) map.getMapCenter()))
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ----------------------- Menú (FAB) -----------------------

    private void showMenu(android.view.View anchor) {
        String[] opts = {"Elegir mapa", "Administrar capas", "Referencias",
                "Mensajes", "Exportar GPX"};
        new AlertDialog.Builder(this)
                .setItems(opts, (d, i) -> {
                    switch (i) {
                        case 0: chooseBasemap(); break;
                        case 1: manageLayers(); break;
                        case 2: showReferences(); break;
                        case 3: showMessages(); break;
                        case 4: exportGpx(); break;
                    }
                })
                .show();
    }

    private void chooseBasemap() {
        String[] names = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) names[i] = sources.get(i).name();
        new AlertDialog.Builder(this)
                .setTitle("Elegir mapa base")
                .setItems(names, (d, i) -> {
                    map.setTileSource(sources.get(i));
                    map.invalidate();
                })
                .show();
    }

    private void manageLayers() {
        String[] labels = {"Waypoints", "Recorridos / rutas / shapes",
                "Mensajes", "Panel de referencias", "Barra de escala", "Brújula"};
        boolean[] checked = {showWaypoints, showPaths, showMessages,
                showRefs, scaleBar.isEnabled(), true};
        new AlertDialog.Builder(this)
                .setTitle("Administrar capas")
                .setMultiChoiceItems(labels, checked, (d, i, isC) -> checked[i] = isC)
                .setPositiveButton("Aplicar", (d, w) -> {
                    showWaypoints = checked[0];
                    showPaths = checked[1];
                    showMessages = checked[2];
                    showRefs = checked[3];
                    scaleBar.setEnabled(checked[4]);
                    if (checked[5]) compass.enableCompass(); else compass.disableCompass();
                    refPanel.setVisibility(showRefs ? TextView.VISIBLE : TextView.GONE);
                    redrawAll();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showReferences() {
        GeoPoint c = (GeoPoint) map.getMapCenter();
        String txt = Coords.format(c.getLatitude(), c.getLongitude())
                + "\nZoom: " + String.format(Locale.US, "%.1f", map.getZoomLevelDouble())
                + "\nMapa: " + map.getTileProvider().getTileSource().name();
        new AlertDialog.Builder(this)
                .setTitle("Referencias (centro)")
                .setMessage(txt)
                .setPositiveButton("OK", null)
                .show();
    }

    private void updateRefPanel() {
        if (!showRefs || map == null) return;
        GeoPoint c = (GeoPoint) map.getMapCenter();
        refPanel.setText(Coords.format(c.getLatitude(), c.getLongitude())
                + String.format(Locale.US, "  z%.1f", map.getZoomLevelDouble()));
    }

    // ----------------------- Exportar -----------------------

    private void exportGpx() {
        try {
            File dir = new File(getFilesDir(), "exports");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "terreno_" + System.currentTimeMillis() + ".gpx");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(GpxExporter.toGpx(store).getBytes(StandardCharsets.UTF_8));
            }
            android.net.Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/gpx+xml");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Compartir GPX"));
        } catch (Exception e) {
            Toast.makeText(this, "Error al exportar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------- Dibujo de capas -----------------------

    private void redrawAll() {
        // conservar overlays base: events(0), myLocation, scaleBar, compass, liveTrack
        List<org.osmdroid.views.overlay.Overlay> keep = new ArrayList<>();
        for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) {
            if (o instanceof MapEventsOverlay || o == myLocation || o == scaleBar
                    || o == compass || o == liveTrack) {
                keep.add(o);
            }
        }
        map.getOverlays().clear();
        map.getOverlays().addAll(keep);

        if (showPaths) {
            List<Store.NamedPath> all = new ArrayList<>(store.paths);
            if (editingPath != null) all.add(editingPath);
            for (Store.NamedPath p : all) {
                if ("shape".equals(p.kind)) {
                    Polygon poly = new Polygon(map);
                    poly.setPoints(p.points);
                    poly.getFillPaint().setColor(0x552962FF);
                    poly.getOutlinePaint().setColor(0xFF2962FF);
                    poly.getOutlinePaint().setStrokeWidth(5f);
                    poly.setTitle(p.name);
                    map.getOverlays().add(poly);
                } else {
                    Polyline line = new Polyline(map);
                    line.setPoints(p.points);
                    int col = "route".equals(p.kind) ? 0xFF2962FF : 0xFFFF3D00;
                    line.getOutlinePaint().setColor(col);
                    line.getOutlinePaint().setStrokeWidth(6f);
                    line.setTitle(p.name);
                    map.getOverlays().add(line);
                }
            }
        }

        if (showWaypoints) {
            for (Store.Waypoint w : store.waypoints) {
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(w.lat, w.lon));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle(w.name);
                map.getOverlays().add(m);
            }
        }

        if (showMessages) {
            for (Store.Message msg : store.messages) {
                Marker m = new Marker(map);
                m.setPosition(new GeoPoint(msg.lat, msg.lon));
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                m.setTitle("✉ " + msg.text);
                map.getOverlays().add(m);
            }
        }
        map.invalidate();
    }

    // ----------------------- Utilidades -----------------------

    private void centerOnLocation() {
        GeoPoint p = myLocation.getMyLocation();
        if (p != null) {
            map.getController().animateTo(p);
            map.getController().setZoom(16.0);
        } else {
            Toast.makeText(this, "Ubicación aún no disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private int count(String kind) {
        int n = 0;
        for (Store.NamedPath p : store.paths) if (kind.equals(p.kind)) n++;
        return n;
    }

    private void showStatus(String s) {
        statusPanel.setVisibility(TextView.VISIBLE);
        statusPanel.setText(s);
    }

    private static String fmtTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private boolean hasLocationPerm() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerms() {
        List<String> need = new ArrayList<>();
        if (!hasLocationPerm()) need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (myLocation != null) myLocation.enableMyLocation();
    }

    @Override protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
        store.save(this);
    }
}
