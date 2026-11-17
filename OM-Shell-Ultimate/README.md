# OM Shell Ultimate 2.1.1 — Java 25 Edition

OM Shell is an independent command interpreter and scripting environment written in Java. It tokenizes and parses its own command language, runs built-ins in-process, and starts external operating-system programs with Java `ProcessBuilder`. Bash is not used as the interpreter, and the repository contains no Bash test scripts.

The source is deliberately kept readable: descriptive names, normal control flow, small command classes, and comments that explain decisions. Java source files are physically kept directly under `src/main/java` and `src/test/java`; package declarations such as `com.om.shell.parser` are retained.

> Compatibility statement: OM Shell is **not a complete Bash replacement**. This release adds useful array support and OS signal traps, but full Bash grammar and POSIX terminal process-group control require further work and platform-specific native integration.

## Added in 2.1.1

- Indexed arrays: `items=(red green blue)`, `items[1]=green`, `items+=(gold)`, `${items[1]}`, `${items[@]}`, `${#items[@]}`.
- Associative arrays: `declare -A colors=([red]=warm [blue]=cool)`, `${colors[red]}`, `${!colors[@]}`, `${#colors[@]}`.
- Array element removal with `unset items[1]` and full array removal with `unset items`.
- `declare -a`, `declare -A`, and basic `declare -p` support.
- Signal trap registration for supported operating-system signals: `INT`, `TERM`, `HUP`, `QUIT`, `USR1`, `USR2`, `ALRM`, and `PIPE`, in addition to `EXIT` / `0`.
- `trap -l` lists the signal names recognized by the shell.
- Signal registration uses the JDK's optional `sun.misc.Signal` bridge through reflection. It is platform/JDK dependent; unsupported signals are rejected instead of being silently claimed as supported.
- Additional dependency-free tests for indexed arrays, associative arrays, and signal-trap dispatch.

## Existing functionality

### Interactive use
- Interactive prompt and persistent history in `~/.omsh_history`.
- `-c` command mode and `.om` script mode with positional parameters.

### Parsing and execution
- Java lexer and command-line parser.
- Single/double quotes and backslash escaping.
- `;`, `&&`, `||`, background `&`, pipelines, and supported redirections.
- Built-ins run inside the Java process. External executables are launched directly through `ProcessBuilder`.

### Expansions and scripting
- Local and exported variables.
- `$NAME`, `${NAME}`, `$?`, `$$`, `$!`, positional parameters, `$*`, and `$@` where supported.
- `$(...)` and backtick command substitution.
- Integer arithmetic expansion `$((...))`.
- Aliases and shell functions.
- `if` / `elif` / `else` / `fi`, `for`, `while`, and `until` constructs.
- `source` / `.` runs a script in the current shell instance.

### Built-ins
`help`, `cd`, `pwd`, `echo`, `printf`, `cat`, `mkdir`, `touch`, `rm`, `history`, `clear`, `exit`, `export`, `declare`, `unset`, `env`, `alias`, `unalias`, `type`, `which`, `jobs`, `fg`, `bg`, `kill`, `source`, `true`, `false`, `test`, `[`, `sleep`, `wait`, `mktemp`, `grep`, `wc`, `tr`, `trap`, `set`, and `shopt`.

## Important limitations

The following are **not** complete in this release:

- Full Bash grammar, complete nested compound-command parsing, and all quoting/escaping edge cases.
- Complete array semantics: sparse-array indexing rules, array slicing, all compound assignments, namerefs, and every Bash array expansion operator.
- Full parameter expansion such as all `${parameter#word}`, `${parameter/pattern/replacement}`, indirect expansion, and locale-specific behavior.
- Brace expansion, pathname globbing, process substitution, and complete here-document parsing/expansion.
- **True terminal job control**: OM Shell can launch and wait for background work, but does not yet create POSIX process groups, transfer the controlling terminal with `tcsetpgrp`, or fully reproduce Ctrl-Z / `fg` / `bg` behavior. Java's standard `Process` API does not expose those primitives portably. A native POSIX layer (for example JNI/JNA or a Java 25 Foreign Function & Memory implementation) is needed for this.
- Signal support is limited to signals the current JDK/OS exposes through `sun.misc.Signal`. It is not a full replacement for Bash's trap semantics, and Java cannot catch uncatchable signals such as `KILL` or `STOP`.
- `set` and `shopt` do not implement the full Bash option system.
- External commands and signal names differ by operating system; programs such as `git`, `docker`, and `python` must be installed separately.

## Project structure

```text
OM-Shell-Ultimate/
├── pom.xml
├── README.md
├── scripts/
│   ├── example.om
│   └── smoke-test.om
└── src/
    ├── main/java/com/om/shell/   # all production Java classes
    └── test/java/com/om/shell/   # all Java smoke tests
```

All Java classes use the single `com.om.shell` package, and the physical folders match that package. This avoids IDE package-path mismatch errors and keeps the source tree compact.

## Requirements

- JDK 25
- Maven 3.9+
- A terminal for interactive use

No third-party Java runtime dependencies are required.

## Build and run

```bash
java -version
mvn -version
mvn clean package
```

Interactive shell:

```bash
mvn exec:java
```

Single command:

```bash
mvn exec:java -Dexec.args='-c "echo Hello from OM Shell"'
```

Run a script:

```bash
mvn exec:java -Dexec.args='scripts/example.om'
```

Run the packaged JAR:

```bash
java -jar target/om-shell-2.1.1.jar
```

## Dependency-free smoke tests

After compilation:

```bash
java -cp target/classes:target/test-classes com.om.shell.ParserSmokeTest
java -cp target/classes:target/test-classes com.om.shell.ShellSmokeTest
java -cp target/classes:target/test-classes com.om.shell.ArrayAndSignalSmokeTest
```

PowerShell uses `;` instead of `:` between classpath entries.
