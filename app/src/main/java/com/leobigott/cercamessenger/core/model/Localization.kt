package com.leobigott.cercamessenger.core.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight in-app localization store for the field build.
 * This keeps the UI language independent from the phone language, useful when
 * international rescue teams share the same APK.
 */
enum class AppLanguage(val code: String, val label: String) {
    ES("es", "Español"),
    EN("en", "English"),
    FR("fr", "Français"),
    IT("it", "Italiano"),
    DE("de", "Deutsch")
}

data class Strings(
    val crisisTitle: String,
    val nearbyTitle: String,
    val chatDescription: String,
    val quickReport: String,
    val quickReportDescription: String,
    val publicBroadcast: String,
    val publicBroadcastDescription: String,
    val priority: String,
    val approximateLocation: String,
    val locationPlaceholder: String,
    val peopleAffected: String,
    val details: String,
    val requiresResponse: String,
    val sending: String,
    val createCrisisReport: String,
    val createPublicBroadcast: String,
    val transportedReports: String,
    val publicAnnouncements: String,
    val verification: String,
    val location: String,
    val hops: String,
    val copies: String,
    val status: String,
    val bottomCrisis: String,
    val bottomChats: String,
    val bottomNearby: String,
    val bottomGuide: String,
    val bottomContacts: String,
    val bottomSettings: String,
    val settings: String,
    val language: String,
    val languageDescription: String,
    val activeCrisis: String,
    val activeCrisisDescription: String,
    val foregroundService: String,
    val foregroundServiceDescription: String,
    val showRoutingMetadata: String,
    val showRoutingMetadataDescription: String,
    val nodeMode: String,
    val nodeModeDescription: String,
    val firebase: String,
    val firebaseDescription: String,
    val sync: String,
    val deleteLocalMessages: String,
    val deleteLocalMessagesDescription: String,
    val deleteLocalContacts: String,
    val deleteLocalContactsDescription: String,
    val delete: String,
    val yesDelete: String,
    val cancel: String,
    val deleteMessagesDialog: String,
    val deleteMessagesDialogBody: String,
    val deleteContactsDialog: String,
    val deleteContactsDialogBody: String,
    val baseCopies: String,
    val ttl: String,
    val localMessagesDeleted: String,
    val localContactsDeleted: String,
    val firebaseSyncRequested: String,
    val unexpectedError: String,
    val grantPermissions: String,
    val permissionsText: String,
    val guideTitle: String,
    val guideBody: String
)

object LocalizationStore {
    private const val PREFS = "cerca_settings"
    private const val KEY_LANGUAGE = "app_language"

    private val _language = MutableStateFlow(AppLanguage.ES)
    val language: StateFlow<AppLanguage> = _language

    fun init(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.ES.name) ?: AppLanguage.ES.name
        _language.value = runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.ES)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.name)
            .apply()
        _language.value = language
    }

    fun strings(language: AppLanguage = _language.value): Strings = when (language) {
        AppLanguage.ES -> Strings(
            crisisTitle = "CERCA Crisis",
            nearbyTitle = "Dispositivos Cercanos",
            chatDescription = "La detección de proximidad y la retransmisión cifrada se ejecutan a través del motor del protocolo CERCA.",
            quickReport = "Reporte rápido",
            quickReportDescription = "Crea mensajes accionables para que CERCA los transporte por la red DTN hasta voluntarios o gateways.",
            publicBroadcast = "Anuncio público",
            publicBroadcastDescription = "Difusión para todos: visible para cualquier nodo cercano, con reenvío tipo Epidemic limitado por copias y TTL.",
            priority = "Prioridad",
            approximateLocation = "Ubicación aproximada",
            locationPlaceholder = "Ej. La Guaira, edificio X, piso 2",
            peopleAffected = "Personas afectadas",
            details = "Detalles",
            requiresResponse = "Requiere respuesta",
            sending = "Enviando...",
            createCrisisReport = "Crear reporte de crisis",
            createPublicBroadcast = "Crear anuncio público",
            transportedReports = "Reportes transportados",
            publicAnnouncements = "Anuncios públicos",
            verification = "Verificación",
            location = "Ubicación",
            hops = "Saltos",
            copies = "Copias",
            status = "Estado",
            bottomCrisis = "Crisis",
            bottomChats = "Chats",
            bottomNearby = "Cercanos",
            bottomGuide = "Guía",
            bottomContacts = "Contactos",
            bottomSettings = "Ajustes",
            settings = "Ajustes",
            language = "Idioma",
            languageDescription = "Útil para equipos internacionales de rescate.",
            activeCrisis = "CERCA crisis activo",
            activeCrisisDescription = "Nearby/DTN sigue siendo el canal principal. Firebase solo actúa como puente cuando aparece Internet.",
            foregroundService = "Servicio mesh en primer plano",
            foregroundServiceDescription = "Discovery/advertising corre mediante un servicio foreground después de conceder permisos.",
            showRoutingMetadata = "Mostrar metadatos de enrutamiento",
            showRoutingMetadataDescription = "Muestra saltos, copias, relay candidato y score CERCA.",
            nodeMode = "Modo del nodo",
            nodeModeDescription = "Define cómo otros nodos priorizan este teléfono: ciudadano, voluntario o gateway/centro de mando.",
            firebase = "Firebase",
            firebaseDescription = "Sube reportes locales y baja reportes activos cuando haya Internet.",
            sync = "Sincronizar",
            deleteLocalMessages = "Borrar mensajes locales",
            deleteLocalMessagesDescription = "Limpia este teléfono sin afectar otros nodos.",
            deleteLocalContacts = "Borrar contactos locales",
            deleteLocalContactsDescription = "Elimina contactos guardados y sus claves públicas locales.",
            delete = "Borrar",
            yesDelete = "Sí, borrar",
            cancel = "Cancelar",
            deleteMessagesDialog = "Borrar mensajes locales",
            deleteMessagesDialogBody = "Esto borra los mensajes almacenados en este teléfono. No borra mensajes que ya estén en otros nodos ni en Firebase.",
            deleteContactsDialog = "Borrar contactos locales",
            deleteContactsDialogBody = "Esto borra contactos y claves públicas guardadas localmente. Tendrás que volver a escanear QR para enviar mensajes cifrados.",
            baseCopies = "8 copias base; mensajes P0/P1 y anuncios públicos reciben más copias automáticamente.",
            ttl = "60 minutos base; mensajes críticos y anuncios públicos duran más.",
            localMessagesDeleted = "Mensajes locales borrados.",
            localContactsDeleted = "Contactos locales borrados.",
            firebaseSyncRequested = "Sincronización Firebase solicitada.",
            unexpectedError = "Error inesperado.",
            grantPermissions = "Conceder permisos",
            permissionsText = "CERCA necesita permisos de dispositivos cercanos para descubrir y cambiar mensajes offline.",
            guideTitle = "Guía de uso",
            guideBody = "1. Elige tu modo: ciudadano, voluntario o gateway.\n2. Usa Crisis para reportes urgentes.\n3. Usa Anuncio público para mensajes dirigidos a todos.\n4. Mantén Bluetooth y Wi‑Fi activos.\n5. Sincroniza Firebase solo cuando haya Internet."
        )
        AppLanguage.EN -> Strings(
            crisisTitle = "CERCA Crisis", nearbyTitle = "Nearby Devices", chatDescription = "Nearby discovery and encrypted relaying run through the CERCA protocol engine",  quickReport = "Quick report", quickReportDescription = "Create actionable messages so CERCA can carry them through the DTN network to volunteers or gateways.", publicBroadcast = "Public broadcast", publicBroadcastDescription = "Broadcast to everyone: visible to any nearby node, with Epidemic-like forwarding limited by copies and TTL.", priority = "Priority", approximateLocation = "Approximate location", locationPlaceholder = "E.g. La Guaira, building X, floor 2", peopleAffected = "People affected", details = "Details", requiresResponse = "Requires response", sending = "Sending...", createCrisisReport = "Create crisis report", createPublicBroadcast = "Create public broadcast", transportedReports = "Carried reports", publicAnnouncements = "Public announcements", verification = "Verification", location = "Location", hops = "Hops", copies = "Copies", status = "Status", bottomCrisis = "Crisis", bottomChats = "Chats", bottomNearby = "Nearby", bottomGuide = "Guide", bottomContacts = "Contacts", bottomSettings = "Settings", settings = "Settings", language = "Language", languageDescription = "Useful for international rescue teams.", activeCrisis = "CERCA crisis active", activeCrisisDescription = "Nearby/DTN remains the main channel. Firebase only acts as a bridge when Internet appears.", foregroundService = "Foreground mesh service", foregroundServiceDescription = "Discovery/advertising runs through a foreground service after permissions are granted.", showRoutingMetadata = "Show routing metadata", showRoutingMetadataDescription = "Shows hops, copies, relay candidate and CERCA score.", nodeMode = "Node mode", nodeModeDescription = "Defines how other nodes prioritize this phone: citizen, volunteer or gateway/command center.", firebase = "Firebase", firebaseDescription = "Uploads local reports and downloads active reports when Internet is available.", sync = "Sync", deleteLocalMessages = "Delete local messages", deleteLocalMessagesDescription = "Cleans this phone without affecting other nodes.", deleteLocalContacts = "Delete local contacts", deleteLocalContactsDescription = "Deletes saved contacts and local public keys.", delete = "Delete", yesDelete = "Yes, delete", cancel = "Cancel", deleteMessagesDialog = "Delete local messages", deleteMessagesDialogBody = "This deletes messages stored on this phone. It does not delete messages already on other nodes or in Firebase.", deleteContactsDialog = "Delete local contacts", deleteContactsDialogBody = "This deletes contacts and public keys saved locally. You will need to scan QR codes again for encrypted messages.", baseCopies = "8 base copies; P0/P1 messages and public broadcasts receive more copies automatically.", ttl = "60 minutes base; critical messages and public broadcasts last longer.", localMessagesDeleted = "Local messages deleted.", localContactsDeleted = "Local contacts deleted.", firebaseSyncRequested = "Firebase sync requested.", unexpectedError = "Unexpected error.", grantPermissions = "Grant permissions", permissionsText = "CERCA needs nearby-device permissions to discover and exchange offline messages.", guideTitle = "User guide", guideBody = "1. Choose your mode: citizen, volunteer or gateway.\n2. Use Crisis for urgent reports.\n3. Use Public broadcast for messages meant for everyone.\n4. Keep Bluetooth and Wi‑Fi on.\n5. Sync Firebase only when Internet is available."
        )
        AppLanguage.FR -> Strings(
            crisisTitle = "CERCA Crise", nearbyTitle = "Appareils à Proximité", chatDescription = "Nearby discovery and encrypted relaying run through the CERCA protocol engine", quickReport = "Rapport rapide", quickReportDescription = "Créez des messages exploitables afin que CERCA les transporte par le réseau DTN vers des bénévoles ou des passerelles.", publicBroadcast = "Annonce publique", publicBroadcastDescription = "Diffusion à tous : visible par tout nœud proche, avec relais de type Epidemic limité par les copies et le TTL.", priority = "Priorité", approximateLocation = "Localisation approximative", locationPlaceholder = "Ex. La Guaira, bâtiment X, étage 2", peopleAffected = "Personnes touchées", details = "Détails", requiresResponse = "Nécessite une réponse", sending = "Envoi...", createCrisisReport = "Créer un rapport de crise", createPublicBroadcast = "Créer une annonce publique", transportedReports = "Rapports transportés", publicAnnouncements = "Annonces publiques", verification = "Vérification", location = "Localisation", hops = "Sauts", copies = "Copies", status = "État", bottomCrisis = "Crise", bottomChats = "Chats", bottomNearby = "Nearby", bottomGuide = "Guide", bottomContacts = "Contacts", bottomSettings = "Réglages", settings = "Réglages", language = "Langue", languageDescription = "Utile pour les équipes internationales de secours.", activeCrisis = "CERCA crise actif", activeCrisisDescription = "Nearby/DTN reste le canal principal. Firebase sert seulement de pont lorsqu'Internet est disponible.", foregroundService = "Service mesh au premier plan", foregroundServiceDescription = "La découverte/publication fonctionne via un service au premier plan après autorisation.", showRoutingMetadata = "Afficher les métadonnées de routage", showRoutingMetadataDescription = "Affiche les sauts, copies, relais candidat et score CERCA.", nodeMode = "Mode du nœud", nodeModeDescription = "Définit comment les autres nœuds priorisent ce téléphone : citoyen, bénévole ou passerelle/centre de commandement.", firebase = "Firebase", firebaseDescription = "Téléverse les rapports locaux et télécharge les rapports actifs quand Internet est disponible.", sync = "Synchroniser", deleteLocalMessages = "Supprimer les messages locaux", deleteLocalMessagesDescription = "Nettoie ce téléphone sans affecter les autres nœuds.", deleteLocalContacts = "Supprimer les contacts locaux", deleteLocalContactsDescription = "Supprime les contacts enregistrés et leurs clés publiques locales.", delete = "Supprimer", yesDelete = "Oui, supprimer", cancel = "Annuler", deleteMessagesDialog = "Supprimer les messages locaux", deleteMessagesDialogBody = "Cela supprime les messages stockés sur ce téléphone. Les messages déjà présents sur d'autres nœuds ou dans Firebase ne sont pas supprimés.", deleteContactsDialog = "Supprimer les contacts locaux", deleteContactsDialogBody = "Cela supprime les contacts et clés publiques enregistrés localement. Il faudra rescanner les QR pour les messages chiffrés.", baseCopies = "8 copies de base ; les messages P0/P1 et annonces publiques reçoivent plus de copies automatiquement.", ttl = "60 minutes de base ; les messages critiques et annonces publiques durent plus longtemps.", localMessagesDeleted = "Messages locaux supprimés.", localContactsDeleted = "Contacts locaux supprimés.", firebaseSyncRequested = "Synchronisation Firebase demandée.", unexpectedError = "Erreur inattendue.", grantPermissions = "Autoriser", permissionsText = "CERCA a besoin des autorisations d'appareils proches pour découvrir et échanger des messages hors ligne.", guideTitle = "Guide d'utilisation", guideBody = "1. Choisissez votre mode : citoyen, bénévole ou passerelle.\n2. Utilisez Crise pour les rapports urgents.\n3. Utilisez Annonce publique pour les messages destinés à tous.\n4. Gardez Bluetooth et Wi‑Fi activés.\n5. Synchronisez Firebase seulement avec Internet."
        )
        AppLanguage.IT -> Strings(
            crisisTitle = "CERCA Crisi", nearbyTitle = "Dispositivi Nelle Vicinanze", chatDescription = "Nearby discovery and encrypted relaying run through the CERCA protocol engine", quickReport = "Segnalazione rapida", quickReportDescription = "Crea messaggi operativi affinché CERCA li trasporti nella rete DTN verso volontari o gateway.", publicBroadcast = "Annuncio pubblico", publicBroadcastDescription = "Diffusione a tutti: visibile a qualsiasi nodo vicino, con inoltro simile a Epidemic limitato da copie e TTL.", priority = "Priorità", approximateLocation = "Posizione approssimativa", locationPlaceholder = "Es. La Guaira, edificio X, piano 2", peopleAffected = "Persone coinvolte", details = "Dettagli", requiresResponse = "Richiede risposta", sending = "Invio...", createCrisisReport = "Crea segnalazione di crisi", createPublicBroadcast = "Crea annuncio pubblico", transportedReports = "Segnalazioni trasportate", publicAnnouncements = "Annunci pubblici", verification = "Verifica", location = "Posizione", hops = "Salti", copies = "Copie", status = "Stato", bottomCrisis = "Crisi", bottomChats = "Chat", bottomNearby = "Nearby", bottomGuide = "Guida", bottomContacts = "Contatti", bottomSettings = "Impostazioni", settings = "Impostazioni", language = "Lingua", languageDescription = "Utile per squadre internazionali di soccorso.", activeCrisis = "CERCA crisi attivo", activeCrisisDescription = "Nearby/DTN resta il canale principale. Firebase agisce solo da ponte quando c'è Internet.", foregroundService = "Servizio mesh in primo piano", foregroundServiceDescription = "Discovery/advertising funziona tramite un servizio in primo piano dopo i permessi.", showRoutingMetadata = "Mostra metadati di instradamento", showRoutingMetadataDescription = "Mostra salti, copie, relay candidato e punteggio CERCA.", nodeMode = "Modalità nodo", nodeModeDescription = "Definisce come altri nodi priorizzano questo telefono: cittadino, volontario o gateway/centro di comando.", firebase = "Firebase", firebaseDescription = "Carica segnalazioni locali e scarica segnalazioni attive quando c'è Internet.", sync = "Sincronizza", deleteLocalMessages = "Elimina messaggi locali", deleteLocalMessagesDescription = "Pulisce questo telefono senza influire sugli altri nodi.", deleteLocalContacts = "Elimina contatti locali", deleteLocalContactsDescription = "Elimina contatti salvati e chiavi pubbliche locali.", delete = "Elimina", yesDelete = "Sì, elimina", cancel = "Annulla", deleteMessagesDialog = "Elimina messaggi locali", deleteMessagesDialogBody = "Elimina i messaggi memorizzati su questo telefono. Non elimina quelli già su altri nodi o in Firebase.", deleteContactsDialog = "Elimina contatti locali", deleteContactsDialogBody = "Elimina contatti e chiavi pubbliche salvati localmente. Dovrai scansionare di nuovo i QR per messaggi cifrati.", baseCopies = "8 copie base; messaggi P0/P1 e annunci pubblici ricevono più copie automaticamente.", ttl = "60 minuti base; messaggi critici e annunci pubblici durano di più.", localMessagesDeleted = "Messaggi locali eliminati.", localContactsDeleted = "Contatti locali eliminati.", firebaseSyncRequested = "Sincronizzazione Firebase richiesta.", unexpectedError = "Errore imprevisto.", grantPermissions = "Concedi permessi", permissionsText = "CERCA richiede permessi per dispositivi vicini per scoprire e scambiare messaggi offline.", guideTitle = "Guida d'uso", guideBody = "1. Scegli la modalità: cittadino, volontario o gateway.\n2. Usa Crisi per segnalazioni urgenti.\n3. Usa Annuncio pubblico per messaggi destinati a tutti.\n4. Mantieni Bluetooth e Wi‑Fi attivi.\n5. Sincronizza Firebase solo con Internet."
        )
        AppLanguage.DE -> Strings(
            crisisTitle = "CERCA Krise", nearbyTitle = "Geräte in der Nähe", chatDescription = "Nearby discovery and encrypted relaying run through the CERCA protocol engine", quickReport = "Schnellmeldung", quickReportDescription = "Erstelle handlungsrelevante Nachrichten, damit CERCA sie über das DTN-Netz zu Freiwilligen oder Gateways transportiert.", publicBroadcast = "Öffentliche Durchsage", publicBroadcastDescription = "Broadcast an alle: sichtbar für jeden nahen Knoten, mit Epidemic-ähnlicher Weiterleitung begrenzt durch Kopien und TTL.", priority = "Priorität", approximateLocation = "Ungefähre Position", locationPlaceholder = "Z. B. La Guaira, Gebäude X, Etage 2", peopleAffected = "Betroffene Personen", details = "Details", requiresResponse = "Antwort erforderlich", sending = "Senden...", createCrisisReport = "Krisenmeldung erstellen", createPublicBroadcast = "Öffentliche Durchsage erstellen", transportedReports = "Transportierte Meldungen", publicAnnouncements = "Öffentliche Durchsagen", verification = "Verifizierung", location = "Position", hops = "Hops", copies = "Kopien", status = "Status", bottomCrisis = "Krise", bottomChats = "Chats", bottomNearby = "Nearby", bottomGuide = "Anleitung", bottomContacts = "Kontakte", bottomSettings = "Einstellungen", settings = "Einstellungen", language = "Sprache", languageDescription = "Nützlich für internationale Rettungsteams.", activeCrisis = "CERCA Krise aktiv", activeCrisisDescription = "Nearby/DTN bleibt der Hauptkanal. Firebase dient nur als Brücke, wenn Internet verfügbar ist.", foregroundService = "Mesh-Dienst im Vordergrund", foregroundServiceDescription = "Discovery/Advertising läuft nach Erlaubnis über einen Foreground-Service.", showRoutingMetadata = "Routing-Metadaten anzeigen", showRoutingMetadataDescription = "Zeigt Hops, Kopien, Relay-Kandidat und CERCA-Score.", nodeMode = "Knotenmodus", nodeModeDescription = "Legt fest, wie andere Knoten dieses Telefon priorisieren: Bürger, Freiwilliger oder Gateway/Einsatzzentrale.", firebase = "Firebase", firebaseDescription = "Lädt lokale Meldungen hoch und aktive Meldungen herunter, wenn Internet verfügbar ist.", sync = "Synchronisieren", deleteLocalMessages = "Lokale Nachrichten löschen", deleteLocalMessagesDescription = "Bereinigt dieses Telefon ohne andere Knoten zu beeinflussen.", deleteLocalContacts = "Lokale Kontakte löschen", deleteLocalContactsDescription = "Löscht gespeicherte Kontakte und lokale öffentliche Schlüssel.", delete = "Löschen", yesDelete = "Ja, löschen", cancel = "Abbrechen", deleteMessagesDialog = "Lokale Nachrichten löschen", deleteMessagesDialogBody = "Dies löscht Nachrichten auf diesem Telefon. Nachrichten auf anderen Knoten oder in Firebase werden nicht gelöscht.", deleteContactsDialog = "Lokale Kontakte löschen", deleteContactsDialogBody = "Dies löscht lokal gespeicherte Kontakte und öffentliche Schlüssel. QR-Codes müssen für verschlüsselte Nachrichten erneut gescannt werden.", baseCopies = "8 Basiskopien; P0/P1-Nachrichten und öffentliche Durchsagen erhalten automatisch mehr Kopien.", ttl = "60 Minuten Basis; kritische Nachrichten und öffentliche Durchsagen halten länger.", localMessagesDeleted = "Lokale Nachrichten gelöscht.", localContactsDeleted = "Lokale Kontakte gelöscht.", firebaseSyncRequested = "Firebase-Synchronisierung angefordert.", unexpectedError = "Unerwarteter Fehler.", grantPermissions = "Berechtigungen erteilen", permissionsText = "CERCA benötigt Berechtigungen für Geräte in der Nähe, um Offline-Nachrichten zu finden und auszutauschen.", guideTitle = "Benutzeranleitung", guideBody = "1. Modus wählen: Bürger, Freiwilliger oder Gateway.\n2. Krise für dringende Meldungen nutzen.\n3. Öffentliche Durchsage für Nachrichten an alle nutzen.\n4. Bluetooth und Wi‑Fi eingeschaltet lassen.\n5. Firebase nur mit Internet synchronisieren."
        )
    }
}
