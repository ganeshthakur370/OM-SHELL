
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a sequence of tokens into structured CommandLine pipelines.
 */
public class CommandLineParser {

    private final List<Token> tokens;
    private int cursor = 0;

    public CommandLineParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parses the full token list into a chain of CommandLine objects.
     *
     * @return First CommandLine in the execution chain, or null if input is empty
     */
    public CommandLine parse() {
        CommandLine firstCommand = null;
        CommandLine currentCommand = null;

        while (!isAtEnd()) {
            CommandLine cmd = parsePipeline();
            if (cmd == null || cmd.isEmpty()) {
                // Skip empty commands e.g. after a trailing semicolon
                continue;
            }

            if (firstCommand == null) {
                firstCommand = cmd;
                currentCommand = cmd;
            } else {
                currentCommand.setNextCommand(cmd);
                currentCommand = cmd;
            }

            // Look for chaining operator or background ampersand
            if (match(TokenType.AND_IF)) {
                cmd.setNextOperator(CommandLine.Operator.AND_IF);
            } else if (match(TokenType.OR_IF)) {
                cmd.setNextOperator(CommandLine.Operator.OR_IF);
            } else if (match(TokenType.SEMICOLON)) {
                cmd.setNextOperator(CommandLine.Operator.SEMICOLON);
            }
        }

        return firstCommand;
    }

    private CommandLine parsePipeline() {
        List<PipelineStage> stages = new ArrayList<>();
        boolean background = false;

        while (!isAtEnd()) {
            PipelineStage stage = parseStage();
            if (stage != null && !stage.isEmpty()) {
                stages.add(stage);
            }

            if (match(TokenType.PIPE)) {
                if (stage == null || stage.isEmpty()) {
                    throw new IllegalArgumentException("empty pipeline stage");
                }
                // Expect another stage after pipe
                if (isAtEnd() || check(TokenType.SEMICOLON) || check(TokenType.AND_IF) || check(TokenType.OR_IF)) {
                    throw new IllegalArgumentException("empty pipeline stage");
                }
                continue;
            }

            if (match(TokenType.AMPERSAND)) {
                background = true;
                break;
            }

            if (check(TokenType.SEMICOLON) || check(TokenType.AND_IF) || check(TokenType.OR_IF)) {
                break;
            }

            if (isAtEnd()) {
                break;
            }
        }

        if (stages.isEmpty()) {
            return null;
        }

        return new CommandLine(stages, background);
    }

    private PipelineStage parseStage() {
        List<String> words = new ArrayList<>();
        List<Redirection> redirections = new ArrayList<>();

        while (!isAtEnd() && !check(TokenType.PIPE) && !check(TokenType.SEMICOLON)
                && !check(TokenType.AND_IF) && !check(TokenType.OR_IF) && !check(TokenType.AMPERSAND)) {

            Token token = peek();

            if (token.getType() == TokenType.WORD) {
                words.add(advance().getValue());
                continue;
            }

            if (token.getType() == TokenType.REDIRECT_ERR_TO_OUT) {
                advance();
                redirections.add(new Redirection(Redirection.Type.ERROR_TO_OUTPUT, "&1"));
                continue;
            }

            if (isRedirectionToken(token.getType())) {
                Token redirToken = advance();
                if (isAtEnd() || peek().getType() != TokenType.WORD) {
                    throw new IllegalArgumentException("missing redirection target");
                }
                Token targetToken = advance();
                redirections.add(new Redirection(mapRedirectionType(redirToken.getType()), targetToken.getValue()));
                continue;
            }

            break;
        }

        return new PipelineStage(words, redirections);
    }

    private boolean isRedirectionToken(TokenType type) {
        return type == TokenType.REDIRECT_IN
                || type == TokenType.REDIRECT_OUT
                || type == TokenType.REDIRECT_APPEND
                || type == TokenType.REDIRECT_ERR
                || type == TokenType.REDIRECT_ERR_APPEND
                || type == TokenType.REDIRECT_ALL
                || type == TokenType.REDIRECT_ALL_APPEND;
    }

    private Redirection.Type mapRedirectionType(TokenType type) {
        return switch (type) {
            case REDIRECT_IN -> Redirection.Type.INPUT;
            case REDIRECT_OUT -> Redirection.Type.OUTPUT_OVERWRITE;
            case REDIRECT_APPEND -> Redirection.Type.OUTPUT_APPEND;
            case REDIRECT_ERR -> Redirection.Type.ERROR_OVERWRITE;
            case REDIRECT_ERR_APPEND -> Redirection.Type.ERROR_APPEND;
            case REDIRECT_ALL -> Redirection.Type.ALL_OVERWRITE;
            case REDIRECT_ALL_APPEND -> Redirection.Type.ALL_APPEND;
            default -> throw new IllegalArgumentException("Unknown redirection token: " + type);
        };
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private Token advance() {
        if (!isAtEnd()) cursor++;
        return tokens.get(cursor - 1);
    }

    private boolean isAtEnd() {
        return cursor >= tokens.size() || tokens.get(cursor).getType() == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(cursor);
    }
}
