# Quark

A small framework for Minecraft plugin developers: a runtime Maven dependency
manager, annotation-driven configs, a logger facade, an update checker, thin
platform adapters and a Gradle plugin that also runs dev servers. Java 17
libraries plus one Kotlin Gradle plugin, all in one Gradle build. The structural
fact everything else follows from: **every module is optional and Quark is
shaded and relocated into each plugin**, so a plugin carries only the modules it
asked for, in its own package.

## Architecture

The libraries under `lib/` know nothing about server platforms; they depend on
`quark-common` only and talk to the world through JDK types (`Executor`,
`CompletableFuture`, `Path`). The adapters under `platform/` implement
`quark-platform-api` for one server each and are the only place with
`org.bukkit`, `com.velocitypowered` or `net.md_5` imports. Anything heavy a
library needs at runtime (snakeyaml-engine for configs, ASM for relocation) is
not shipped: it is found on the class path or downloaded by `quark-dependency`.

The Gradle plugin is the user-facing glue. `quark { platform = ...; modules(...) }`
adds the adapter and modules from the BOM, the `quark` configuration becomes the
runtime manifest (`META-INF/quark/manifest`), Shadow relocates Quark to
`<group>.libs.quark`, `devServer { }` registers `runServer`, and `pluginYml { }`
writes the descriptor of the platform (`plugin.yml`, `paper-plugin.yml`, `bungee.yml`,
`velocity-plugin.json`).

| Module | Responsibility |
| ------ | -------------- |
| `common/` (`quark-common`) | `SemanticVersion`, the `QuarkLogger` contract and its JUL default. No runtime dependencies, ever. |
| `lib/dependency/` (`quark-dependency`) | `LibraryManager`: resolve, download, relocate and load Maven dependencies. Own ASM relocator in `relocation.asm`. |
| `lib/logger/` (`quark-logger`) | SLF4J and Log4j adapters for `QuarkLogger`, the global debug switch. |
| `lib/update/` (`quark-update`) | `UpdateChecker` with Modrinth, Hangar, GitHub, Spigot, Reposilite and JSON-URL providers on `java.net.http`. |
| `lib/config/core/` (`quark-config`) | `QuarkConfig`, the `ConfigNode` tree, `TypeSerializer`/`SerdesRegistry`, migrations, the validation hook and `BackendProvider`. |
| `lib/config/format-yaml/` (`quark-config-yaml`) | YAML on snakeyaml-engine, which is `compileOnly` and loaded through `BackendProvider`. |
| `lib/config/validator/` (`quark-config-validator`) | `@NotNull`, `@Min`, `@Max`, `@Pattern`, `@Check`. |
| `lib/config/serdes-bukkit/` (`quark-config-serdes-bukkit`) | ItemStack, Location, Sound, Component and other `ConfigurationSerializable` types. |
| `platform/api/` (`quark-platform-api`) | `Platform`, `Scheduler`, `PlatformVersion`, `Platform.detect()` via `ServiceLoader`. |
| `platform/{bukkit,paper,velocity,bungee}/` | One adapter each: platform, scheduler, `ClassLoaderAppender` and update notifier. |
| `bom/` (`quark-bom`) | Pins every module to one version. |
| `gradle/plugin/` | `org.bxteam.quark` (and the deprecated `org.bxteam.runserver` shim). |
| `build-logic/` | Convention plugins: `quark.base`, `quark.published`, `quark.platform`, `quark.bom`. |

Optional pieces are wired with `ServiceLoader`, never with a hard reference:
`ClassLoaderAppenderProvider` and `PlatformProvider` in the adapters, and
`ConfigFormat`, `SerdesPack` and `ConfigValidator` in the config modules. A
module on the class path switches itself on; a missing one costs nothing.

### Decisions that are settled

- **`lib/*` never depends on `quark-platform-api` or a server API.** Four libraries
  times four platforms would be sixteen artifacts. A library that needs a thread
  takes an `Executor`; the adapter offers `scheduler().asExecutor()`.
- **`quark-common` has no runtime dependencies.** It is in every plugin. Only
  `compileOnly` annotations are allowed.
- **Quark is always relocated.** Two plugins with different Quark versions on one
  server otherwise load each other's classes. Statics are therefore per plugin,
  which `BackendProvider` relies on.
- **Heavy backends are loaded at runtime, not shipped.** The order is class path,
  then `quark-dependency`, then an error that says what to add. snakeyaml-engine is
  relocated by the Gradle plugin to `<quarkPackage>.snakeyaml`, and the runtime
  download is relocated to the same package.
- **The relocator is ours, on ASM only.** `relocation.asm` replaced
  `me.lucko:jar-relocator`, whose POM pulled a second ASM. Its classes are defined
  by `RelocatorClassLoader` from the plugin JAR next to the downloaded ASM.
- **One version per library.** The resolver keeps the version a root declares,
  otherwise the newest one requested (`MavenVersions`, not `SemanticVersion`, whose
  fallback compares `1.10` below `1.9`). The Gradle manifest is loaded
  non-transitively: Gradle already chose the versions, and re-resolving POMs would
  bring the losers back.
- **The manifest has one format.** The 1.x text files are not read; plugins built
  with the 1.x Gradle plugin must be rebuilt.
- **`Scheduler.sync()` on Folia goes to the global region scheduler.** Work bound
  to an entity or a location uses the explicit `sync(entity/location, ...)`
  overloads.
- **Configs are regenerated from the class on save.** Key order is field order,
  comments come from `@Comment`/`@Header`, hand-written comments are lost. Unknown
  keys are kept unless `removeOrphans()`. A load is all or nothing: a validation
  failure throws `ConfigValidationException`, leaves the file untouched and keeps
  the old values.
- **No Lombok.** Models are records, utilities are `final` classes with a private
  constructor.
- **Java 17 for every module.** The Gradle plugin is built with Gradle 9 and must
  keep working on Gradle 8.14 — `DevServerFunctionalTest` runs it there.
- **`pluginYml { }` replaces plugin-yml, without Jackson.** One block for every
  platform, dependencies declared once and translated per format, a small
  hand-written YAML/JSON writer. Strings SnakeYAML 1.1 would misread (`1.20`, `yes`)
  are quoted. Generation is opt-in, so hand-written descriptors keep working.
- **`runServer` keeps its name.** People have IDE run configurations on it; other
  dev servers are `run<Name>Server`.

## Commands

```bash
./gradlew build --configuration-cache                           # compile, javadoc, unit and Gradle functional tests
./gradlew checkArtifactSize --configuration-cache               # common + platform-api + dependency + paper must stay under 200 KB
./gradlew :quark-config:test --tests '*SerdesTest*'             # one module, one class
./gradlew :quark-gradle-plugin:test                             # TestKit tests; publishes every module to build/local-repo first
./gradlew publishToMavenLocal                                   # try a change in a real plugin
```

The pull request check runs `build` and `checkArtifactSize`, both with the
configuration cache. Easy to forget: the Gradle plugin's functional tests depend
on the publish tasks of every module listed in `gradle/plugin/build.gradle.kts`,
so a new module must be added there and to `bom/`.

## Code Guidelines

### Comments

- NO file-header banners and NO divider comments (`// --- helpers ---`). Group
  code with methods, not comment art.
- Add an inline comment only where the code is genuinely non-obvious — a real
  footgun, a wire-format quirk, a reason a thing is done backwards. Then keep it
  to a line or two.
- Don't narrate the obvious. If a comment restates the next line, delete it.
- Doc comments on public items are fine and should say *why*, in one or two
  sentences.

### Style

- There is no formatter; match the file you are editing. Four spaces, `final`
  utility classes, `requireNonNull(x, "X cannot be null")` on public entry points,
  `@NotNull`/`@Nullable` from JetBrains annotations.
- Every public type and method in a published module has Javadoc; `javadoc` runs
  in `build`.
- Breaking a published API means `@Deprecated(forRemoval = true)` in the same
  module first, not a silent change.
- In the Gradle plugin, extension state is `Property`/`ListProperty`/`SetProperty`
  only, tasks are registered with `tasks.register`, and nothing resolves or touches
  the network during configuration.

### User-facing strings

Everything a plugin developer or server owner reads — log lines, exception
messages, Gradle errors — is English and says what to do next: "Add
quark-config-yaml (quark { modules(QuarkModule.CONFIG) }) or set one with
options.format(...)", not "format missing".

### Relocation and class loading gotchas

- **Shadow rewrites string literals that name a relocated class.** That is how
  `YamlFormat` finds its relocated probe class. A literal that must keep the
  original name is built at runtime — `"org{}snakeyaml{}engine".replace("{}", ".")`
  — and a package Quark needs at runtime is derived from a class
  (`getPackageName()`), not written out.
- **Plugin class loaders ask the server first.** A library the server already
  ships in the same package wins over the downloaded one; `LibraryManager` warns
  about it, relocation fixes it.
- **Classes in `relocation.asm` may use only the JDK and ASM.** They run in a
  class loader that cannot see Quark; throw JDK exceptions there.
- **A `ServiceLoader` provider must not touch platform classes before
  `supports()`.** On the wrong server the class load is the crash. Discovery
  catches `LinkageError` and skips the provider.
- **A class that references an optional dependency stays separate from the class
  that checks for it** (`DependencyBackendLoader`, `SnakeYamlBackend`,
  `ComponentSerializer`): the check must run before the JVM resolves the
  reference.
- **Paper plugins (`paper-plugin.yml`) load libraries through the library loader**
  of `PaperPluginClassLoader`, not the plugin class loader itself; see
  `PaperClassLoaderAppenderProvider`.

### Testing

Tests sit next to each module in `src/test`. Resolver and relocator tests run
against `FakeMavenRepository`, so they never touch the network; the Gradle plugin
is tested with TestKit against `build/local-repo`. A real download from Maven
Central is checked by hand, not in CI. One test per real trap, not one per method.

## Bash Guidelines

- Don't pipe output through `head`/`tail`/`less` to truncate — use tool-native
  flags (`git log -n 10`, `./gradlew build --console=plain`). Read the full output.
- Don't create scratch files (scripts, notes) unless asked.
- When given failures, just fix them — don't argue about who introduced them.
