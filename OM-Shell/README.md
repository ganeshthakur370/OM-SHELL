# OM Shell

**OM Shell** is a command-line shell developed using Java. The goal of this project is to build an independent shell interpreter and understand how commands are parsed, processed and executed.

Instead of simply using Bash internally, OM Shell has its own command-processing engine, lexer, parser and scripting system.

## Features

- Interactive command execution
- Custom lexer and parser
- Built-in shell commands
- Variables and environment variables
- Command substitution and arithmetic expansion
- Conditional statements and loops
- Shell script execution (`.om` files)
- Indexed and associative arrays
- Functions
- Pipes and I/O redirection
- Background process execution
- Job management
- Signal handling
- Command history

*Some features may have limitations compared with Bash.*

## Built With

- Java 25
- Apache Maven
- Ubuntu / Linux
- Git

## Getting Started

### Requirements

- JDK 25
- Maven
- Ubuntu or another supported Linux environment

### Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/OM-SHELL.git
cd OM-SHELL
```

### Build the project

```bash
mvn clean package
```

### Run OM Shell

```bash
mvn exec:java
```

## Shell Scripting

OM Shell supports its own `.om` script files.

Example (`hello.om`):

```bash
name="OM Shell"

echo "Welcome to $name"

for i in 1 2 3
do
    echo $i
done
```

Run the script using:

```bash
mvn exec:java -Dexec.args='hello.om'


## Development

This project is actively being developed. The focus is on improving the parser, scripting capabilities, process management and overall shell architecture.

The long-term goal is to make OM Shell a more complete and extensible command-line environment.

## Contributing

Suggestions, bug reports and improvements are welcome. Feel free to fork the repository and submit a pull request.

## Author
ADITYA THAKUR

Developed as an independent Java systems-programming project.

**GitHub:** https://github.com/ganeshthakur370