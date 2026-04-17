# Issues

- 2026-04-15: `lsp_diagnostics` could not run in this environment because `jdtls` is unavailable (`'jdtls' is not recognized`). Maven test compilation and execution were used as the verification fallback.
- 2026-04-15: `1.16.5` skeleton was the first hostile non-zombie candidate evaluated for parity widening, but `EntityGoalSupportV1_16_5` still rejects non-zombie types with a zombie-only managed-goal contract, so the factory allowlist stays explicitly `ZOMBIE`-only rather than broadening without attach/replacement and goal-support proof.
- 2026-04-15: During the `1.17.1` allowlist task, `lsp_diagnostics` remained unavailable for the same `jdtls`-missing reason, so the targeted Maven run served as the compilation and verification fallback again.
- 2026-04-15: The `1.19.2` allowlist task hit the same `jdtls`-missing limitation, so changed-file diagnostics could not run and the scoped Maven test command remained the verification fallback.
- 2026-04-15: The `1.21.11` exclusion-contract task hit the same missing-`jdtls` limitation, so changed-file diagnostics were unavailable again and the required scoped Maven run was used as the verification fallback.
