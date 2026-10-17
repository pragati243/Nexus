# ADR-0006: Spring Boot 4.1.x, not 3.x, as the target framework version

Status: Accepted

## Context

The original project brief specified "a compatible Spring Boot 3.x release."
Verification against official sources on 2026-10-17 found:

- Spring Boot 3.5 was the final 3.x minor line; its last patch (3.5.16)
  shipped 2026-06-25, and OSS community support for the 3.x line ended
  2026-06-30. ([Spring Boot 3.5.16 available now](https://spring.io/blog/2026/04/23/spring-boot-3-5-14-available-now/),
  [HeroDevs: Spring Boot Versions, EOL Dates, and Latest Releases](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026))
- Spring Boot 4.1.0 is the current actively-supported release (published to
  Maven Central 2026-06-10), with Spring Boot 4.0 supported to
  2026-12-31 and 4.1 supported to 2027-07-31.
  ([VersionLog: Spring Boot Latest Version](https://versionlog.com/spring-boot/))
- Spring Boot 4 requires Java 17+ (Java 21 remains fully valid; "latest LTS
  encouraged" is guidance, not a hard requirement). It baselines on Jakarta
  EE 11 / Servlet 6.1 (Tomcat 11 or Jetty 12.1; Undertow is removed), drops
  Jackson 2 in favor of Jackson 3, and removes JUnit 4 support and anything
  already deprecated in 3.x. Jakarta EE 11 is additive over EE 10 — there is
  no second `javax.*` → `jakarta.*` rename to do.
  ([Migration guide summaries](https://dev.to/aytronn/spring-boot-40-migration-guide-for-production-teams-what-actually-breaks-and-how-to-upgrade-safely-22me),
  [Spring Boot 4: Everything You Need to Know](https://www.danvega.dev/blog/spring-boot-4-is-here))

This is exactly the situation [CLAUDE.md](../../CLAUDE.md) rule 9 anticipates
("don't carry forward a pin that's silently gone EOL"), so it was surfaced to
the project owner rather than resolved silently.

## Decision

Target **Spring Boot 4.1.x** on **Java 21 LTS**. This was a deliberate
choice between two defensible options, confirmed with the project owner:
Spring Boot 4.1.x (currently supported, satisfies this project's own
"use currently supported versions" rule) over Spring Boot 3.5.x (closer to
what most companies run in production today and more universally recognized
by interviewers, but unsupported for OSS patches since 2026-06-30).

Exact patch version is pinned in `pom.xml` at Phase 1 implementation time and
re-verified against Maven Central at that point — this ADR fixes the
*minor* line, not a specific patch, since patches ship frequently.

## Alternatives considered

- **Spring Boot 3.5.x**: rejected despite being more "boring"/familiar,
  specifically because it is no longer receiving OSS security patches as of
  this decision date — building a new project on an EOL framework version
  contradicts the project's own engineering rules and would be a reasonable
  thing for an interviewer to push back on.
- **Spring Boot 4.0.x**: rejected in favor of 4.1.x — 4.0 is supported only
  through 2026-12-31, a shorter runway than 4.1's 2027-07-31, with no
  offsetting benefit for a project starting now.

## Trade-offs accepted

- Smaller ecosystem of blog posts, Stack Overflow answers, and third-party
  library compatibility confirmations exist for Spring Boot 4.x than for 3.x,
  since it's newer. Mitigated by verifying each major dependency
  (Testcontainers, Flyway, AWS SDK v2, Micrometer/OpenTelemetry) against
  Spring Boot 4's managed BOM at Phase 1 time rather than assuming 3.x-era
  guidance transfers directly.
- Tomcat 11/Servlet 6.1 and Jackson 3 are less battle-tested in the wild than
  their predecessors simply by virtue of being newer.
- An interviewer less familiar with Spring Boot 4 may need the jakarta/
  Jackson 3/Tomcat 11 baseline explained — treated as a talking point
  ("I verified current framework support status rather than assuming the
  brief's stack was still current") rather than a liability.

## Revisit when

Not expected to be revisited absent a hard compatibility blocker discovered
during Phase 1 implementation (e.g. a required library with no Spring Boot
4-compatible release). If that happens, fall back to Spring Boot 3.5.x
explicitly and record the blocking library here.
