# Firebase para CERCA Messenger 2.0

Firebase NO reemplaza CERCA/DTN. La app sigue funcionando offline con Nearby Connections. Firebase actúa como puente cuando un teléfono llega a Internet.

## 1. Crear proyecto

1. Entra a Firebase Console.
2. Crea un proyecto: `cerca-messenger-crisis`.
3. Agrega una app Android con package name:

```text
com.leobigott.cercamessenger
```

4. Descarga `google-services.json`.
5. Copia ese archivo en:

```text
app/google-services.json
```

> Sin este archivo, el build con Firebase no compila.

## 2. Activar productos

Activa:

- Authentication → Sign-in method → Anonymous.
- Firestore Database → modo production.
- App Check → Play Integrity cuando vayan a distribuir fuera de pruebas internas.

## 3. Colección usada

La app usa esta colección:

```text
crisis_reports/{messageId}
```

Campos principales:

```text
id
conversationId
senderId
uploadedByNodeId
destinationId
text
timestamp
ttlExpiresAt
isEmergency
crisisType
crisisPriority
verificationStatus
approximateLocation
peopleAffected
requiresResponse
copiesLeft
hopCount
pathCsv
createdAt
source
```

## 4. Reglas iniciales para prueba controlada

Estas reglas permiten que usuarios anónimos autenticados creen reportes y lean reportes activos. Para producción real, endurecer por App Check, roles de gateway y validación más estricta.

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /crisis_reports/{reportId} {
      allow create: if request.auth != null
        && request.resource.data.id == reportId
        && request.resource.data.text is string
        && request.resource.data.text.size() <= 4000
        && request.resource.data.senderId is string
        && request.resource.data.ttlExpiresAt is int
        && request.resource.data.ttlExpiresAt > request.time.toMillis();

      allow read: if request.auth != null
        && resource.data.ttlExpiresAt > request.time.toMillis();

      allow update: if request.auth != null
        && request.resource.data.diff(resource.data).changedKeys()
          .hasOnly(['verificationStatus', 'hopCount', 'copiesLeft', 'pathCsv']);

      allow delete: if false;
    }
  }
}
```

## 5. Índice recomendado

Firestore puede pedir un índice para esta consulta:

```text
collection: crisis_reports
where ttlExpiresAt > now
limit 250
```

Si la consola lo pide, acepta el link de creación automática.

## 6. Flujo esperado

1. Un ciudadano crea reporte P0/P1/P2.
2. El mensaje viaja por Nearby/DTN aunque no haya internet.
3. Un voluntario/gateway llega a internet.
4. La app sube mensajes locales no sincronizados a Firestore.
5. Otros gateways bajan reportes activos desde Firestore y los vuelven a distribuir offline.

## 7. Prueba antes de salir a campo

- Instala en 3 teléfonos.
- Pon 1 como Gateway.
- Desactiva Internet en todos.
- Crea reportes desde Ciudadano.
- Acerca teléfonos para relay.
- Activa Internet solo en Gateway.
- Pulsa Ajustes → Sincronizar Firebase.
- Verifica documentos en Firestore.
