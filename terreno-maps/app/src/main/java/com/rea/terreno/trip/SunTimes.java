package com.rea.terreno.trip;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Cálculo aproximado de amanecer y ocaso (ecuación del sol) para una posición. */
public final class SunTimes {

    private SunTimes() {}

    /** Devuelve "HH:mm / HH:mm" (amanecer/ocaso) en hora local, o "--" si no aplica. */
    public static String format(double lat, double lon) {
        try {
            Calendar now = Calendar.getInstance();
            int n = now.get(Calendar.DAY_OF_YEAR);

            double lngHour = lon / 15.0;
            Double rise = calc(true, n, lat, lon, lngHour);
            Double set = calc(false, n, lat, lon, lngHour);
            if (rise == null || set == null) return "sin orto/ocaso";

            TimeZone tz = TimeZone.getDefault();
            double offset = tz.getOffset(now.getTimeInMillis()) / 3600000.0;
            return hm(rise + offset) + " / " + hm(set + offset);
        } catch (Exception e) {
            return "--";
        }
    }

    private static Double calc(boolean rise, int n, double lat, double lon, double lngHour) {
        final double ZENITH = 90.833;
        double t = n + ((rise ? 6 : 18) - lngHour) / 24.0;
        double M = (0.9856 * t) - 3.289;
        double L = M + (1.916 * sin(M)) + (0.020 * sin(2 * M)) + 282.634;
        L = mod360(L);
        double RA = atan(0.91764 * tan(L));
        RA = mod360(RA);
        double Lquadrant = Math.floor(L / 90.0) * 90.0;
        double RAquadrant = Math.floor(RA / 90.0) * 90.0;
        RA = RA + (Lquadrant - RAquadrant);
        RA = RA / 15.0;
        double sinDec = 0.39782 * sin(L);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(ZENITH)) - (sinDec * sin(lat)))
                / (cosDec * cos(lat));
        if (cosH > 1 || cosH < -1) return null; // sol no sale/pone ese día
        double H = rise ? 360.0 - acos(cosH) : acos(cosH);
        H = H / 15.0;
        double T = H + RA - (0.06571 * t) - 6.622;
        double UT = mod24(T - lngHour);
        return UT;
    }

    private static double sin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double cos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double tan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double atan(double x) { return Math.toDegrees(Math.atan(x)); }
    private static double acos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double mod360(double x) { return ((x % 360) + 360) % 360; }
    private static double mod24(double x) { return ((x % 24) + 24) % 24; }

    private static String hm(double hours) {
        double h = ((hours % 24) + 24) % 24;
        int hh = (int) h;
        int mm = (int) Math.round((h - hh) * 60);
        if (mm == 60) { mm = 0; hh = (hh + 1) % 24; }
        return String.format(Locale.US, "%02d:%02d", hh, mm);
    }
}
