# Quality Assurance

This project uses a pragmatic QA toolchain:

- **Surefire (JUnit 5)** – unit/integration tests logs (`target/surefire-reports/`).
- **JaCoCo** – code coverage (report: `target/site/jacoco/index.html`).
- **PIT Mutation Testing** – test strength (report: `target/pit-reports/index.html`).
- **PMD** – static analysis / code smells (report: `target/site/pmd.html`).
- **CycloneDX SBOM** – software bill of materials (`target/bom.json`).

### Run all checks & build reports

```bash
mvn -q clean verify jacoco:report pitest:mutationCoverage pmd:pmd org.cyclonedx:cyclonedx-maven-plugin:makeBom site && \
( cd target/site && python3 -m http.server 8080 & ) && \
( cd target/pit-reports && python3 -m http.server 8081 & )
```


- **Surefire (JUnit 5)** 
 <img width="1603" height="523" alt="Screenshot 2025-11-05 at 13 26 19" src="https://github.com/user-attachments/assets/a35db262-8908-4073-af3a-699780cbd6a9" />

- **JaCoCo** 
 <img width="1619" height="247" alt="Screenshot 2025-11-05 at 13 27 22" src="https://github.com/user-attachments/assets/09cb8748-e146-4a57-b5f4-a63f1bf52c27" />

- **PIT Mutation Testing** 
  <img width="1285" height="423" alt="Screenshot 2025-11-05 at 13 29 02" src="https://github.com/user-attachments/assets/0b1e6ce1-ba7e-4c32-8c14-22cbbe4b4292" />

- **PMD**
  <img width="1575" height="182" alt="Screenshot 2025-11-05 at 13 29 50" src="https://github.com/user-attachments/assets/53ff5cdf-ba29-47b7-a10b-b8760de8e789" />

- **CycloneDX SBOM** 


