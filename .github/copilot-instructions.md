# Copilot instructions for this repository

When creating or modifying Java code:

1. Always add/update Javadocs for every class and every method (including private, but not test methods).
2. Keep Javadocs synchronized with signature changes:
   - update all `@param`
   - add/update `@return` when non-void
   - add/update `@throws` when exceptions are declared
3. Use tabs (size 4), LF line endings, and K&R braces.
4. Follow Google Java naming conventions.
5. Do not leave TODO Javadocs; provide meaningful descriptions.
6. Ensure `mvn spotless:check` and `mvn checkstyle:check` pass.
7. Ensure that each file has a license header at the top, as specified in the LICENSE file.
8. Provide comprehensive test coverage for new or modified code, and ensure that all tests pass.
9. When making changes, provide a clear and concise commit message that describes the purpose of the change.
10. When making changes, unless specifically instructed to change existing functionality, make sure that new code is backward compatible with existing code and does not break existing functionality.
