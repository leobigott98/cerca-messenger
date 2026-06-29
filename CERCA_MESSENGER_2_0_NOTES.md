# CERCA Messenger 2.0 - Cambios implementados

Esta versión transforma el prototipo de chat offline en una base de **mensajería de crisis tolerante a desconexiones**.

## Cambios principales

1. **Pantalla Crisis**
   - Nueva pestaña principal `CERCA Crisis`.
   - Reportes rápidos: Estoy bien, Necesito ayuda, Reportar atrapados, Reportar heridos, Medicina, Agua/comida, Refugio y Mensaje familiar.
   - Campos estructurados: ubicación aproximada, personas afectadas, detalles y si requiere respuesta.

2. **Tipos y prioridades de crisis**
   - `CrisisMessageType` y `CrisisPriority` agregados al modelo central.
   - Prioridades P0-P5: SOS vital, Salud, Reunificación, Logística, Comunidad y Personal.
   - Los mensajes P0/P1 reciben más TTL y más copias iniciales.

3. **Estados de verificación**
   - Se añadió `VerificationStatus`: no verificado, visto por varios nodos, confirmado por voluntario, confirmado por autoridad, resuelto, duplicado y descartado.

4. **Modos de nodo**
   - Se añadió `NodeMode`: Ciudadano, Voluntario y Gateway/Centro de mando.
   - Settings permite cambiar el modo local.
   - El modo se anuncia en `HELLO` y `SUMMARY` para que otros nodos puedan priorizarlo.

5. **CERCA 2.0 en el payload y la base local**
   - `CercaMessagePayload` y `DtnMessageEntity` ahora transportan tipo de crisis, prioridad, verificación, ubicación aproximada, personas afectadas y `requiresResponse`.
   - Los summaries ahora incluyen `messageIds` para evitar duplicados.

6. **Routing más cercano a CERCA 2.0**
   - El motor de decisión suma prioridad de crisis, gateway, modo voluntario/gateway y verificación al cálculo de utilidad.
   - Los mensajes críticos se reenvían con prioridad alta.

7. **Bug de reconexión Nearby**
   - Se agregaron sets de endpoints conectados/en conexión.
   - Al perder conexión se reinicia advertising/discovery suavemente.
   - Se añadió desempate básico usando `displayName|nodeId` para evitar conexiones simultáneas duplicadas.

## Limitación importante

No pude compilar el proyecto en este entorno porque el wrapper de Gradle intenta descargar `gradle-8.11-bin.zip` y no hay acceso a internet. Debes abrirlo en Android Studio o ejecutar Gradle en una máquina con internet para validar compilación y hacer ajustes finos.

## Siguiente paso recomendado

Abrir en Android Studio, sincronizar Gradle, compilar y probar en dos teléfonos reales:

1. Crear reporte P0 en teléfono A.
2. Ver que aparece en la pestaña Crisis de A.
3. Acercar teléfono B.
4. Confirmar que el mensaje se transporta y aparece como reporte relay.
5. Alejar y volver a acercar B para validar reconexión.
