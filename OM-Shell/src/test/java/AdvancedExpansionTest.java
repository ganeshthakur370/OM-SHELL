




public class AdvancedExpansionTest {

    public static void main(String[] args) {
        Shell shell = new Shell();

        
        check(Expander.expandVariables("${UNSET_VAR:-fallback}", shell).equals("fallback"), "unset var with :- default");
        shell.setVariable("SET_VAR", "original", false);
        check(Expander.expandVariables("${SET_VAR:-fallback}", shell).equals("original"), "set var with :- default");

        check(Expander.expandVariables("${ASSIGN_VAR:=assigned_value}", shell).equals("assigned_value"), "var := assign default");
        check("assigned_value".equals(shell.getVariable("ASSIGN_VAR")), "var := assigns to shell environment");

        
        check(Expander.expandVariables("${UNSET_VAR2:+alternate}", shell).isEmpty(), "unset var with :+ alternate");
        shell.setVariable("NON_EMPTY", "value", false);
        check(Expander.expandVariables("${NON_EMPTY:+alternate}", shell).equals("alternate"), "set var with :+ alternate");

        
        shell.setVariable("STRING", "0123456789abcdef", false);
        check(Expander.expandVariables("${STRING:0:4}", shell).equals("0123"), "substring offset 0 len 4");
        check(Expander.expandVariables("${STRING:7:3}", shell).equals("789"), "substring offset 7 len 3");
        check(Expander.expandVariables("${STRING:10}", shell).equals("abcdef"), "substring offset 10 to end");
        check(Expander.expandVariables("${STRING: -4:2}", shell).equals("cd"), "negative offset substring");

        
        shell.setVariable("FILE_PATH", "/home/user/docs/report.txt", false);
        check(Expander.expandVariables("${FILE_PATH#*/}", shell).equals("home/user/docs/report.txt"), "shortest prefix removal");
        check(Expander.expandVariables("${FILE_PATH##*/}", shell).equals("report.txt"), "longest prefix removal (basename)");

        
        check(Expander.expandVariables("${FILE_PATH%/*}", shell).equals("/home/user/docs"), "shortest suffix removal (dirname)");
        shell.setVariable("ARCHIVE", "archive.tar.gz", false);
        check(Expander.expandVariables("${ARCHIVE%.*}", shell).equals("archive.tar"), "shortest suffix extension removal");
        check(Expander.expandVariables("${ARCHIVE%%.*}", shell).equals("archive"), "longest suffix extension removal");

        
        shell.setVariable("MESSAGE", "banana apple banana orange", false);
        check(Expander.expandVariables("${MESSAGE/banana/cherry}", shell).equals("cherry apple banana orange"), "first pattern replacement");
        check(Expander.expandVariables("${MESSAGE//banana/cherry}", shell).equals("cherry apple cherry orange"), "all patterns replacement");
        check(Expander.expandVariables("${MESSAGE/#banana/fruit}", shell).equals("fruit apple banana orange"), "prefix anchor replacement");
        check(Expander.expandVariables("${MESSAGE/%orange/citrus}", shell).equals("banana apple banana citrus"), "suffix anchor replacement");

        
        shell.setVariable("TITLE", "hello world", false);
        check(Expander.expandVariables("${TITLE^}", shell).equals("Hello world"), "uppercase first character");
        check(Expander.expandVariables("${TITLE^^}", shell).equals("HELLO WORLD"), "uppercase all characters");
        shell.setVariable("SHOUT", "LOUD NOISE", false);
        check(Expander.expandVariables("${SHOUT,}", shell).equals("lOUD NOISE"), "lowercase first character");
        check(Expander.expandVariables("${SHOUT,,}", shell).equals("loud noise"), "lowercase all characters");

        
        shell.setVariable("TARGET", "destination_value", false);
        shell.setVariable("POINTER", "TARGET", false);
        check(Expander.expandVariables("${!POINTER}", shell).equals("destination_value"), "indirect parameter expansion");

        
        shell.executeLine("items=(zero one two three four five)");
        check(Expander.expandVariables("${#items[@]}", shell).equals("6"), "array length ${#items[@]}");
        check(Expander.expandVariables("${items[@]:1:3}", shell).equals("one two three"), "array slice ${items[@]:1:3}");
        check(Expander.expandVariables("${items[@]:4}", shell).equals("four five"), "array slice ${items[@]:4}");

        System.out.println("AdvancedExpansionTest: all checks passed successfully!");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("AdvancedExpansionTest FAILED: " + description);
        }
    }
}
