package com.rea.terreno.map;

import com.rea.terreno.R;

/** Categorías de reportes de vía (estilo Waze, locales). */
public final class Reports {

    private Reports() {}

    public static final String[] IDS = {
            "accidente", "cierre", "bache", "reten", "peligro_via", "trafico"
    };

    public static final String[] LABELS = {
            "Accidente", "Cierre de vía", "Bache", "Retén / control", "Peligro", "Tráfico"
    };

    public static int drawable(String id) {
        if (id == null) return R.drawable.rep_peligro;
        switch (id) {
            case "accidente":   return R.drawable.rep_accidente;
            case "cierre":      return R.drawable.rep_cierre;
            case "bache":       return R.drawable.rep_bache;
            case "reten":       return R.drawable.rep_reten;
            case "trafico":     return R.drawable.rep_trafico;
            default:            return R.drawable.rep_peligro;
        }
    }

    public static String label(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return "Reporte";
    }
}
