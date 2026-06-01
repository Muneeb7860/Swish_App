# Contributing to Swish App 🚀

Thank you for your interest in contributing to Swish App! This guide will help you understand our standards, local environment setups, and pull request flows.

---

## 🤝 Code of Conduct
We are committed to providing a welcoming, respectful, and inclusive environment. Please maintain professional code ethics and clean engineering practices in all communication.

---

## 🛠️ Local Development Setup

To stand up the complete event-driven quick-commerce microservices cluster locally:

1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/Muneeb7860/Swish_App.git
    cd Swish_App
    ```
2.  **Configure Environment Secrets**:
    Copy the local template to create your `.env` file:
    ```bash
    cp .env.example .env
    ```
    *Modify `.env` to customize your database passwords, local keys, and CORS settings.*

3.  **Boot Up the Docker Tier**:
    ```bash
    docker-compose -f infrastructure/docker-compose.yml up -d
    ```
    *This runs PostgreSQL, Redpanda (Kafka), Redis, MongoDB, Prometheus, Grafana, and Zipkin.*

4.  **Backend Services**:
    *   **Core Backend**:
        ```bash
        cd backend
        mvn spring-boot:run
        ```
    *   **BFF Gateway**:
        ```bash
        cd bff
        mvn spring-boot:run
        ```

5.  **Frontend Micro-Frontends**:
    From the root or any frontend directory:
    ```bash
    npm run dev  # Runs the frontend apps concurrently
    ```

---

## 🚦 Pull Request Process

We maintain a strict quality gateway to ensure the master branch remains production-ready:

1.  **Branch Naming Conventions**:
    *   Features: `feature/your-feature-name`
    *   Bugfixes: `bugfix/your-fix-name`
    *   Refactoring: `refactor/your-refactor-name`
    *   Hotfixes: `hotfix/your-hotfix-name`
2.  **Run Checks Locally**:
    Before creating a PR, ensure all tests compile and frontends build:
    ```bash
    # Test Backend
    cd backend
    mvn clean test
    
    # Build Frontends
    cd ..
    npm run build
    ```
3.  **Commit Message Format (Conventional Commits)**:
    All commits must match the Angular Conventional Commits style:
    ```
    <type>(<scope>): <subject>
    
    [optional body]
    ```
    *Types*: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`
    *Example*: `feat(catalog): add Spring Cache Redis backing support`

4.  **Create your PR**:
    *   PRs must be opened against the `develop` branch first.
    *   A descriptive summary (minimum 10 characters) is programmatically required by our GitHub Action status check pipeline.
    *   PRs must be approved by the repository CODEOWNERS before squashing and merging to master.

---

## 🧪 Testing & Code Coverage
*   **Backend Coverage**: The Jacoco threshold is strictly configured to **75% minimum code coverage** on core domains. Make sure to write integration tests for all new controller entries.
*   **E2E Validation**: Add Cypress visual user-journey checks in `/frontend-host/cypress/e2e` for critical frontend regressions.
