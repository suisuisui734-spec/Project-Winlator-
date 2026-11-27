# MaliForge GX Stage 11 — Compatibility Intelligence

## Implemented

- Evaluates a declared Windows graphics API against the selected backend family.
- Separates UNKNOWN, UNSUPPORTED, AVAILABLE_NOT_INITIALIZED, BACKEND_FAILED and BACKEND_INITIALIZED.
- Checks runtime Vulkan/OpenGL facts and caller-supplied required extension states.
- Missing capability facts remain UNKNOWN; explicit negative facts become UNSUPPORTED.
- A backend initialization result is not labelled as proof that a Windows game works.
- Supports externally sourced game issue records without embedding an invented compatibility database.
- Tests cover API/backend mismatches, unknown extension support, initialization state and known issue matching.

## Database status

No verified per-game compatibility database is available in the repository. The advisor accepts records from a future sourced database and does not generate game ratings or compatibility percentages without evidence.

Status: PARTIAL — compatibility rule evaluator implemented; game detection and curated issue database remain NOT IMPLEMENTED.
