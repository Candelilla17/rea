package com.rea.terreno.map;

import com.rea.terreno.R;

/** Catálogo de símbolos tácticos para waypoints. */
public final class Symbols {

    private Symbols() {}

    public static final String[] IDS = {
            "wpt", "friendly", "hostile", "neutral", "unknown",
            "hq", "observation", "hazard", "objective"
    };

    public static final String[] LABELS = {
            "Waypoint", "Fuerza amiga", "Hostil", "Neutral", "Desconocido",
            "Puesto de mando", "Observación", "Peligro", "Objetivo"
    };

    public static int drawable(String id) {
        if (id == null) return R.drawable.ic_waypoint;
        switch (id) {
            case "friendly":    return R.drawable.sym_friendly;
            case "hostile":     return R.drawable.sym_hostile;
            case "neutral":     return R.drawable.sym_neutral;
            case "unknown":     return R.drawable.sym_unknown;
            case "hq":          return R.drawable.sym_hq;
            case "observation": return R.drawable.sym_observation;
            case "hazard":      return R.drawable.sym_hazard;
            case "objective":   return R.drawable.sym_objective;
            default:            return R.drawable.ic_waypoint;
        }
    }

    public static String label(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return LABELS[0];
    }
}
