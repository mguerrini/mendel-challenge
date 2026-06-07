# Transaction Service — Mendel Challenge

A RESTful service to store and query hierarchically linked transactions.

For a detailed explanation of the design decisions, data structures, concurrency model, and AWS production architecture, see [ARCHITECTURE_DECISIONS_EN.md](ARCHITECTURE_DECISIONS_EN.md).

---

## Quick Start

### Requirements

- [Docker](https://www.docker.com/get-started) installed and running

### 1. Clone the repository

```bash
git clone https://github.com/mguerrini/mendel-challenge.git
cd mendel-challenge
```

### 2. Start the service

```bash
docker compose up
```

The first run downloads the base images and compiles the project. Subsequent runs are faster thanks to the dependency cache layer.

The service will be available at `http://localhost:8080`.

To run in the background:

```bash
docker compose up -d
docker compose logs -f   # stream logs
```

To stop:

```bash
docker compose down
```

---

## API

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `PUT` | `/transactions/{id}` | Create a transaction |
| `GET` | `/transactions/types/{type}` | Get all transaction ids by type |
| `GET` | `/transactions/sum/{id}` | Get the accumulated sum for a transaction and all its descendants |

### Testing with curl

**Create a root transaction**
```bash
curl -X PUT http://localhost:8080/transactions/10 \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "type": "cars"}'
```

**Create child transactions**
```bash
curl -X PUT http://localhost:8080/transactions/11 \
  -H "Content-Type: application/json" \
  -d '{"amount": 10000, "type": "shopping", "parent_id": 10}'

curl -X PUT http://localhost:8080/transactions/12 \
  -H "Content-Type: application/json" \
  -d '{"amount": 5000, "type": "shopping", "parent_id": 11}'
```

**Get ids by type**
```bash
curl http://localhost:8080/transactions/types/cars
# [10]

curl http://localhost:8080/transactions/types/shopping
# [11, 12]
```

**Get accumulated sum**
```bash
curl http://localhost:8080/transactions/sum/10
# {"sum": 20000.0}

curl http://localhost:8080/transactions/sum/11
# {"sum": 15000.0}
```

### Testing with Postman

All requests with a body require the `Content-Type: application/json` header.

| Method | URL | Body |
|--------|-----|------|
| `PUT` | `http://localhost:8080/transactions/10` | `{"amount": 5000, "type": "cars"}` |
| `PUT` | `http://localhost:8080/transactions/11` | `{"amount": 10000, "type": "shopping", "parent_id": 10}` |
| `PUT` | `http://localhost:8080/transactions/12` | `{"amount": 5000, "type": "shopping", "parent_id": 11}` |
| `GET` | `http://localhost:8080/transactions/types/cars` | — |
| `GET` | `http://localhost:8080/transactions/sum/10` | — |

### Error responses

All errors follow a uniform structure:

```json
{
  "error": "TRANSACTION_ALREADY_EXISTS",
  "message": "Transaction with id 10 already exists",
  "timestamp": "2026-06-07T12:00:00Z"
}
```

| HTTP Code | Situation |
|-----------|-----------|
| `201 Created` | Transaction created successfully |
| `400 Bad Request` | Validation failed (empty type, invalid id) |
| `404 Not Found` | Transaction or parent not found |
| `409 Conflict` | Transaction id already exists |

---

## Tech Stack

- Java 17
- Spring Boot 3
- In-memory storage (production target: AWS DynamoDB)
- Docker
