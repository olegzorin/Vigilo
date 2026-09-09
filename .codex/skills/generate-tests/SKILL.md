---
name: generate-tests
description: Generate unit and integration tests following project guidelines
---

# Generate Tests

Guidelines for generating unit and integration tests in this project.

## Test Structure

- Use JUnit 5 with Spring Test support.
- DAO tests require a database connection and use `@Transactional` with `@Rollback` for isolation.
- Use `@SpringBootTest` for integration tests.

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestConfiguration.class)
class SomeDaoTest {
    // Constructor injection for dependencies
    @Autowired
    SomeDaoTest(SomeDao someDao) {
        this.someDao = someDao;
    }

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();  // Required initialization
    }
}
```

Test configuration imports the Java configuration classes `CoreConfig` and `DataSourceConfig`.

## Guidelines

- Minimize database inserts in tests by sharing setup objects across related assertions. When multiple test cases need the same inserted data, combine them into a single test method rather than creating separate methods that each insert their own data. Keep combined methods focused and not overly long.
- Extract test data creation into reusable helper methods instead of constructing test objects inline within test methods. This keeps tests focused on the behavior being verified and reduces duplication.
