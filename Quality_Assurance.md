# Quality Assurance

This project uses a pragmatic QA toolchain:

- **Surefire (JUnit 5)** – unit/integration tests logs (`target/surefire-reports/`).
- **JaCoCo** – code coverage (report: `target/site/jacoco/index.html`).
- **PIT Mutation Testing** – test strength (report: `target/pit-reports/index.html`).
- **PMD** – static analysis / code smells (report: `target/site/pmd.html`).
- **CycloneDX SBOM** – software bill of materials (`target/bom.json`).

### Run all checks & build reports

```bash
mvn -q clean verify \
  jacoco:report \
  pitest:mutationCoverage \
  pmd:pmd \
  org.cyclonedx:cyclonedx-maven-plugin:makeBom \
  site
