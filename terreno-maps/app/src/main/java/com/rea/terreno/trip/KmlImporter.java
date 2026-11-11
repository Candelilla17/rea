package com.rea.terreno.trip;

import com.rea.terreno.data.Store;

import org.osmdroid.util.GeoPoint;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;

/** Importa puntos (Point) y recorridos (LineString) desde un archivo KML. */
public final class KmlImporter {

    private KmlImporter() {}

    public static int importKml(InputStream in, Store store) throws Exception {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setNamespaceAware(false);
        XmlPullParser p = f.newPullParser();
        p.setInput(in, null);

        int imported = 0;
        String pmName = null;
        boolean inPoint = false, inLine = false;
        StringBuilder text = new StringBuilder();

        int ev = p.getEventType();
        while (ev != XmlPullParser.END_DOCUMENT) {
            String tag = p.getName();
            if (ev == XmlPullParser.START_TAG) {
                text.setLength(0);
                if ("Placemark".equals(tag)) { pmName = null; inPoint = false; inLine = false; }
                else if ("Point".equals(tag)) inPoint = true;
                else if ("LineString".equals(tag)) inLine = true;
            } else if (ev == XmlPullParser.TEXT) {
                text.append(p.getText());
            } else if (ev == XmlPullParser.END_TAG) {
                if ("name".equals(tag)) {
                    pmName = text.toString().trim();
                } else if ("coordinates".equals(tag)) {
                    String[] tuples = text.toString().trim().split("\\s+");
                    if (inLine && tuples.length > 1) {
                        Store.NamedPath path = new Store.NamedPath(
                                pmName == null || pmName.isEmpty() ? "Recorrido KML" : pmName, "track");
                        for (String t : tuples) {
                            GeoPoint g = parse(t);
                            if (g != null) path.points.add(g);
                        }
                        if (path.points.size() > 1) { store.paths.add(path); imported++; }
                    } else if (inPoint && tuples.length >= 1) {
                        GeoPoint g = parse(tuples[0]);
                        if (g != null) {
                            Store.Waypoint w = new Store.Waypoint(
                                    pmName == null || pmName.isEmpty()
                                            ? "WPT " + (store.waypoints.size() + 1) : pmName,
                                    g.getLatitude(), g.getLongitude());
                            store.waypoints.add(w);
                            imported++;
                        }
                    }
                } else if ("Point".equals(tag)) inPoint = false;
                else if ("LineString".equals(tag)) inLine = false;
            }
            ev = p.next();
        }
        return imported;
    }

    /** "lon,lat[,alt]" -> GeoPoint. */
    private static GeoPoint parse(String tuple) {
        try {
            String[] c = tuple.split(",");
            if (c.length < 2) return null;
            double lon = Double.parseDouble(c[0].trim());
            double lat = Double.parseDouble(c[1].trim());
            return new GeoPoint(lat, lon);
        } catch (Exception e) {
            return null;
        }
    }
}
