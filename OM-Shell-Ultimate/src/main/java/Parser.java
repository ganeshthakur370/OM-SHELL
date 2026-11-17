
import java.util.ArrayList;
import java.util.List;

/**
 * Standard parser utility for OM Shell.
 * Provides backwards-compatible parse() methods returning stage word lists,
 * as well as parsing into structured CommandLine execution objects.
 */
public final class Parser {

    private Parser() {
        // Utility class
    }

    /**
     * Parses a command line string into a list of pipeline stages, where each stage
     * is a list of token strings (words and redirection operators).
     *
     * @param commandLine The raw command string to parse
     * @return List of pipeline stages, each being a list of tokens
     * @throws IllegalArgumentException on syntax error, unclosed quote, or empty pipeline stage
     */
    public static List<List<String>> parse(String commandLine) {
        if (commandLine == null) {
            return new ArrayList<>();
        }

        List<List<String>> stages = new ArrayList<>();
        List<String> currentStage = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char activeQuote = 0;
        boolean escaped = false;

        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);

            if (escaped) {
                word.append(ch);
                escaped = false;
                continue;
            }

            if (ch == '\\' && activeQuote != '\'') {
                escaped = true;
                continue;
            }

            if (activeQuote != 0) {
                if (ch == activeQuote) {
                    activeQuote = 0;
                } else {
                    word.append(ch);
                }
                continue;
            }

            if (ch == '\'' || ch == '"') {
                activeQuote = ch;
                continue;
            }

            if (Character.isWhitespace(ch)) {
                flushWord(word, currentStage);
                continue;
            }

            if (ch == '|') {
                flushWord(word, currentStage);
                if (currentStage.isEmpty()) {
                    throw new IllegalArgumentException("empty pipeline stage");
                }
                stages.add(currentStage);
                currentStage = new ArrayList<>();
                continue;
            }

            // Check for redirection operators: <, >, >>, 2>, 2>&1
            if (ch == '>' || ch == '<' || (ch == '2' && i + 1 < commandLine.length() && commandLine.charAt(i + 1) == '>')) {
                flushWord(word, currentStage);
                String op;
                if (ch == '2') {
                    if (i + 3 < commandLine.length() && commandLine.substring(i, i + 4).equals("2>&1")) {
                        op = "2>&1";
                        i += 3;
                    } else if (i + 2 < commandLine.length() && commandLine.substring(i, i + 3).equals("2>>")) {
                        op = "2>>";
                        i += 2;
                    } else {
                        op = "2>";
                        i++;
                    }
                } else if (ch == '>' && i + 1 < commandLine.length() && commandLine.charAt(i + 1) == '>') {
                    op = ">>";
                    i++;
                } else {
                    op = String.valueOf(ch);
                }
                currentStage.add(op);
                continue;
            }

            word.append(ch);
        }

        if (activeQuote != 0) {
            throw new IllegalArgumentException("unclosed quote");
        }

        if (escaped) {
            word.append('\\');
        }

        flushWord(word, currentStage);

        if (!currentStage.isEmpty()) {
            stages.add(currentStage);
        }

        return stages;
    }

    /**
     * Parses a command string into a structured CommandLine model with typed
     * pipeline stages and redirections.
     *
     * @param commandLine The raw command string
     * @return Parsed CommandLine object
     */
    public static CommandLine parseCommandLine(String commandLine) {
        Lexer lexer = new Lexer(commandLine);
        List<Token> tokens = lexer.tokenize();
        CommandLineParser parser = new CommandLineParser(tokens);
        return parser.parse();
    }

    private static void flushWord(StringBuilder word, List<String> stage) {
        if (word.length() > 0) {
            stage.add(word.toString());
            word.setLength(0);
        }
    }
}
