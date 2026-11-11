package com.rea.terreno.map;

import java.util.Locale;

/** Conversión de coordenadas geográficas (WGS84) a UTM, para referencias. */
public final class Coords {

    private Coords() {}

    public static String format(double lat, double lon) {
        return String.format(Locale.US, "Lat %.6f  Lon %.6f\n%s", lat, lon, utm(lat, lon));
    }

    /** lat/lon -> cadena UTM "Zona 17N  E 123456  N 1234567". */
    public static String utm(double lat, double lon) {
        final double a = 6378137.0;            // semieje mayor WGS84
        final double f = 1 / 298.257223563;    // achatamiento
        final double e2 = f * (2 - f);
        final double ep2 = e2 / (1 - e2);
        final double k0 = 0.9996;

        int zone = (int) Math.floor((lon + 180) / 6) + 1;
        double lonOrigin = (zone - 1) * 6 - 180 + 3;
        double latR = Math.toRadians(lat);
        double lonR = Math.toRadians(lon);
        double lonOriginR = Math.toRadians(lonOrigin);

        double N = a / Math.sqrt(1 - e2 * Math.sin(latR) * Math.sin(latR));
        double T = Math.tan(latR) * Math.tan(latR);
        double C = ep2 * Math.cos(latR) * Math.cos(latR);
        double A = Math.cos(latR) * (lonR - lonOriginR);

        double M = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latR
                - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024)
                    * Math.sin(2 * latR)
                + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * Math.sin(4 * latR)
                - (35 * e2 * e2 * e2 / 3072) * Math.sin(6 * latR));

        double easting = k0 * N * (A + (1 - T + C) * A * A * A / 6
                + (5 - 18 * T + T * T + 72 * C - 58 * ep2) * Math.pow(A, 5) / 120) + 500000.0;

        double northing = k0 * (M + N * Math.tan(latR) * (A * A / 2
                + (5 - T + 9 * C + 4 * C * C) * Math.pow(A, 4) / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * ep2) * Math.pow(A, 6) / 720));
        if (lat < 0) northing += 10000000.0;

        char hemi = lat >= 0 ? 'N' : 'S';
        return String.format(Locale.US, "UTM %d%c  E %.0f  N %.0f",
                zone, hemi, easting, northing);
    }
}
