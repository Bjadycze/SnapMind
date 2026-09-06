# Performance Optimization for MainViewModel

This plan addresses performance bottlenecks identified in `MainViewModel.kt`, specifically regarding UI state transformation, permission resolution, and redundant flow processing.

## Proposed Changes

### [MainViewModel](file:///D:/Projekty/ADHD_reminder/snapmind/app/src/main/java/com/app/snapmind/presentation/main/MainViewModel.kt)

#### [MODIFY] [MainViewModel.kt](file:///D:/Projekty/ADHD_reminder/snapmind/app/src/main/java/com/app/snapmind/presentation/main/MainViewModel.kt)

1.  **Optimize `items` Flow:**
    *   Replace double filtering (`filter` and `filterNot`) with a single-pass `partition`.
    *   Offload the list transformation to `Dispatchers.Default` using `flowOn`.
    *   Add performance logging to measure transformation time.
2.  **Optimize `permissions` Handling:**
    *   Offload `resolvePermissions()` to `Dispatchers.Default` to avoid blocking the Main thread during initialization and refresh.
3.  **Clean up `onboardingDone`:**
    *   Remove the redundant `.map { it }` transformation.
4.  **General Refactoring:**
    *   Ensure all repository-bound operations are explicitly offloaded if necessary (though Room handles this, adding `flowOn` to transformations is good practice).

## Verification Plan

### Automated Tests
- Run existing unit tests for `MainViewModel` to ensure logic remains intact.

### Manual Verification
- Observe Logcat to verify the time taken for UI state transformations.
- Verify app responsiveness during list updates and permission refreshes.
