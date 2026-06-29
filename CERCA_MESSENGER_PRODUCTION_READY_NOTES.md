# CERCA Messenger 2.0 - production readiness pass

## Cambios implementados

### Guía de uso
- Nueva pestaña `Guía` con instrucciones para uso antes/durante/después del despliegue.
- Archivo `FIELD_DEPLOYMENT_CHECKLIST.md` para imprimir o compartir con el equipo.

### Contactos
- Al escanear QR, la app ya no guarda automáticamente con el nombre anunciado por el teléfono.
- Ahora pide un nombre personalizado.
- No permite guardar dos contactos diferentes con el mismo nombre visible.
- Permite renombrar contactos guardados.
- Permite borrar contactos individualmente.
- Ajustes permite borrar todos los contactos locales.

### Mensajes
- Ajustes permite borrar todos los mensajes locales.
- La eliminación es local: no borra copias que ya viajan por otros nodos ni documentos en Firebase.

### Identidad del nodo
- Se mantiene `nodeId` aleatorio estable generado por la app.
- No se usa serial, IMEI ni endpointId como identidad permanente.
- El endpointId de Nearby es temporal; cambia entre sesiones.

### Firebase
- Se agregó Firebase Auth anónimo y Cloud Firestore.
- `FirebaseCloudSyncService` sube reportes de crisis no sincronizados cuando hay Internet.
- Baja reportes activos desde Firestore para redistribuirlos offline por CERCA/Nearby.
- Se agregó botón `Ajustes → Sincronizar`.
- Se agregó `FIREBASE_SETUP_CERCA.md` con pasos, reglas e índice.

## Pendiente antes de compilar

1. Crear proyecto en Firebase.
2. Agregar app Android con package `com.leobigott.cercamessenger`.
3. Descargar `google-services.json`.
4. Copiarlo en `app/google-services.json`.
5. Activar Authentication anónimo.
6. Activar Firestore.
7. Publicar reglas de `FIREBASE_SETUP_CERCA.md`.

## Nota de compilación

No se pudo compilar en este entorno porque Gradle intenta descargar `gradle-8.11-bin.zip` desde `services.gradle.org`, y el entorno no tiene Internet. Compilar en Android Studio con Internet.
