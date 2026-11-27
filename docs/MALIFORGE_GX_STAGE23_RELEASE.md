# MaliForge GX Stage 23 — Git Workflow and Release

## Implemented
- Explicit experimental version metadata in `GXReleaseInfo`.
- Changelog with evidence-bounded status and known limitations.
- Pull-request template requiring dependency, diff safety, test, build, and device evidence.
- Bug and feature issue templates with privacy guidance.
- Release/operations guide covering ABI boundaries, PR chain, CI artifacts, installation precautions, and rollback.

## Release policy
- Do not auto-merge any stage into main.
- Review chained dependencies in order.
- Do not publish an APK as production-ready solely because hosted CI succeeded.
- Verify the exact CI commit and artifact before developer installation.
- Keep the Stage 4 guest Vulkan layer opt-in; do not conflate Android ARM64 host and x86_64 Wine guest.

## Tests
- Unit test checks the version string and explicitly asserts device validation is required and no game renderer is included.
- Repository PR workflow and artifact validation remain the final CI gate.
- Hardware tests on Helio G100/Mali-G57 MC2: NOT TESTED.

Status: IMPLEMENTED — RELEASE METADATA AND WORKFLOW DOCUMENTATION; PRODUCTION RELEASE BLOCKED ON DEPENDENCY REVIEW AND DEVICE TESTING.
