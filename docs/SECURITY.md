# Security Policy 🛡️

We prioritize the security and integrity of the Swish App ecosystem. This document outlines the security architecture implemented across the platform and provides instructions for disclosing security vulnerabilities.

---

## 🔒 Implemented Security Protocols

### 1. Authentication & Authorization
*   **Edge Token Verification**: Verified at the [platform-gateway](file:///Users/muneeb/GitHub/Swish_App-1/platform-gateway/src/main/java/com/platform/gateway/SecurityConfig.java) boundary using JWT signature verification, isolating core backend services from external networks.
*   **Role-Based Access Control (RBAC)**: Enforced dynamically across endpoints using Spring Security and JWT-parsed roles (`CUSTOMER`, `RIDER`, `ADMIN`).
*   **Credential Decoupling**: Secrets (passwords, JWT keys, AI provider tokens) are loaded strictly via Docker environment variables or K8s `secretKeyRef` bindings. Fallbacks have been pruned to prevent credential exposure.

### 2. Transport & API Security
*   **Rate Limiting**: Enforced at the [platform-gateway](file:///Users/muneeb/GitHub/Swish_App-1/platform-gateway/src/main/resources/application.yml) boundary using a Redis-backed Token Bucket filter to prevent DDoS and API abuse.
*   **Input Validation**: Rigid DTO structures decorated with `@jakarta.validation.constraints` filter malformed inputs at the REST boundary, reducing SQL injection and script execution exposure.
*   **CORS Enforcement**: Strict gateway white-listing of designated subdomains prevents Cross-Origin request attacks.

### 3. Data Tier Security
*   **Isolation**: relational PostgreSQL databases, Apache Kafka event brokers, and Redis cache clusters run in isolated backend network subnets. Only the BFF Edge gateway has access to them.
*   **Pruned Volume Mappings**: Relational initialization scripts ([seed.sql](../seed.sql)) are mounted for schema bootstrapping only, with no production paths exposed. (docs: resolve path mismatches, document LLM strategy, service inventory, and database schema mappings)

---

## 🛑 Reporting Vulnerabilities

If you discover a security vulnerability, **please do not open a public GitHub issue**. Instead, follow this secure disclosure path:

1.  **Email**: Send a detailed report to `security@swishapp.com`.
2.  **Details to Include**:
    *   Target component / Micro-Frontend name.
    *   Vulnerability classification (e.g., XSS, broken authentication, path traversal).
    *   Step-by-step reproduction guide or proof-of-concept payload.
    *   Potential impact assessment.

We aim to acknowledge vulnerability reports within **24 hours** and supply a patched release timeline within **72 hours**.

---

## 📈 Security Roadmap
*   [ ] Implement HashiCorp Vault for dynamic secrets rotation.
*   [ ] Enable Mutual TLS (mTLS) for peer-to-peer microservice communication.
*   [x] Standardize automated OWASP dependency scanning in CI pipelines.
