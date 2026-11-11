package com.rea.terreno.map;

import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.util.MapTileIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de mapas base SIN clave API.
 *
 * Todas las fuentes usan servidores de teselas públicos que no requieren token.
 * El patrón de URL admite los marcadores {z} {x} {y}. Para fuentes cuyo orden
 * es z/y/x (p. ej. Esri) basta con reflejar ese orden en la plantilla.
 *
 * La entrada "Topográfico ruso" es un slot configurable: el usuario coloca la
 * URL de SU propio servidor de teselas de hojas del Estado Mayor / dominio
 * público. No se incrusta ni se redistribuye ninguna app comercial.
 */
public final class TileSources {

    private TileSources() {}

    private static final TileSourcePolicy POLICY = new TileSourcePolicy(
            2,
            TileSourcePolicy.FLAG_NO_BULK
                    | TileSourcePolicy.FLAG_NO_PREVENTIVE
                    | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL
                    | TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED);

    /** Crea una fuente a partir de una plantilla con {z}/{x}/{y}. */
    public static OnlineTileSourceBase template(final String name,
                                                final String urlTemplate,
                                                final int maxZoom) {
        return new OnlineTileSourceBase(name, 0, maxZoom, 256, "",
                new String[]{""}, name, POLICY) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                final int z = MapTileIndex.getZoom(pMapTileIndex);
                final int x = MapTileIndex.getX(pMapTileIndex);
                final int y = MapTileIndex.getY(pMapTileIndex);
                return urlTemplate
                        .replace("{z}", Integer.toString(z))
                        .replace("{x}", Integer.toString(x))
                        .replace("{y}", Integer.toString(y));
            }
        };
    }

    /** Topográfico ruso: coloca aquí la URL de tu propio servidor de teselas. */
    public static final String RUSSIAN_TOPO_TEMPLATE =
            "https://tiles.example.org/russian-topo/{z}/{x}/{y}.png";

    public static List<ITileSource> all() {
        List<ITileSource> list = new ArrayList<>();
        // Callejero OSM (keyless)
        list.add(template("OSM Native",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png", 19));
        // Topográfico abierto
        list.add(template("OpenTopoMap",
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", 17));
        // Satelital Esri (keyless) — orden z/y/x
        list.add(template("Satelital (Esri)",
                "https://server.arcgisonline.com/ArcGIS/rest/services/"
                        + "World_Imagery/MapServer/tile/{z}/{y}/{x}", 19));
        // Topográfico USGS (dominio público EE. UU.)
        list.add(template("USGS Topo",
                "https://basemap.nationalmap.gov/arcgis/rest/services/"
                        + "USGSTopo/MapServer/tile/{z}/{y}/{x}", 16));
        // Relieve sombreado abierto
        list.add(template("Relieve (ESRI Terrain)",
                "https://server.arcgisonline.com/ArcGIS/rest/services/"
                        + "World_Terrain_Base/MapServer/tile/{z}/{y}/{x}", 13));
        // Topográfico ruso configurable (tu servidor)
        list.add(template("Topográfico ruso (config.)",
                RUSSIAN_TOPO_TEMPLATE, 15));
        return list;
    }

    public static ITileSource defaultSource() {
        return all().get(0);
    }
}
