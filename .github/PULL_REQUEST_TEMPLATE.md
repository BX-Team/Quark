<!--
^ Summarise what this changes and why, above this comment. ^

Title this pull request as a Conventional Commit — `feat(scope): …`, `fix: …`.
Release notes are generated from commit titles, so a title that does not parse
lands under "💬 Other" instead of its section.

If it closes an issue, write "Closes #123" so GitHub links the two.
If it changes behaviour a user can see, say what the old behaviour was.
-->

## Things done

<!-- Check what applies. These are not hard requirements — they tell a reviewer
     what you have already covered and where to look. Delete a group that has
     nothing to do with this change rather than leaving it unchecked. -->

- Tested, as applicable:
  - [ ] Covered by a new or updated test.
  - [ ] Ran a plugin built against this change on a real server (name it below).
  - [ ] Nothing to test — documentation, formatting or build-only change.
- [ ] `lib/*` and `common/` stay platform-free: no `org.bukkit`, `io.papermc`,
      `com.velocitypowered` or `net.md_5` import outside `platform/*`, and
      `quark-common` gained no runtime dependency.
- API compatibility, where this touches a published module:
  - [ ] Source- and binary-compatible with the last release, or
  - [ ] Breaking, with the old API kept as `@Deprecated(forRemoval = true)`.
- Platform reach, where this touches an adapter or the scheduler:
  - [ ] Paper
  - [ ] Folia
  - [ ] Spigot / Bukkit
  - [ ] Velocity
  - [ ] BungeeCord
- [ ] Gradle plugin changes do no resolution or network access during
      configuration, and a new module is added to `bom/` and to the plugin's
      functional-test publish list.
- [ ] Documentation updated, where this changes something a user can see. The
      docs site lives in [BX-Team/code] — open a pull request there, then paste
      its link here and this one's link there, so a reviewer can see the two
      halves belong together.
- [ ] This pull request has one subject. A formatting sweep bundled with a fix is
      harder to review and harder to revert.
- [ ] Fits [CONTRIBUTING.md].

<!--
Want a build of this branch to review? Add the "📦 upload artifacts" label and the
PR check attaches a Maven repository with every Quark module and the Gradle plugin
to its run — the Artifacts box at the bottom of the run summary. The build runs
either way; only the upload depends on the label.

Found a security issue? Do not open a pull request in the open — see [SECURITY.md].
-->

[CONTRIBUTING.md]: https://github.com/BX-Team/.github/blob/master/CONTRIBUTING.md
[SECURITY.md]: https://github.com/BX-Team/.github/blob/master/SECURITY.md
[BX-Team/code]: https://github.com/BX-Team/code
