# OM Shell Ultimate 3.0 — Modular Advanced Java Shell Engine (Java 25)

**OM Shell Ultimate 3.0** is an independent, pure Java command interpreter, scripting engine, and execution runtime built on **Java 25** and **Maven 3.9+**. It contains **zero external runtime dependencies** (pure standard Java SE APIs) and completely replaces Bash as an interpreter with human-written, clean, idiomatic Java 25 code.

---

## 🌟 What's New in Version 3.0

Version 3.0 represents a major architectural evolution, implementing all major POSIX and Bash compatibility milestones:

### 2.3 Lexer and Parser Improvements
- **ANSI-C Quoting**: `$'...'` escape sequence decoding (`\n`, `\t`, `\r`, `\a`, `\b`, `\e`, `\xHH`, `\0NNN`, etc.).
- **Locale Quoting**: `$"..."` quoting support.
- **Extended Redirections**:
  - Here-Strings: `<<< "string data"`
  - Merged standard output & error: `&>`, `&>>`, `>&`
  - Explicit clobber: `>|`
  - File descriptor duplicates: `2>&1`, `1>&2`
- **Compound Language Tokens**: Brackets `[[` and `]]`, arithmetic parens `((` and `))`, command groups `{` and `}`, and subshells `(` and `)`.

### 2.4 Indexed and Associative Arrays
- **Sparse Indexed Arrays**: Support for arbitrary indices (`numbers=([2]=two [5]=five [10]=ten)`), preserving sparse order via `TreeMap<Integer, String>`.
- **Associative Arrays**: String-keyed key-value dictionaries declared via `declare -A` (`declare -A servers=([web]=nginx [port]=8080)`).
- **Compound Assignments & Appends**: `colors=(red green blue)`, `colors+=(gold silver)`.
- **Array Queries & Operations**:
  - Element access: `${arr[index]}` and `${assoc[key]}`
  - All elements: `${arr[@]}` and `${arr[*]}`
  - Keys / Indices: `${!arr[@]}` and `${!assoc[@]}`
  - Element count: `${#arr[@]}` and `${#assoc[@]}`
  - Slicing: `${arr[@]:offset:length}` and `${arr[@]:offset}`
  - Element unsetting: `unset arr[index]` or `unset assoc[key]`

### 2.5 Advanced Parameter Expansion Subsystem
- **Default & Alternative Values**:
  - `${var:-default}`: Use default if unset or null.
  - `${var:=default}`: Assign default to variable if unset or null.
  - `${var:+alternate}`: Use alternate value if set and non-null.
  - `${var:?error_message}`: Write error message and abort if unset or null.
- **Substring Expansion**:
  - `${var:offset:length}`, `${var:offset}` with support for positive and negative offsets (`${var: -4:2}`).
- **Prefix Pattern Removal**:
  - Shortest prefix removal: `${var#pattern}`
  - Longest prefix removal (basename): `${var##*/}`
- **Suffix Pattern Removal**:
  - Shortest suffix removal (dirname / ext): `${var%pattern}`, `${var%.*}`
  - Longest suffix removal (stem): `${var%%.*}`
- **Pattern Replacement**:
  - First occurrence: `${var/pattern/replacement}`
  - All occurrences: `${var//pattern/replacement}`
  - Prefix anchor: `${var/#pattern/replacement}`
  - Suffix anchor: `${var/%pattern/replacement}`
- **Case Modification**:
  - Uppercase first character: `${var^}`
  - Uppercase all characters: `${var^^}`
  - Lowercase first character: `${var,}`
  - Lowercase all characters: `${var,,}`
- **Indirect Expansion**:
  - Dereference variable names: `${!pointer_var}`

### 2.6 Process Groups & Terminal Job Control
- **Job States**: Complete lifecycle tracking: `RUNNING`, `STOPPED`, `DONE`, `FAILED`, `TERMINATED`.
- **Job Table Management**:
  - `jobs`: List background and tracked jobs.
  - `jobs -l`: List jobs with process IDs (PID).
  - `jobs -p`: List process IDs only.
  - `jobs -r`: Restrict output to running jobs.
  - `jobs -s`: Restrict output to stopped jobs.
- **Foreground / Background Transitions**:
  - `fg [%id]`: Bring background job to foreground and wait.
  - `bg [%id]`: Resume stopped job in the background.
  - `disown [-a] [-r] [%id...]`: Remove jobs from shell table.
  - `wait [%id | pid]`: Wait for jobs or specific processes.

### 2.7 Advanced Signal Handling
- **Reflection Bridge**: Safe dynamic binding to `sun.misc.Signal` for POSIX and operating-system signals (`INT`, `TERM`, `HUP`, `QUIT`, `USR1`, `USR2`, `ALRM`, `PIPE`).
- **Signal Trap Dispatch**:
  - `trap 'cmd' SIGNAL...`: Register trap handler.
  - `trap -p [SIG...]`: Print registered trap specifications.
  - `trap -l`: List supported signal numbers and names.
  - `trap - SIGNAL...`: Reset signal to default handler.
  - Pseudo-signals: `EXIT` (run on shell exit), `ERR` (run on command error), `DEBUG`.
- **Thread-Safe Queuing**: Deferred signal delivery queue (`pendingSignals`) ensuring signals are cleanly processed at statement boundaries.

### 2.8 Bash Compatibility Improvements
- **Extended Test Construct (`[[ ... ]]`)**:
  - Glob pattern matching: `[[ $file == *.txt ]]`, `[[ $file != *.log ]]`
  - Regular expression matching: `[[ $str =~ ^[0-9]+$ ]]`
  - String comparisons: `<`, `>`, `==`, `!=`
  - Numeric tests: `-eq`, `-ne`, `-lt`, `-le`, `-gt`, `-ge`
  - File tests: `-f`, `-d`, `-e`, `-s`, `-r`, `-w`, `-x`, `-z`, `-n`
  - Compound logical operators: `&&`, `||`, `!`, `( ... )`
- **Arithmetic Evaluation (`(( ... ))` and `let`)**:
  - `(( expr ))`: Evaluates arithmetic, returns exit code 0 if non-zero, 1 if zero. Supports in-place assignment (`(( count = 5 + 3, x += 2 ))`).
  - `let "expr" ...`: Evaluates each argument as an arithmetic expression.
- **Commands**:
  - `read [-r] [-p prompt] [-a array] [vars...]`: Read line from stdin into array or variables.
  - `eval [args...]`: Concatenate and execute arguments in the current shell.
  - `shift [n]`: Shift positional parameters by `n` positions (default 1).
  - `case WORD in ... esac`: Multi-branch case pattern matching supporting globs and `|` alternates.
  - `set`: Shell options management (`-e`/`+e` errexit, `-u`/`+u` nounset, `-x`/`+x` xtrace, `-o pipefail`/`+o pipefail`).
- **Command Groups & Subshells**:
  - `{ list; }`: Group commands executed within current shell environment.
  - `( list )`: Subshell isolation executed with cloned environment.

---

## 🏗️ Architecture & Subsystems (3.0 Modular Architecture)

OM Shell 3.0 is partitioned into cohesive, decoupled subsystems:

1. **`Lexer`**: Streaming tokenizer supporting multi-level quoting (single, double, ANSI-C `$'...'`, locale `$"..."`), backslash escapes, here-strings `<<<`, here-docs `<<`, and compound symbols.
2. **`CommandLineParser`**: Recursive descent pipeline parser building structured `CommandLine` and `PipelineStage` objects with operators (`&&`, `||`, `;`, `&`, `|`).
3. **`Expander` & `ParameterExpander`**: Multi-phase expansion pipeline executing alias expansion, arithmetic evaluation, command substitution, variable and parameter transformations, and tilde resolution.
4. **`ArithmeticEvaluator`**: Recursive-descent arithmetic parser supporting all standard arithmetic, bitwise (`&`, `|`, `^`, `~`, `<<`, `>>`), comparison, ternary (`?:`), comma, and compound assignment operators (`+=`, `-=`, etc.).
5. **`ScriptEngine`**: Control flow parser and interpreter managing blocks (`if`/`elif`/`else`/`fi`, `for`/`done`, `while`/`done`, `until`/`done`, `case`/`esac`, `{ ... }`, `( ... )`, functions).
6. **`PipelineExecutor`**: Multi-stage process and stream plumbing coordinator supporting built-ins, external `ProcessBuilder` execution, piping, and all redirection forms (`<`, `>`, `>>`, `&>`, `&>>`, `>|`, `2>&1`, `1>&2`, `<<<`).
7. **`JobManager`**: Background job scheduler and tracker maintaining PID associations, state transitions, futures, and thread-safe job tables.
8. **`SignalManager`**: Operating system signal bridge, signal queuing, and trap registry.
9. **`BuiltinRegistry`**: Registry of in-process built-in command handlers.
10. **`Shell`**: Central environment facade managing variables, array scopes, attributes, working directory, history, and options.

---

## 🧪 Comprehensive Test Suite (7 Suites, 100% Passing)

The project includes 7 regression and integration test suites:

| Test Suite | Purpose | Status |
| :--- | :--- | :--- |
| `ParserSmokeTest` | Tokenizer, command line stages, and redirection parsing | **PASS** |
| `ShellSmokeTest` | Core shell built-ins, control flow, pipelines, background execution | **PASS** |
| `ArrayAndSignalSmokeTest` | Indexed & associative arrays, OS signal traps | **PASS** |
| `AdvancedExpansionTest` | 2.5 Parameter expansions (substring, patterns `#`, `%`, `/`, case, indirect) | **PASS** |
| `BashCompatibilityTest` | `[[ ... ]]` regex/glob, `(( ... ))`, `let`, `eval`, `read`, `shift`, `case`, subshells | **PASS** |
| `JobControlSmokeTest` | Job states, `jobs -l/-p/-r/-s`, `fg`, `bg`, `disown` | **PASS** |
| `V3ModularEngineTest` | Master integration suite verifying all 3.0 features end-to-end | **PASS** |

---

## 🚀 Building & Running

### Prerequisites
- **JDK 25** (`javac`, `java`)
- **Maven 3.9+**

### Compile & Package
```bash
mvn clean package
```
This produces `target/om-shell-3.0.0.jar`.

### Run Interactive Shell
```bash
java -jar target/om-shell-3.0.0.jar
mvn exec:java
```

### Run a Command String (-c)
```bash
java -jar target/om-shell-3.0.0.jar -c "echo 'Hello from OM Shell 3.0'"
```

### Run a Script (.om)
```bash
java -jar target/om-shell-3.0.0.jar scripts/v3-feature-demo.om
```

### Run the Test Suite
```bash
javac --release 25 -cp target/classes -d target/test-classes src/test/java/*.java
java -cp "target/classes;target/test-classes" V3ModularEngineTest
```

---

## 📜 Complete Built-in Command Reference

| Command | Syntax / Usage | Description |
| :--- | :--- | :--- |
| `cd` | `cd [dir]` | Change current working directory (supports `~`, `-`) |
| `pwd` | `pwd` | Print current working directory |
| `echo` | `echo [-n] [-e] [args...]` | Output line with optional escape interpretation |
| `printf` | `printf format [args...]` | Formatted output |
| `cat` | `cat [file...]` | Concatenate and print files (or stdin) |
| `declare` | `declare [-aAfFiprx] [name[=val] ...]` | Declare variables, arrays, attributes |
| `read` | `read [-r] [-p prompt] [-a arr] [vars...]` | Read line into array or variables |
| `eval` | `eval [args...]` | Concatenate and execute arguments |
| `let` | `let "expr" ...` | Evaluate arithmetic expressions |
| `shift` | `shift [n]` | Shift positional parameters |
| `disown` | `disown [-a] [-r] [%id...]` | Remove jobs from active table |
| `jobs` | `jobs [-l|-p|-r|-s] [%id...]` | List active background jobs |
| `fg` | `fg [%id]` | Bring job to foreground |
| `bg` | `bg [%id]` | Resume job in background |
| `kill` | `kill [-sig] pid...` | Send signal or terminate processes |
| `trap` | `trap [-lp] [[cmd] signal...]` | Register exit and signal trap handlers |
| `set` | `set [-e|+e] [-u|+u] [-x|+x] [-o opt]` | Configure shell execution options |
| `export` | `export [name[=value]...]` | Export variables to child processes |
| `unset` | `unset [name...]` | Unset variables or array elements (`arr[i]`) |
| `test` / `[` | `test expr` / `[ expr ]` | Evaluate POSIX condition expressions |
| `[[` | `[[ expr ]]` | Extended condition test with regex and glob |
| `source` / `.` | `source file [args...]` | Execute script in current environment |
| `alias` | `alias [name=value...]` | Define command aliases |
| `unalias` | `unalias name...` | Remove aliases |
| `type` | `type name...` | Display command type information |
| `which` | `which command...` | Locate executable in PATH |
| `grep` | `grep [-i] [-v] [-n] pattern [files...]` | Search text patterns |
| `wc` | `wc [-l] [-w] [-c] [files...]` | Print newline, word, and byte counts |
| `tr` | `tr [-d] set1 [set2]` | Translate or delete characters |
| `sleep` | `sleep seconds` | Pause execution |
| `wait` | `wait [%id \| pid...]` | Wait for process or background job |
| `history` | `history [-c]` | Display command history |
| `clear` | `clear` | Clear terminal display |
| `true` | `true` | Return success exit status (0) |
| `false` | `false` | Return failure exit status (1) |
| `exit` | `exit [status]` | Exit the shell |
| `help` | `help [command]` | Display built-in command documentation |
