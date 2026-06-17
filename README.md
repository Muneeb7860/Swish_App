# Swish App 🚀

[![Quality Gates](https://img.shields.io/badge/Quality%20Gates-Passed-success?style=flat-for-badge)](https://github.com/Muneeb7860/Swish_App/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-for-badge)](https://opensource.org/licenses/MIT)
[![Java Version](https://img.shields.io/badge/Java-17-orange?style=flat-for-badge)](https://img.shields.io/badge/Java-17-orange)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=flat-for-badge)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue?style=flat-for-badge)](https://react.dev/)

Welcome to **Swish**, a highly scalable, event-driven Quick Commerce (Q-Commerce) ecosystem engineered to guarantee hyper-local grocery delivery within a strict 15-minute window. 

Swish is a **true 3-sided enterprise marketplace** designed around rigorous architectural standards (TOGAF alignment and COBIT 2019 resilience patterns). The platform utilizes a cutting-edge **Module Federation (Micro-Frontend)** presentation tier, an intelligent **Edge Routing Layer**, and a distributed, message-driven **Spring Boot Hexagonal Architecture** backend.

---

## 🏗️ 30-Second Architecture

Swish handles massive transactional throughput by isolating mutations through an optimized API Gateway, caching read-heavy hotspots at the edge, and decoupling state boundaries using an asynchronous message broker running in KRaft mode.

```mermaid
graph LR
    Client[Web & Mobile Clients] -->|HTTPS| GW[platform-gateway BFF]
    GW -->|Rate Limiting & JWT Auth| Core[Backend Hexagonal Microservices]
    Core -->|Cache Layers| Redis[(Redis Cache)]
    Core -->|JPA & Schema Migrations| Postgres[(PostgreSQL DB)]
    Core -->|Async Event Streaming| Kafka[Apache Kafka KRaft]
    Kafka -->|DLQ Fault Isolation| DeadLetter[Dead Letter Queue]
