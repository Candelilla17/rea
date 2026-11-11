package com.rea.terreno.trip;

import com.rea.terreno.data.Store;

import org.osmdroid.util.GeoPoint;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/** Importa waypoints, rutas y recorridos desde un archivo GPX. */
public final class GpxImporter {

    private GpxImporter() {}

    /** Devuelve cuántos elementos (wpt/trk/rte) se importaron. */
    public static int importGpx(InputStream in, Store store) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(false);
        XmlPullParser p = f.newPullParser();
        p.setInput(in, null);

        int imported = 0;
        Store.NamedPath current = null;
        double lat = 0, lon = 0;
        String wptName = null;
        boolean inWpt = false;
        StringBuilder text = new StringBuilder();

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            String tag = p.getName();
            if (ev == XmlPullParser.START_TAG) {
                text.setLength(0);
                if ("wpt".equals(tag)) {
                    inWpt = true;
                    lat = dbl(p.getAttributeValue(null, "lat"));
                    lon = dbl(p.getAttributeValue(null, "lon"));
                    wptName = null;
                } else if ("trk".equals(tag)) {
                    current = new Store.NamedPath("Recorrido importado", "track");
                } else if ("rte".equals(tag)) {
                    current = new Store.NamedPath("Ruta importada", "route");
                } else if ("trkpt".equals(tag) || "rtept".equals(tag)) {
                    if (current != null) {
                        current.points.add(new GeoPoint(
                                dbl(p.getAttributeValue(null, "lat")),
                                dbl(p.getAttributeValue(null, "lon"))));
                    }
                }
            } else if (ev == XmlPullParser.TEXT) {
                text.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                if ("name".equals(tag) && inWpt) {
                    wptName = text.toString().trim();
                } else if ("wpt".equals(tag)) {
                    Store.Waypoint w = new Store.Waypoint(
                            wptName == null || wptName.isEmpty()
                                    ? "WPT " + (store.waypoints.size() + 1) : wptName, lat, lon);
                    store.waypoints.add(w);
                    imported++;
                    inWpt = false;
                } else if ("trk".equals(tag) || "rte".equals(tag)) {
                    if (current != null && current.points.size() > 1) {
                        store.paths.add(current);
                        imported++;
                    }
                    current = null;
                }
            }
            ev = p.next();
        }
        return imported;
    }

    private static double dbl(String s) {
        try { return s == null ? 0 : Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
