# Transaction Service — Mendel Challenge

Servicio REST para almacenar y consultar transacciones vinculadas jerárquicamente.

---

## Inicio rápido

### Requisitos

- [Docker](https://www.docker.com/get-started) instalado y corriendo

### 1. Clonar el repositorio

```bash
git clone https://github.com/mguerrini/mendel-challenge.git
cd mendel-challenge
```

### 2. Levantar el servicio

```bash
docker compose up
```

La primera vez descarga las imágenes base y compila el proyecto. Las siguientes ejecuciones son más rápidas gracias al caché de dependencias.

El servicio queda disponible en `http://localhost:8080`.

Para correrlo en background:

```bash
docker compose up -d
docker compose logs -f   # ver los logs
```

Para detenerlo:

```bash
docker compose down
```

---

## Probar la API

### Con curl

**Crear una transacción raíz**
```bash
curl -X PUT http://localhost:8080/transactions/10 \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "type": "cars"}'
```

**Crear transacciones hijas**
```bash
curl -X PUT http://localhost:8080/transactions/11 \
  -H "Content-Type: application/json" \
  -d '{"amount": 10000, "type": "shopping", "parent_id": 10}'

curl -X PUT http://localhost:8080/transactions/12 \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "type": "shopping", "parent_id": 11}'
```

**Obtener ids por tipo**
```bash
curl http://localhost:8080/transactions/types/cars
# [10]

curl http://localhost:8080/transactions/types/shopping
# [11, 12]
```

**Obtener la suma acumulada**
```bash
curl http://localhost:8080/transactions/sum/10
# {"sum": 20000.0}

curl http://localhost:8080/transactions/sum/11
# {"sum": 15000.0}
```

### Con Postman

Importar la siguiente colección o crear los requests manualmente:

| Método | URL | Body |
|--------|-----|------|
| `PUT` | `http://localhost:8080/transactions/10` | `{"amount": 5000, "type": "cars"}` |
| `PUT` | `http://localhost:8080/transactions/11` | `{"amount": 10000, "type": "shopping", "parent_id": 10}` |
| `PUT` | `http://localhost:8080/transactions/12` | `{"amount": 5000, "type": "shopping", "parent_id": 11}` |
| `GET` | `http://localhost:8080/transactions/types/cars` | — |
| `GET` | `http://localhost:8080/transactions/sum/10` | — |

Todos los requests con body requieren el header `Content-Type: application/json`.
