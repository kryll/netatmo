# Netatmo Smart — App Android

Aplicación Android para automatizar el termostato Netatmo con control inteligente basado en ubicación, calendario, programaciones y escenarios.

## Características

### 🏠 Control del termostato
- Temperatura actual y objetivo por habitación
- Control manual con slider y presets rápidos
- Cambio de modo: Manual, Programado, Ausente, Anticongelación, Apagado
- Estado en tiempo real: calefacción activa, batería de módulos, conectividad

### 📍 Automatización por ubicación (Geovalla)
- Define zonas geográficas con radio configurable (50m–2km)
- Acción al **llegar** a casa: sube la temperatura
- Acción al **salir** de casa: baja la temperatura o modo ausente
- Usa el GPS del teléfono en segundo plano

### 📅 Automatización por Calendario
- Conecta con cualquier calendario del dispositivo (Google Calendar, etc.)
- Filtra eventos por título (ej: "Reunión", "Trabajo")
- Precalienta la habitación N minutos antes del evento
- Se comprueba cada 15 minutos en background

### 📊 Estadísticas y Gráficos
- Historial de temperatura con gráficos nativos
- Temperatura media, máxima y mínima
- Horas de calefacción activa
- Periodos: últimas 24h, 7 días, 30 días

### ⚡ Escenarios y Accesos Rápidos
- **Accesos rápidos**: Confort, Eco, Ausente, Apagado con un toque
- **Escenarios personalizados**: aplica diferentes temperaturas a varias habitaciones simultáneamente

### ⚙️ Ajustes
- Selección de hogar Netatmo
- Sincronización automática configurable
- Notificaciones de automatizaciones

---

## Configuración inicial

### 1. Crear aplicación en Netatmo
1. Ve a [dev.netatmo.com/apps](https://dev.netatmo.com/apps)
2. Crea una nueva aplicación
3. Anota el **Client ID** y **Client Secret**
4. En "Redirect URI" añade: `com.arsys.netatmo://oauth`

### 2. Configurar credenciales
Edita `app/build.gradle.kts` y reemplaza los valores:

```kotlin
buildConfigField("String", "NETATMO_CLIENT_ID", "\"TU_CLIENT_ID\"")
buildConfigField("String", "NETATMO_CLIENT_SECRET", "\"TU_CLIENT_SECRET\"")
```

### 3. Configurar SDK de Android
Copia `local.properties.template` a `local.properties` y establece la ruta de tu Android SDK.

### 4. Compilar
```bash
./gradlew assembleDebug
```

---

## Arquitectura

```
app/
├── data/
│   ├── api/          # Retrofit — Netatmo Connect API
│   ├── local/        # Room DB — automatizaciones, historial, caché
│   └── repository/   # Fuente única de verdad
├── domain/
│   ├── model/        # Modelos de dominio
│   └── usecase/      # Casos de uso
├── ui/
│   ├── screens/      # Composables por pantalla
│   ├── components/   # Componentes reutilizables
│   ├── navigation/   # NavGraph con bottom nav
│   └── theme/        # Material 3 con colores personalizados
├── service/          # GeofenceManager, GeofenceTransitionService
└── worker/           # WorkManager: DataSyncWorker, CalendarWorker
```

**Stack tecnológico:**
- Kotlin + Jetpack Compose (Material 3)
- Hilt (inyección de dependencias)
- Room (base de datos local)
- Retrofit + OkHttp (API REST)
- WorkManager (tareas en background)
- Google Location Services (geovalla)
- DataStore Preferences (configuración)

---

## Permisos requeridos

| Permiso | Uso |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS para geovalla |
| `ACCESS_BACKGROUND_LOCATION` | Detectar entrada/salida fuera de la app |
| `READ_CALENDAR` | Leer eventos de calendario |
| `INTERNET` | Comunicación con API Netatmo |
| `RECEIVE_BOOT_COMPLETED` | Reactivar background al reiniciar |
| `POST_NOTIFICATIONS` | Avisos de automatizaciones |

---

## API Netatmo utilizada

- `GET /api/homesdata` — Datos del hogar y dispositivos
- `GET /api/homestatus` — Estado actual (temperatura, modo, batería)
- `POST /api/setroomthermpoint` — Cambiar temperatura/modo de una habitación
- `POST /api/setthermmode` — Modo global del hogar
- `POST /api/switchhomeschedule` — Activar programación
- `GET /api/getroommeasure` — Historial de temperatura

---

## Licencia

MIT
