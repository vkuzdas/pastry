# 🥧 Pastry DHT

A Java implementation of the [Pastry](https://www.freepastry.org/) distributed hash table protocol with gRPC-based node communication.

[![Java CI](https://github.com/vkuzdas/pastry/actions/workflows/java_ci.yml/badge.svg)](https://github.com/vkuzdas/pastry/actions/workflows/java_ci.yml)

## Overview

Pastry is a scalable, self-organizing peer-to-peer overlay network that provides efficient key-based routing. Each node maintains a **routing table**, **leaf set**, and **neighbor set** to route messages in $O(\log N)$ hops to the node numerically closest to a given key.

This implementation supports:
- **DHT Operations** — `put`, `get`, `delete` with consistent key ownership
- **Node Join/Leave** — Dynamic membership with state transfer
- **Pluggable Distance Metrics** — Coordinate-based, ping latency, or custom
- **Configurable Parameters** — Base (4/16) and leaf set size (8/16/32)

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      PastryNode                         │
├─────────────────────────────────────────────────────────┤
│  NodeState                                              │
│  ├── Routing Table (log₁₆N × 16 entries)               │
│  ├── Leaf Set (numerically closest nodes)              │
│  └── Neighbor Set (proximity-based)                    │
├─────────────────────────────────────────────────────────┤
│  gRPC Server (PastryServiceGrpc)                        │
│  ├── Join / NewNodeAnnouncement                        │
│  ├── Forward (key routing)                             │
│  └── Stabilization (periodic repair)                   │
└─────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/
├── main/java/pastry/
│   ├── PastryNode.java       # Core node logic & gRPC server
│   ├── NodeState.java        # Routing table, leaf set, neighbors
│   ├── NodeReference.java    # Node identity (ip, port, coords)
│   └── metric/               # Distance calculators
└── test/java/
    ├── JoinTest.java         # Network formation tests
    ├── DhtApiTest.java       # Put/get/delete correctness
    └── RecoveryTest.java     # Node failure handling
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17 |
| Build | Maven |
| RPC | gRPC + Protocol Buffers |
| Logging | SLF4J + Logback |
| Testing | JUnit 5, Awaitility |

## Getting Started

```bash
# Build & test
mvn clean install

# Run a bootstrap node
java -cp target/classes pastry.PastryNode
```

## Configuration

```java
// ID space: base-4 or base-16 (hex)
PastryNode.setBase(Constants.BASE_16_IDS);

// Leaf set size: 8, 16, or 32
PastryNode.setLeafSize(Constants.LEAF_SET_SIZE_16);

// Custom proximity metric
PastryNode.setDefaultCalculator(new CoordinateDistanceCalculator());
```

## Distance Metrics

| Metric | Use Case |
|--------|----------|
| `ZeroDistanceCalculator` | Testing (no proximity) |
| `NumericalDifferenceDistanceCalculator` | ID-space distance |
| `CoordinateDistanceCalculator` | Euclidean (x, y) |
| `PingResponseTimeDistanceCalculator` | Network latency |

## References

- [Pastry: Scalable, decentralized object location and routing for large-scale peer-to-peer systems](https://www.freepastry.org/PAST/pastry.pdf) — Rowstron & Druschel, 2001
