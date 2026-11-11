package com.rea.terreno.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Almacén local del proyecto: waypoints, recorridos, rutas, shapes y mensajes.
 * Se serializa a un único archivo JSON en el almacenamiento interno de la app.
 */
public class Store {

    public static class Waypoint {
        public String name;
        public String note;
        public double lat, lon, alt;
        public Waypoint() {}
        public Waypoint(String name, double lat, double lon) {
            this.name = name; this.lat = lat; this.lon = lon;
        }
    }

    /** Camino nombrado: sirve para recorridos, rutas y shapes (polígonos). */
    public static class NamedPath {
        public String name;
        public String kind; // "track" | "route" | "shape"
        public final List<GeoPoint> points = new ArrayList<>();
        public NamedPath() {}
        public NamedPath(String name, String kind) { this.name = name; this.kind = kind; }
    }

    public static class Message {
        public String text;
        public double lat, lon;
        public long ts;
        public Message() {}
        public Message(String text, double lat, double lon) {
            this.text = text; this.lat = lat; this.lon = lon;
            this.ts = System.currentTimeMillis();
        }
    }

    public final List<Waypoint> waypoints = new ArrayList<>();
    public final List<NamedPath> paths = new ArrayList<>();
    public final List<Message> messages = new ArrayList<>();

    private static File file(Context c) {
        return new File(c.getFilesDir(), "proyecto.json");
    }

    public void save(Context c) {
        try {
            JSONObject root = new JSONObject();

            JSONArray wa = new JSONArray();
            for (Waypoint w : waypoints) {
                JSONObject o = new JSONObject();
                o.put("name", w.name); o.put("note", w.note == null ? "" : w.note);
                o.put("lat", w.lat); o.put("lon", w.lon); o.put("alt", w.alt);
                wa.put(o);
            }
            root.put("waypoints", wa);

            JSONArray pa = new JSONArray();
            for (NamedPath p : paths) {
                JSONObject o = new JSONObject();
                o.put("name", p.name); o.put("kind", p.kind);
                JSONArray pts = new JSONArray();
                for (GeoPoint g : p.points) {
                    JSONArray xy = new JSONArray();
                    xy.put(g.getLatitude()); xy.put(g.getLongitude()); xy.put(g.getAltitude());
                    pts.put(xy);
                }
                o.put("points", pts);
                pa.put(o);
            }
            root.put("paths", pa);

            JSONArray ma = new JSONArray();
            for (Message m : messages) {
                JSONObject o = new JSONObject();
                o.put("text", m.text); o.put("lat", m.lat); o.put("lon", m.lon); o.put("ts", m.ts);
                ma.put(o);
            }
            root.put("messages", ma);

            try (FileOutputStream fos = new FileOutputStream(file(c))) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public void load(Context c) {
        waypoints.clear(); paths.clear(); messages.clear();
        File f = file(c);
        if (!f.exists()) return;
        try {
            byte[] bytes = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int r = in.read(bytes);
                if (r <= 0) return;
            }
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));

            JSONArray wa = root.optJSONArray("waypoints");
            if (wa != null) for (int i = 0; i < wa.length(); i++) {
                JSONObject o = wa.getJSONObject(i);
                Waypoint w = new Waypoint();
                w.name = o.optString("name"); w.note = o.optString("note");
                w.lat = o.optDouble("lat"); w.lon = o.optDouble("lon"); w.alt = o.optDouble("alt");
                waypoints.add(w);
            }

            JSONArray pa = root.optJSONArray("paths");
            if (pa != null) for (int i = 0; i < pa.length(); i++) {
                JSONObject o = pa.getJSONObject(i);
                NamedPath p = new NamedPath(o.optString("name"), o.optString("kind"));
                JSONArray pts = o.optJSONArray("points");
                if (pts != null) for (int j = 0; j < pts.length(); j++) {
                    JSONArray xy = pts.getJSONArray(j);
                    p.points.add(new GeoPoint(xy.getDouble(0), xy.getDouble(1),
                            xy.length() > 2 ? xy.getDouble(2) : 0));
                }
                paths.add(p);
            }

            JSONArray ma = root.optJSONArray("messages");
            if (ma != null) for (int i = 0; i < ma.length(); i++) {
                JSONObject o = ma.getJSONObject(i);
                Message m = new Message();
                m.text = o.optString("text"); m.lat = o.optDouble("lat");
                m.lon = o.optDouble("lon"); m.ts = o.optLong("ts");
                messages.add(m);
            }
        } catch (Exception ignored) {}
    }
}
