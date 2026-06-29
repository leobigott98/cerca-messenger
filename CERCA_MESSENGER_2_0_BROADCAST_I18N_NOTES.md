# CERCA Messenger 2.0 — Broadcast público e idiomas

## Broadcast público

Se agregó un broadcast formal para anuncios públicos usando `DestinationScope.PUBLIC_BROADCAST`.

Características:

- Conversación local: `public-broadcast`.
- Destino lógico: `cerca-public-broadcast`.
- Tipo de mensaje: `CrisisMessageType.PUBLIC_BROADCAST`.
- No espera ACK global, porque no hay un único destinatario.
- Se propaga con comportamiento tipo Epidemic controlado:
  - deduplicación por `messageId`;
  - límite de copias;
  - TTL;
  - evita reenviar a nodos que ya reportaron tener el mensaje en su summary vector.
- Se sincroniza con Firebase junto con los reportes de crisis cuando aparece Internet.

Uso recomendado:

- Anuncios de evacuación.
- Punto médico disponible.
- Refugio disponible.
- Calle bloqueada.
- Punto de acopio.
- Mensaje institucional o comunitario relevante para todos.

No usar para rumores ni mensajes no verificados.

## Idiomas

Se agregó configuración de idioma en Ajustes.

Idiomas incluidos:

- Español
- Inglés
- Francés
- Italiano
- Alemán

La internacionalización se implementó como un selector interno de idioma (`LocalizationStore`) para que el idioma de la app pueda cambiarse aunque el teléfono esté configurado en otro idioma. Esto es útil si varios rescatistas internacionales comparten equipos o si se entrega el mismo APK a equipos mixtos.

Pantallas cubiertas en esta pasada:

- Crisis
- Broadcast público
- Ajustes
- Guía
- Barra inferior
- Pantalla de permisos

Notas:

- Algunos nombres de enums internos siguen en español porque son etiquetas del modelo actual. Se pueden migrar a claves de traducción completas en una siguiente iteración.
- El broadcast público está pensado para texto breve. No enviar fotos ni videos en esta versión.
