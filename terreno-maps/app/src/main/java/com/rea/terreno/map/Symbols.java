package com.rea.terreno.map;

import com.rea.terreno.R;

/** Catálogo de símbolos (militares y ambientales) para waypoints. */
public final class Symbols {

    private Symbols() {}

    public static final String[] IDS = {
            // Genérico
            "wpt",
            // Militares / tácticos
            "friendly", "hostile", "neutral", "unknown",
            "hq", "observation", "hazard", "objective",
            // Ambientales / ecológicos
            "forest", "water", "fire", "fauna",
            "pollution", "logging", "camp", "protected"
    };

    public static final String[] LABELS = {
            "Waypoint",
            "Fuerza amiga", "Hostil", "Neutral", "Desconocido",
            "Puesto de mando", "Observación", "Peligro", "Objetivo",
            "Bosque", "Agua / río", "Incendio", "Fauna",
            "Contaminación", "Tala ilegal", "Campamento", "Área protegida"
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
            case "forest":      return R.drawable.env_forest;
            case "water":       return R.drawable.env_water;
            case "fire":        return R.drawable.env_fire;
            case "fauna":       return R.drawable.env_fauna;
            case "pollution":   return R.drawable.env_pollution;
            case "logging":     return R.drawable.env_logging;
            case "camp":        return R.drawable.env_camp;
            case "protected":   return R.drawable.env_protected;
            default:            return R.drawable.ic_waypoint;
        }
    }

    public static String label(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return LABELS[0];
    }
}
