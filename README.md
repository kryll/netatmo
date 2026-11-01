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

## Automatizaciones avanzadas

En el editor avanzado, los disparadores, las condiciones y las acciones de cada automatización se pueden reordenar con las flechas ▲▼ de cada elemento (por ejemplo, para que la notificación avise antes o después del cambio de modo). Las flechas solo aparecen cuando el elemento puede moverse en esa dirección.

Las acciones disponibles incluyen, además de temperatura, modos, notificación y espera:

- **Programa**: activa uno de los programas (horarios) de la instalación Netatmo y devuelve la casa al modo programado, igual que al activarlo desde la pantalla Programas.
- **Escenario**: aplica un escenario guardado en la app.
- **Automatización**: ejecuta las acciones de otra automatización guardada, solo si está activa y sus condiciones se cumplen en ese momento (sus disparadores no se evalúan: quien manda es la automatización que invoca). Las referencias en bucle o cadenas de más de 10 automatizaciones se detectan, se omiten y quedan registradas en el diagnóstico, para que una cadena nunca repita acciones de calefacción.

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

## Variables en las notificaciones de automatizaciones

En el título y el mensaje de una acción Notificación puedes escribir:

| Variable | Valor al ejecutar la acción |
| --- | --- |
| `%automatizacion%` | Nombre de la automatización |
| `%tem_ext%` | Temperatura exterior de la fuente meteorológica configurada, con el mismo fallback que los disparadores |
| `%tem_int%` | Temperatura de la estancia seleccionada |
| `%estancia%` | Nombre de esa estancia |
| `%consigna%` | Consigna de esa estancia |
| `%modo%` | Modo de esa estancia |
| `%temperaturas%` | Lista de estancias y temperaturas de la casa |

La estancia seleccionada es la de la alerta de temperatura, si existe. Sin alerta se usa la estancia más fría de la única casa disponible. Con varias casas y sin alerta no se elige una al azar: los valores generales muestran `no disponible`. Para seleccionar una estancia concreta usa su ID de Netatmo: `%tem_int:ID%`, `%consigna:ID%`, `%modo:ID%` o `%estancia:ID%`. Un ID inexistente muestra `no disponible`.

Ejemplo de título: `%automatizacion%: %tem_ext% °C fuera`.
Ejemplo de mensaje: `%estancia% está a %tem_int% °C; consigna %consigna% °C (%modo%). %temperaturas%`.

Los números usan un decimal y coma, sin unidad: añade `°C` al texto si lo deseas. Un valor ausente o no finito muestra `no disponible`; las variables desconocidas se dejan tal cual. La sustitución es de una sola pasada: los valores insertados no se vuelven a interpretar. El texto sin variables se conserva y no provoca consultas adicionales. No se cambia el formato ni la base de datos de las automatizaciones guardadas. Los datos se consultan al ejecutar cada notificación, también después de una espera o un cambio de consigna; su frescura depende de las API de Netatmo y meteorología. El estado del termostato no expone humedad, por lo que no hay variable de humedad.
