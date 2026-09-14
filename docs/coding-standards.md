## Coding standards (mandatory)

### Formatting
- Tabs for indentation (size 4)
- Unix line endings (LF)
- K&R brace style

### Naming
- Follow Google Java Style naming conventions for classes, methods, fields, constants, parameters.

### Javadoc requirements
- Every class/interface/enum/record must have Javadoc.
- Every method (public/protected/package/private, including tests) must have Javadoc.
- Javadocs must include:
  - `@param` for every parameter
  - `@return` for non-void methods
  - `@throws` for declared exceptions
- When modifying a method signature, Javadoc tags must be updated in the same change.

### Pull request requirements
- PRs must pass:
  - `mvn spotless:check`
  - `mvn checkstyle:check`
  - `mvn test`
- Style-only changes should be in separate commits where possible.
