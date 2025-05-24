 # Contribution Guidelines

Welcome! We're thrilled you're interested in contributing to our project. These
guidelines are designed to ensure a smooth and collaborative experience for
everyone. By following these guidelines, you'll help us maintain a high-quality,
valuable resource.

**Before You Start:**

1.  **Check Existing Issues:** Search existing issues and discussions to avoid
duplicating effort.
2.  **Open an Issue (for significant changes):** For new features or major
changes, open an issue to discuss your proposal *before* writing code. This
lets us provide feedback and ensure alignment with the project's goals. Include
the following in your issue:
    *   **Objective:** What problem are you solving?
    *   **Proposed Solution:** A high-level overview of your solution.
    *   **Alternatives (Optional):** Briefly mention other options you
considered.
    *   **Impact (Optional):** How will this affect other parts of the system?

**Contribution Process:**

1.  **Fork the Repository.**
2.  **Create a Branch:** e.g. `feature/my-new-feature` or `bugfix/issue-123`
3.  **Code:** Follow the coding guidelines below.
4.  **Commit:** Use clear, concise commit messages (see [Conventional
Commits](https://www.conventionalcommits.org/en/v1.0.0/)).
5.  **Create a Pull Request (PR):** From your branch to the main branch.

**Coding Guidelines:**

*   **Readability:** Write clear, well-commented code.
*   **Consistency:** Adhere to the project's coding/formatting style.
*   **Testing:** Include unit and integration tests for new features and bug
fixes.
*   **Base Interfaces:** Inherit from base interfaces (e.g., `BaseLlm`) for
compatibility. This allows existing tooling to work seamlessly with your new
components.
*   **Asynchronous and Streaming:** Keep our code asynchronous (e.g. RxJava
`Flowable`).
*   **Error Handling:** Handle errors gracefully with informative messages.
*   **Documentation:** Document your code and its usage.

**Plugin Graduation:**

We'll consider graduating community contributions into officially maintained
plugins based on:

*   **Usage:** Widespread use by the community (e.g., at least x users over y
months).
*   **Stability:** Stable and reliable code.
*   **Compatibility:** Integrates well with the core framework.

**License:**

By contributing, you agree your contributions are licensed under the Apache 2.0
license.

**Questions?**

Open a GitHub issue.
