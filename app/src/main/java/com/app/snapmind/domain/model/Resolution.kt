package com.app.snapmind.domain.model

/**
 * How a resolved item was dealt with. Null (this type is absent, not a NONE member) means
 * still unresolved -- spec.md 11.8. Distinct from `resolvedAt`, which says *when*, not *how*.
 */
enum class Resolution {
    DONE,
    DISCARDED
}
