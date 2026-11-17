
import java.util.List;


public class ParserSmokeTest {

    public static void main(String[] args) {
        
        check(Parser.parse("echo hello").get(0).equals(List.of("echo", "hello")), "simple command");

        
        check(Parser.parse("echo 'hello world'").get(0).equals(List.of("echo", "hello world")), "single quote");

        
        check(Parser.parse("cat x | grep y").size() == 2, "pipeline splitting");

        
        check(Parser.parse("echo hi > out").get(0).equals(List.of("echo", "hi", ">", "out")), "redirect output");

        
        check(Parser.parse("echo hi >> out").get(0).contains(">>"), "append output redirection");

        
        check(Parser.parse("cat < in").get(0).contains("<"), "input redirection");

        
        check(Parser.parse("echo hi 2> err").get(0).contains("2>"), "stderr redirection");

        
        try {
            Parser.parse("echo 'bad");
            throw new AssertionError("Expected IllegalArgumentException for unclosed quote");
        } catch (IllegalArgumentException expected) {
            
        }

        
        CommandLine cmd = Parser.parseCommandLine("cat file.txt | grep search > results.txt");
        check(cmd != null, "command line non-null");
        check(cmd.getStages().size() == 2, "two pipeline stages");
        check(cmd.getStages().get(0).getCommandName().equals("cat"), "first stage command");
        check(cmd.getStages().get(1).getCommandName().equals("grep"), "second stage command");
        check(!cmd.getStages().get(1).getRedirections().isEmpty(), "second stage has redirection");

        System.out.println("All OM Shell parser checks passed successfully.");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + description);
        }
    }
}
