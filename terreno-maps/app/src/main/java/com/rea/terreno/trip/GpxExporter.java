package com.rea.terreno.trip;

import com.rea.terreno.data.Store;

import org.osmdroid.util.GeoPoint;

import java.util.Locale;

/** Genera un documento GPX 1.1 con waypoints, rutas y recorridos. */
public final class GpxExporter {

    private GpxExporter() {}

    public static String toGpx(Store store) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"REA Terreno Maps\" ")
          .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");

        for (Store.Waypoint w : store.waypoints) {
            sb.append(String.format(Locale.US,
                    "  <wpt lat=\"%.7f\" lon=\"%.7f\"><ele>%.1f</ele><name>%s</name></wpt>\n",
                    w.lat, w.lon, w.alt, esc(w.name)));
        }

        for (Store.NamedPath p : store.paths) {
            if ("route".equals(p.kind) || "shape".equals(p.kind)) {
                sb.append("  <rte><name>").append(esc(p.name)).append("</name>\n");
                for (GeoPoint g : p.points) {
                    sb.append(String.format(Locale.US,
                            "    <rtept lat=\"%.7f\" lon=\"%.7f\"></rtept>\n",
                            g.getLatitude(), g.getLongitude()));
                }
                sb.append("  </rte>\n");
            } else {
                sb.append("  <trk><name>").append(esc(p.name)).append("</name><trkseg>\n");
                for (GeoPoint g : p.points) {
                    sb.append(String.format(Locale.US,
                            "    <trkpt lat=\"%.7f\" lon=\"%.7f\"><ele>%.1f</ele></trkpt>\n",
                            g.getLatitude(), g.getLongitude(), g.getAltitude()));
                }
                sb.append("  </trkseg></trk>\n");
            }
        }

        sb.append("</gpx>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
