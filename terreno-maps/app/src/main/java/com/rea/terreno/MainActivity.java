package com.rea.terreno;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.rea.terreno.data.Store;
import com.rea.terreno.map.Coords;
import com.rea.terreno.map.Reports;
import com.rea.terreno.map.Symbols;
import com.rea.terreno.map.TileSources;
import com.rea.terreno.trip.GpxExporter;
import com.rea.terreno.trip.GpxImporter;
import com.rea.terreno.trip.KmlImporter;
import com.rea.terreno.trip.TripRecorder;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.util.BoundingBox;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private enum Mode { NONE, ROUTE, SHAPE, MESSAGE, MEASURE }

    private DrawerLayout drawer;
    private MapView map;
    private TextView refPanel, statusPanel;
    private final Store store = new Store();
    private final TripRecorder recorder = new TripRecorder();

    private MyLocationNewOverlay myLocation;
    private ScaleBarOverlay scaleBar;
    private CompassOverlay compass;

    private Polyline liveTrack;
    private Polyline measureLine;
    private final List<GeoPoint> measurePoints = new ArrayList<>();
    private Store.NamedPath editingPath;
    private Mode mode = Mode.NONE;

    private boolean showWaypoints = true, showPaths = true,
            showMessages = true, showRefs = true;

    private final List<ITileSource> sources = new ArrayList<>(TileSources.all());

    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) doImport(uri);
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        drawer = findViewById(R.id.drawer);
        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));

        NavigationView nav = findViewById(R.id.nav);
        nav.setNavigationItemSelectedListener(this::onNavItem);

        map = findViewById(R.id.map);
        refPanel = findViewById(R.id.refPanel);
        statusPanel = findViewById(R.id.statusPanel);

        map.setTileSource(sources.get(0));
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(6.0);
        map.getController().setCenter(new GeoPoint(8.5380, -80.7821));

        setupOverlays();
        ((FloatingActionButton) findViewById(R.id.fabTrip))
                .setOnClickListener(v -> startActivity(new Intent(this, TripmasterActivity.class)));

        store.load(this);
        redrawAll();
        requestPerms();
        updateRefPanel();
        handleIncoming(getIntent());
    }

    private void setupOverlays() {
        myLocation = new MyLocationNewOverlay(new GpsMyLocationProvider(this), map);
        myLocation.enableMyLocation();
        map.getOverlays().add(myLocation);

        scaleBar = new ScaleBarOverlay(map);
        scaleBar.setScaleBarOffset(160, 20);
        map.getOverlays().add(scaleBar);

        compass = new CompassOverlay(this,
                new InternalCompassOrientationProvider(this), map);
        compass.enableCompass();
        map.getOverlays().add(compass);

        MapEventsOverlay events = new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) { return onMapTap(p); }
            @Override public boolean longPressHelper(GeoPoint p) { addWaypointAt(p); return true; }
        });
        map.getOverlays().add(0, events);

        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent e) { updateRefPanel(); return false; }
            @Override public boolean onZoom(ZoomEvent e) { updateRefPanel(); return false; }
        });
    }

    // ----------------------- Toolbar -----------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem rec = menu.findItem(R.id.action_record);
        if (rec != null) rec.setTitle(recorder.isRecording() ? "Detener" : "Grabar");
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_locate) { centerOnLocation(); return true; }
        if (id == R.id.action_refresh) { refreshMap(); return true; }
        if (id == R.id.action_record) { toggleRecording(); return true; }
        if (id == R.id.of_report) { addReportFlow(); return true; }
        if (id == R.id.of_offline) { downloadOffline(); return true; }
        if (id == R.id.of_deltrack) { deleteTracksDialog(); return true; }
        if (id == R.id.action_route) { toggleEditPath("route"); return true; }
        if (id == R.id.action_basemap) { chooseBasemap(); return true; }
        if (id == R.id.action_layers) { manageLayers(); return true; }
        if (id == R.id.of_goto) { gotoCoordinate(); return true; }
        if (id == R.id.of_waypoint) { addWaypointAt((GeoPoint) map.getMapCenter()); return true; }
        if (id == R.id.of_shape) { toggleEditPath("shape"); return true; }
        if (id == R.id.of_measure) { toggleMeasure(); return true; }
        if (id == R.id.of_addmap) { addMapDialog(); return true; }
        if (id == R.id.of_references) { showReferences(); return true; }
        if (id == R.id.of_import) { importLauncher.launch(new String[]{"*/*"}); return true; }
        if (id == R.id.of_export) { exportGpx(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private boolean onNavItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_tripmaster) startActivity(new Intent(this, TripmasterActivity.class));
        else if (id == R.id.nav_waypoints) showWaypointList();
        else if (id == R.id.nav_recorridos) showPathList("track");
        else if (id == R.id.nav_rutas) showPathList("route");
        else if (id == R.id.nav_shapes) showPathList("shape");
        else if (id == R.id.nav_reports) addReportFlow();
        else if (id == R.id.nav_simbologia) startActivity(new Intent(this, SimbologiaActivity.class));
        else if (id == R.id.nav_basemap) chooseBasemap();
        else if (id == R.id.nav_layers) manageLayers();
        else if (id == R.id.nav_messages) showMessages();
        else if (id == R.id.nav_prefs) showPrefs();
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) drawer.closeDrawer(GravityCompat.START);
        else super.onBackPressed();
    }

    // ----------------------- Grabación -----------------------

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
            invalidateOptionsMenu();
        } else {
            recorder.stop();
            try { lm.removeUpdates(recorder); } catch (SecurityException ignored) {}
            invalidateOptionsMenu();
            final List<GeoPoint> pts = new ArrayList<>(recorder.getPoints());
            if (liveTrack != null) { map.getOverlays().remove(liveTrack); liveTrack = null; }
            statusPanel.setVisibility(TextView.GONE);
            if (pts.size() > 1) {
                final double meters = recorder.getTotalMeters();
                final EditText in = new EditText(this);
                in.setText("Recorrido " + (count("track") + 1));
                new AlertDialog.Builder(this)
                        .setTitle("Guardar track")
                        .setMessage(String.format(Locale.US,
                                "%.0f m · %d puntos", meters, pts.size()))
                        .setView(in)
                        .setPositiveButton("Guardar", (d, w) -> {
                            Store.NamedPath p = new Store.NamedPath(in.getText().toString(), "track");
                            p.points.addAll(pts);
                            store.paths.add(p);
                            store.save(this);
                            redrawAll();
                            Toast.makeText(this, "Track guardado", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Descartar", (d, w) -> redrawAll())
                        .setCancelable(false)
                        .show();
            } else {
                redrawAll();
                Toast.makeText(this, "Track muy corto", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ----------------------- Waypoints -----------------------

    private void addWaypointAt(GeoPoint p) {
        // Paso 1: elegir símbolo táctico
        new AlertDialog.Builder(this)
                .setTitle("Símbolo del waypoint")
                .setItems(Symbols.LABELS, (d, i) -> promptWaypointName(p, Symbols.IDS[i]))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void promptWaypointName(GeoPoint p, String symbol) {
        final EditText in = new EditText(this);
        in.setText(Symbols.label(symbol) + " " + (store.waypoints.size() + 1));
        new AlertDialog.Builder(this)
                .setTitle("Nombre del waypoint")
                .setView(in)
                .setPositiveButton("Guardar", (d, w) -> {
                    Store.Waypoint wp = new Store.Waypoint(
                            in.getText().toString(), p.getLatitude(), p.getLongitude());
                    wp.alt = p.getAltitude();
                    wp.symbol = symbol;
                    store.waypoints.add(wp);
                    store.save(this);
                    redrawAll();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showWaypointList() {
        if (store.waypoints.isEmpty()) {
            Toast.makeText(this, "Sin waypoints", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> items = new ArrayList<>();
        for (Store.Waypoint w : store.waypoints)
            items.add(String.format(Locale.US, "%s (%.4f, %.4f)", w.name, w.lat, w.lon));
        new AlertDialog.Builder(this)
                .setTitle("Waypoints")
                .setItems(items.toArray(new String[0]), (d, i) -> {
                    Store.Waypoint w = store.waypoints.get(i);
                    map.getController().animateTo(new GeoPoint(w.lat, w.lon));
                    map.getController().setZoom(15.0);
                })
                .show();
    }

    // ----------------------- Rutas / shapes / recorridos -----------------------

    private void toggleEditPath(String kind) {
        if (editingPath != null && kind.equals(editingPath.kind)) {
            if (editingPath.points.size() > 1) {
                store.paths.add(editingPath);
                store.save(this);
                Toast.makeText(this, editingPath.name + " guardada", Toast.LENGTH_SHORT).show();
            }
            editingPath = null;
            mode = Mode.NONE;
            statusPanel.setVisibility(TextView.GONE);
            redrawAll();
            return;
        }
        String label = "route".equals(kind) ? "Ruta" : "Shape";
        editingPath = new Store.NamedPath(label + " " + (count(kind) + 1), kind);
        mode = "route".equals(kind) ? Mode.ROUTE : Mode.SHAPE;
        showStatus("Toca el mapa para añadir vértices. Repite "
                + ("route".equals(kind) ? "Ruta" : "Shape") + " para cerrar.");
    }

    private void showPathList(String kind) {
        List<Store.NamedPath> list = new ArrayList<>();
        for (Store.NamedPath p : store.paths) if (kind.equals(p.kind)) list.add(p);
        if (list.isEmpty()) {
            Toast.makeText(this, "Sin elementos", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> items = new ArrayList<>();
        for (Store.NamedPath p : list)
            items.add(p.name + "  ·  " + fmtDist(pathLength(p)) + "  ·  " + p.points.size() + " pts");
        new AlertDialog.Builder(this)
                .setTitle(kind)
                .setItems(items.toArray(new String[0]), (d, i) -> pathActions(list.get(i)))
                .show();
    }

    private void pathActions(Store.NamedPath p) {
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(new String[]{"Ir al inicio", "Borrar"}, (d, i) -> {
                    if (i == 0) {
                        if (!p.points.isEmpty()) {
                            map.getController().animateTo(p.points.get(0));
                            map.getController().setZoom(14.0);
                        }
                    } else {
                        store.paths.remove(p);
                        store.save(this);
                        redrawAll();
                        Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private static double pathLength(Store.NamedPath p) {
        double m = 0;
        for (int i = 1; i < p.points.size(); i++)
            m += p.points.get(i - 1).distanceToAsDouble(p.points.get(i));
        return m;
    }

    private static String fmtDist(double m) {
        return m < 1000 ? String.format(Locale.US, "%.0f m", m)
                : String.format(Locale.US, "%.2f km", m / 1000);
    }

    // ----------------------- Medida -----------------------

    private void toggleMeasure() {
        if (mode == Mode.MEASURE) {
            mode = Mode.NONE;
            measurePoints.clear();
            if (measureLine != null) { map.getOverlays().remove(measureLine); measureLine = null; }
            statusPanel.setVisibility(TextView.GONE);
            map.invalidate();
            return;
        }
        mode = Mode.MEASURE;
        measurePoints.clear();
        measureLine = new Polyline(map);
        measureLine.getOutlinePaint().setColor(0xFFFFB300);
        measureLine.getOutlinePaint().setStrokeWidth(6f);
        map.getOverlays().add(measureLine);
        showStatus("Medida: toca puntos. Repite Medida para terminar.");
    }

    private double measureTotal() {
        double m = 0;
        for (int i = 1; i < measurePoints.size(); i++)
            m += measurePoints.get(i - 1).distanceToAsDouble(measurePoints.get(i));
        return m;
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
        for (Store.Message m : store.messages)
            items.add(String.format(Locale.US, "%s  (%.4f, %.4f)", m.text, m.lat, m.lon));
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

    // ----------------------- Mapa base / capas / añadir mapa -----------------------

    private void chooseBasemap() {
        String[] names = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) names[i] = sources.get(i).name();
        new AlertDialog.Builder(this)
                .setTitle("Elige un mapa")
                .setItems(names, (d, i) -> { map.setTileSource(sources.get(i)); map.invalidate(); })
                .show();
    }

    private void addMapDialog() {
        final String[] opts = {
                "ArcGIS: NatGeo", "ArcGIS: Ocean", "ArcGIS: Topo", "ArcGIS: Calles",
                "ArcGIS: Imagery Clarity", "Google (pega tu URL con tu clave)",
                "Apple Maps (info)", "Personalizado (URL de teselas)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Añadir mapa")
                .setItems(opts, (d, i) -> {
                    String base = "https://server.arcgisonline.com/ArcGIS/rest/services/";
                    switch (i) {
                        case 0: addPreset("ArcGIS NatGeo", base + "NatGeo_World_Map/MapServer/tile/{z}/{y}/{x}", 16); break;
                        case 1: addPreset("ArcGIS Ocean", base + "Ocean/World_Ocean_Base/MapServer/tile/{z}/{y}/{x}", 13); break;
                        case 2: addPreset("ArcGIS Topo", base + "World_Topo_Map/MapServer/tile/{z}/{y}/{x}", 19); break;
                        case 3: addPreset("ArcGIS Calles", base + "World_Street_Map/MapServer/tile/{z}/{y}/{x}", 19); break;
                        case 4: addPreset("ArcGIS Imagery",
                                "https://clarity.maptiles.arcgis.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", 19); break;
                        case 5: customMapDialog("Google",
                                "Pega la URL de teselas de tu cuenta de Google (Map Tiles API) "
                                        + "con tu clave. Usar tiles de Google requiere tu propia clave y aceptar sus términos."); break;
                        case 6: new AlertDialog.Builder(this).setTitle("Apple Maps")
                                .setMessage("Apple Maps no ofrece un servidor de teselas público: solo funciona "
                                        + "con su SDK (MapKit) y tokens de Apple, así que no puede añadirse como capa aquí.")
                                .setPositiveButton("OK", null).show(); break;
                        default: customMapDialog("Personalizado",
                                "Servidor de teselas con {z}/{x}/{y} (sin clave, o el que tengas autorizado)."); break;
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addPreset(String name, String url, int maxZoom) {
        ITileSource ts = TileSources.template(name, url, maxZoom);
        sources.add(ts);
        map.setTileSource(ts);
        map.invalidate();
        Toast.makeText(this, name + " añadido", Toast.LENGTH_SHORT).show();
    }

    private void customMapDialog(String defName, String note) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        final EditText name = new EditText(this); name.setHint("Nombre del mapa"); name.setText(defName);
        final EditText url = new EditText(this); url.setHint("URL con {z}/{x}/{y}");
        box.addView(name); box.addView(url);
        new AlertDialog.Builder(this)
                .setTitle("Añadir mapa")
                .setMessage(note)
                .setView(box)
                .setPositiveButton("Añadir", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (n.isEmpty() || !u.contains("{z}")) {
                        Toast.makeText(this, "La URL debe incluir {z}/{x}/{y}", Toast.LENGTH_LONG).show();
                        return;
                    }
                    addPreset(n, u, 19);
                })
                .setNegativeButton("Cancelar", null)
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

    private void showPrefs() {
        String txt = "AJMap Pro\nMapas de campo con osmdroid, sin clave API.\n\n"
                + "Mapa activo: " + map.getTileProvider().getTileSource().name()
                + "\nWaypoints: " + store.waypoints.size()
                + "\nRecorridos/rutas/shapes: " + store.paths.size()
                + "\nMensajes: " + store.messages.size();
        new AlertDialog.Builder(this)
                .setTitle("Preferencias")
                .setMessage(txt)
                .setPositiveButton("OK", null)
                .show();
    }

    private void gotoCoordinate() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);
        final EditText latIn = new EditText(this);
        latIn.setHint("Latitud (ej. 8.9824)");
        latIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        final EditText lonIn = new EditText(this);
        lonIn.setHint("Longitud (ej. -79.5199)");
        lonIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(latIn);
        box.addView(lonIn);
        new AlertDialog.Builder(this)
                .setTitle("Ir a coordenada")
                .setMessage("Grados decimales (WGS84).")
                .setView(box)
                .setPositiveButton("Ir", (d, w) -> {
                    try {
                        double lat = Double.parseDouble(latIn.getText().toString().trim());
                        double lon = Double.parseDouble(lonIn.getText().toString().trim());
                        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                            Toast.makeText(this, "Fuera de rango", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        GeoPoint gp = new GeoPoint(lat, lon);
                        map.getController().animateTo(gp);
                        map.getController().setZoom(16.0);
                    } catch (Exception e) {
                        Toast.makeText(this, "Coordenada inválida", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Ir y marcar", (d, w) -> {
                    try {
                        double lat = Double.parseDouble(latIn.getText().toString().trim());
                        double lon = Double.parseDouble(lonIn.getText().toString().trim());
                        GeoPoint gp = new GeoPoint(lat, lon);
                        map.getController().animateTo(gp);
                        map.getController().setZoom(16.0);
                        addWaypointAt(gp);
                    } catch (Exception e) {
                        Toast.makeText(this, "Coordenada inválida", Toast.LENGTH_SHORT).show();
                    }
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

    // ----------------------- Toque del mapa -----------------------

    private boolean onMapTap(GeoPoint p) {
        if (mode == Mode.ROUTE || mode == Mode.SHAPE) {
            editingPath.points.add(p);
            redrawAll();
            return true;
        } else if (mode == Mode.MEASURE) {
            measurePoints.add(p);
            measureLine.setPoints(measurePoints);
            double m = measureTotal();
            showStatus(m < 1000
                    ? String.format(Locale.US, "Medida: %.0f m", m)
                    : String.format(Locale.US, "Medida: %.2f km", m / 1000));
            map.invalidate();
            return true;
        } else if (mode == Mode.MESSAGE) {
            addMessageAt(p);
            mode = Mode.NONE;
            return true;
        }
        return false;
    }

    // ----------------------- Importar / exportar -----------------------

    private void doImport(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("no se pudo abrir el archivo");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            byte[] data = bos.toByteArray();
            String head = new String(data, 0, Math.min(data.length, 512),
                    StandardCharsets.UTF_8).toLowerCase(Locale.US);

            int n;
            if (head.contains("<kml")) {
                n = KmlImporter.importKml(new ByteArrayInputStream(data), store);
            } else {
                n = GpxImporter.importGpx(new ByteArrayInputStream(data), store);
            }
            store.save(this);
            // encuadrar al primer elemento importado
            if (!store.paths.isEmpty() && !store.paths.get(store.paths.size() - 1).points.isEmpty()) {
                map.getController().animateTo(
                        store.paths.get(store.paths.size() - 1).points.get(0));
            } else if (!store.waypoints.isEmpty()) {
                Store.Waypoint w = store.waypoints.get(store.waypoints.size() - 1);
                map.getController().animateTo(new GeoPoint(w.lat, w.lon));
            }
            redrawAll();
            Toast.makeText(this, "Importados " + n + " elementos", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) doImport(uri);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private void exportGpx() {
        try {
            File dir = new File(getFilesDir(), "exports");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "ajmap_" + System.currentTimeMillis() + ".gpx");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(GpxExporter.toGpx(store).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/gpx+xml");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Compartir GPX"));
        } catch (Exception e) {
            Toast.makeText(this, "Error al exportar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ----------------------- Actualizar / reportes / offline -----------------------

    private void refreshMap() {
        try { map.getTileProvider().clearTileCache(); } catch (Exception ignored) {}
        store.load(this);
        redrawAll();
        map.invalidate();
        Toast.makeText(this, "Mapa actualizado", Toast.LENGTH_SHORT).show();
    }

    private void addReportFlow() {
        new AlertDialog.Builder(this)
                .setTitle("Reporte de vía")
                .setItems(Reports.LABELS, (d, i) -> {
                    GeoPoint c = (GeoPoint) map.getMapCenter();
                    store.reports.add(new Store.Report(Reports.IDS[i], c.getLatitude(), c.getLongitude()));
                    store.save(this);
                    redrawAll();
                    Toast.makeText(this, Reports.LABELS[i] + " reportado", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteTracksDialog() {
        List<Store.NamedPath> tracks = new ArrayList<>();
        for (Store.NamedPath p : store.paths) if ("track".equals(p.kind)) tracks.add(p);
        if (tracks.isEmpty()) {
            Toast.makeText(this, "No hay tracks", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[tracks.size()];
        for (int i = 0; i < tracks.size(); i++)
            names[i] = tracks.get(i).name + "  ·  " + fmtDist(pathLength(tracks.get(i)));
        new AlertDialog.Builder(this)
                .setTitle("Borrar track")
                .setItems(names, (d, i) -> {
                    Store.NamedPath t = tracks.get(i);
                    new AlertDialog.Builder(this)
                            .setMessage("¿Borrar \"" + t.name + "\"?")
                            .setPositiveButton("Borrar", (dd, w) -> {
                                store.paths.remove(t);
                                store.save(this);
                                redrawAll();
                                Toast.makeText(this, "Track borrado", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                })
                .setNeutralButton("Borrar TODOS", (d, w) ->
                    new AlertDialog.Builder(this)
                            .setMessage("¿Borrar los " + tracks.size() + " tracks?")
                            .setPositiveButton("Borrar todos", (dd, ww) -> {
                                store.paths.removeAll(tracks);
                                store.save(this);
                                redrawAll();
                                Toast.makeText(this, "Tracks borrados", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancelar", null)
                            .show())
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void downloadOffline() {
        final int zmin = (int) Math.floor(map.getZoomLevelDouble());
        final int zmax = Math.min(zmin + 3, 19);
        final BoundingBox bb = map.getBoundingBox();
        new AlertDialog.Builder(this)
                .setTitle("Descargar área offline")
                .setMessage("Guarda las teselas del área visible (zoom " + zmin + "–" + zmax
                        + ") del mapa actual para usarlo sin datos.")
                .setPositiveButton("Descargar", (d, w) -> {
                    CacheManager cm = new CacheManager(map);
                    cm.downloadAreaAsync(this, bb, zmin, zmax,
                            new CacheManager.CacheManagerCallback() {
                                @Override public void onTaskComplete() {
                                    Toast.makeText(MainActivity.this,
                                            "Área guardada para uso offline", Toast.LENGTH_LONG).show();
                                }
                                @Override public void onTaskFailed(int errors) {
                                    Toast.makeText(MainActivity.this,
                                            "Descarga con " + errors + " errores", Toast.LENGTH_LONG).show();
                                }
                                @Override public void updateProgress(int progress, int currentZoomLevel,
                                                                     int zoomMin, int zoomMax) {}
                                @Override public void downloadStarted() {
                                    Toast.makeText(MainActivity.this,
                                            "Descargando teselas…", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void setPossibleTilesInArea(int total) {
                                    Toast.makeText(MainActivity.this,
                                            total + " teselas a guardar", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String ageText(long ts) {
        long min = (System.currentTimeMillis() - ts) / 60000;
        if (min < 1) return "ahora";
        if (min < 60) return min + " min";
        long h = min / 60;
        if (h < 24) return h + " h";
        return (h / 24) + " d";
    }

    // ----------------------- Dibujo -----------------------

    private void redrawAll() {
        List<org.osmdroid.views.overlay.Overlay> keep = new ArrayList<>();
        for (org.osmdroid.views.overlay.Overlay o : map.getOverlays()) {
            if (o instanceof MapEventsOverlay || o == myLocation || o == scaleBar
                    || o == compass || o == liveTrack || o == measureLine) {
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
                    line.getOutlinePaint().setColor("route".equals(p.kind) ? 0xFF2962FF : 0xFFFF3D00);
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
                m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                m.setTitle(w.name + "  [" + Symbols.label(w.symbol) + "]");
                try {
                    m.setIcon(ContextCompat.getDrawable(this, Symbols.drawable(w.symbol)));
                } catch (Exception ignored) {}
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

        for (Store.Report rp : store.reports) {
            Marker m = new Marker(map);
            m.setPosition(new GeoPoint(rp.lat, rp.lon));
            m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            m.setTitle(Reports.label(rp.category) + "  ·  " + ageText(rp.ts));
            try {
                m.setIcon(ContextCompat.getDrawable(this, Reports.drawable(rp.category)));
            } catch (Exception ignored) {}
            map.getOverlays().add(m);
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
        if (!hasLocationPerm()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (myLocation != null) myLocation.enableMyLocation();
    }

    @Override protected void onResume() { super.onResume(); map.onResume(); }

    @Override protected void onPause() {
        super.onPause();
        map.onPause();
        store.save(this);
    }
}
