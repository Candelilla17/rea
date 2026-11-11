# Terreno Maps (REA)

App Android de mapas de campo construida **desde cero** con [osmdroid](https://github.com/osmdroid/osmdroid).
Sin clave API, sin dependencias de apps comerciales de terceros.

## Funciones

- **Elegir mapa**: OSM, OpenTopoMap, Satelital (Esri), USGS Topo, Relieve y un slot
  configurable para **topográficos rusos** (apunta a tu propio servidor de teselas).
- **Administrar capas**: mostrar/ocultar waypoints, recorridos/rutas/shapes, mensajes,
  panel de referencias, barra de escala y brújula.
- **TripMaster**: graba recorridos por GPS (distancia y tiempo en vivo).
- **Waypoints**: pulsación larga en el mapa o botón WPT en el centro.
- **Rutas**: botón RUTA → toca vértices → RUTA para cerrar.
- **Shapes**: botón SHAPE → toca vértices del polígono → SHAPE para cerrar.
- **Referencias**: lat/lon + UTM del centro, zoom y mapa activo.
- **Mensajes**: notas de campo ancladas a coordenadas.
- **Exportar GPX**: waypoints, rutas y recorridos, compartible.

## Compilar

El APK se compila solo en **GitHub Actions** (`.github/workflows/build-apk.yml`).
Cada push dispara el build; descarga el artefacto **TerrenoMaps-debug-apk** desde la
pestaña *Actions*.

En local (con Android Studio o SDK + JDK 17):

```bash
./gradlew assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

## Topográficos rusos

`TileSources.RUSSIAN_TOPO_TEMPLATE` apunta a un servidor de ejemplo. Sustitúyelo por la
URL de tu propio servicio de teselas de hojas del Estado Mayor / dominio público
(formato `.../{z}/{x}/{y}.png`). No se incluye ni se redistribuye ninguna app de terceros.

## Fuentes de teselas y uso

Respeta las políticas de uso de cada servidor (OSM, OpenTopoMap, Esri, USGS). Para uso
intensivo o producción, usa tu propio servidor de teselas o un plan adecuado.
Licencia del código: MIT.
