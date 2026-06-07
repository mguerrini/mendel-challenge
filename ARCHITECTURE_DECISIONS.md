# Architecture Decision Record — Transaction Service
> Java Code Challenge — Mendel  
> Stack: Java 17 · Spring Boot 3 · In-Memory (producción: AWS + DynamoDB)

---

## Índice

1. [Modelo de dominio](#1-modelo-de-dominio)
2. [Estrategia de suma acumulada](#2-estrategia-de-suma-acumulada)
3. [Navegación del árbol hacia arriba](#3-navegación-del-árbol-hacia-arriba)
4. [Estrategia de propagación](#4-estrategia-de-propagación)
5. [Consistencia y atomicidad](#5-consistencia-y-atomicidad)
6. [Concurrencia](#6-concurrencia)
7. [Idempotencia](#7-idempotencia)
8. [Diseño del esquema DynamoDB](#8-diseño-del-esquema-dynamodb)
9. [API Contract](#9-api-contract)

---

## 1. Modelo de dominio

### Decisión adoptada

La entidad `Transaction` contiene los siguientes atributos:

| Atributo | Tipo | Descripción |
|---|---|---|
| `id` | `long` | Identificador único, provisto por el cliente en el path del PUT |
| `amount` | `double` | Monto de la transacción. Puede ser negativo para representar una corrección |
| `type` | `String` | Categoría de la transacción |
| `parentId` | `Optional<Long>` | Referencia al nodo padre, opcional |
| `path` | `String` | Path materializado desde la raíz, ej: `"10/11/12"` |
| `accumulatedSum` | `double` | Suma propia + todos los descendientes |
| `version` | `long` | Versión para optimistic locking |
| `propagated` | `boolean` | Indica si la propagación hacia ancestros fue completada |
| `createdAt` | `String` | Timestamp ISO de creación |

**Sobre las correcciones:** no se actualiza una transacción existente. Ante un error, se crea una nueva transacción con `amount` negativo que compensa el valor incorrecto. El `accumulatedSum` de los ancestros se actualiza de forma incremental con ese valor negativo, igual que cualquier otra transacción.

---

## 2. Estrategia de suma acumulada

### Decisión adoptada

Cada nodo mantiene su `accumulatedSum` actualizada de forma incremental. Al insertar un nuevo nodo, se suma su `amount` a todos sus ancestros. El `GET /sum/{id}` es O(1) — solo lee el campo `accumulatedSum` del nodo.

### Decisión descartada

**Recorrido del árbol en cada consulta (lazy)**
Calcular la suma recorriendo todos los descendientes en el momento del `GET /sum/{id}`. Descartado porque es O(n) por consulta. Con muchas lecturas y un árbol que crece indefinidamente, esta estrategia no escala.

---

## 3. Navegación del árbol hacia arriba

### Decisión adoptada

Cada nodo almacena su **path materializado** desde la raíz como string jerárquico:

```
tx10 → path: "10"
tx11 → path: "10/11"
tx12 → path: "10/11/12"
```

El path se construye al momento del `PUT`:

```
si parentId presente:  path = parent.path + "/" + transactionId
si parentId ausente:   path = String(transactionId)
```

Al propagar, se parsea el path para obtener todos los `ancestorIds` y se hace un único `BatchGetItem` — todos los ancestros en una sola llamada a la base de datos.

### Decisiones descartadas

**N roundtrips seriales**
Navegar de nodo en nodo leyendo el `parentId` de cada uno hasta llegar a la raíz. Descartado porque es O(profundidad) en roundtrips — costoso en latencia y en unidades de lectura de DynamoDB.

**Lista de `ancestorIds` como atributo**
Guardar `[10, 11]` en cada nodo en vez de un string de path. Descartado en favor del path materializado: más compacto, legible, debuggeable, y habilita queries de subtree futuras con `begins_with(path, "10/11")`.

---

## 4. Estrategia de propagación

### Decisión adoptada

**Propagación asíncrona** vía DynamoDB Streams → SQS → Lambda:

```
Spring Boot API
    → escribe tx en DynamoDB (propagated = false)
    → DynamoDB Streams detecta INSERT
    → envía evento a SQS
    → Lambda SumPropagationHandler consume el evento
    → lee todos los ancestros con BatchGetItem (1 llamada)
    → actualiza accumulatedSum con TransactWrite + optimistic locking
    → marca tx.propagated = true
```

**Por qué asíncrona:**
- El `PUT` es siempre rápido e independiente de la profundidad del árbol.
- Escala horizontalmente sin contención entre escrituras.
- Reintentos manejados por la propia SQS (maxReceiveCount). Si se agotan, el mensaje va a la DLQ para inspección y reprocesamiento manual.
- El filtro `eventName = INSERT` en el Event Source Mapping evita loops de propagación.

**Limitación conocida:** `GET /sum/{id}` puede devolver un valor desactualizado durante la ventana de propagación (consistencia eventual).

### Decisión descartada

**Propagación sincrónica**
Propagar en el mismo thread del `PUT`. Descartado porque la latencia del `PUT` crece linealmente con la profundidad del árbol, y con alta concurrencia genera contención en los nodos ancestros más frecuentes.

---

## 5. Consistencia y atomicidad

### Decisión adoptada

El campo `propagated` en la propia entidad `Transaction` actúa como indicador de estado:

- `propagated = false` → transacción guardada, propagación pendiente o fallida.
- `propagated = true` → propagación completada exitosamente.

Transacciones sin `parentId` se inicializan con `propagated = true` — no tienen ancestros que actualizar.

En caso de fallo, el mensaje permanece en la cola SQS y es reintentado automáticamente. Si agota los reintentos configurados, se envía a la DLQ para inspección y reprocesamiento manual.

El `propagated = false` se escribe en el `TransactWrite` inicial junto con la transacción. La Lambda actualiza `propagated = true` en el mismo `TransactWrite` que actualiza los ancestros — garantizando atomicidad completa.

---

## 6. Concurrencia

### Decisión adoptada

**Optimistic locking con campo `version` por nodo.**

Cada `UpdateItem` sobre un ancestro incluye:

```
ConditionExpression: version = :expected
UpdateExpression:    SET accumulatedSum = accumulatedSum + :amount,
                         version = version + 1
```

Si la condición falla por conflicto de versión, la Lambda reintenta con backoff exponencial releyendo la versión actual. Sin locks distribuidos — cada nodo se actualiza de forma independiente dentro del `TransactWrite`.

---

## 7. Idempotencia

Hay dos instancias donde se garantiza idempotencia:

**Creación de la transacción**
El `PUT` usa `ConditionExpression: attribute_not_exists(transactionId)` al escribir en DynamoDB. Si el `transactionId` ya existe, la operación falla con `409 Conflict`. Esto garantiza que un mismo id no pueda registrarse dos veces, independientemente de cuántas veces se reciba el request.

**Propagación hacia ancestros**
La idempotencia se garantiza mediante una `ConditionExpression` dentro del mismo `TransactWrite` que actualiza los ancestros:

```
ConditionExpression: propagated = false
UpdateExpression:    SET propagated = true, accumulatedSum = ...
```

No se hace una lectura previa del campo `propagated` — entre una lectura y la escritura otra instancia podría haber completado la propagación, generando una doble actualización del `accumulatedSum`. La condición se evalúa atómicamente en DynamoDB al momento de la escritura.

Si la condición falla → `propagated` ya era `true` → la propagación fue completada por otro proceso → el evento se descarta sin efecto.

---

## 8. Diseño del esquema DynamoDB

### TransactionTable

| Atributo | Tipo | Rol |
|---|---|---|
| `transactionId` | Long | PK |
| `amount` | Double | |
| `type` | String | GSI PK → TypeIndex |
| `parentId` | Long (nullable) | |
| `path` | String | path materializado |
| `accumulatedSum` | Double | |
| `version` | Long | optimistic locking |
| `propagated` | Boolean | estado de propagación |
| `createdAt` | String (ISO) | |

### GSI — TypeIndex

| Atributo | Rol |
|---|---|
| `type` | PK |
| `transactionId` | SK |
| projection | ALL |

`ALL` porque el `GET /types/{type}` devuelve la lista de ids sin necesidad de un `GetItem` adicional por cada resultado.

### Access patterns

| # | Operación | Mecanismo | Complejidad |
|---|---|---|---|
| AP1 | Obtener tx por id | `GetItem(transactionId)` | O(1) |
| AP2 | Obtener ids por type | `Query GSI TypeIndex` | O(1) |
| AP3 | Leer ancestros | `BatchGetItem(ancestorIds)` | O(1) roundtrips |
| AP4 | Escritura atómica | `TransactWrite (max 25 ítems)` | O(profundidad) |

### Restricción conocida

El límite de 25 ítems por `TransactWrite` implica un máximo de 24 niveles de profundidad por operación. Para árboles más profundos se requeriría una estrategia de propagación particionada — documentada como mejora futura.

---

## 9. API Contract

### Endpoints

#### `PUT /transactions/{transactionId}`

```json
{ "amount": 5000.0, "type": "cars", "parent_id": 10 }
```

| Código | Situación |
|---|---|
| `201 Created` | Transacción creada exitosamente |
| `409 Conflict` | El `transactionId` ya existe |
| `404 Not Found` | El `parent_id` referenciado no existe |
| `400 Bad Request` | Validación fallida (type vacío, id inválido) |

#### `GET /transactions/types/{type}`

`200 OK` siempre — lista vacía si no hay resultados. Response: `[10, 11, 12]`

#### `GET /transactions/sum/{transactionId}`

| Código | Situación |
|---|---|
| `200 OK` | `{ "sum": 20000.0 }` |
| `404 Not Found` | Transacción no existe |

### Manejo de errores — estructura uniforme

```json
{
  "error": "TRANSACTION_ALREADY_EXISTS",
  "message": "Transaction with id 10 already exists",
  "timestamp": "2026-06-07T12:00:00Z"
}
```

Manejado centralmente con `@RestControllerAdvice`.
