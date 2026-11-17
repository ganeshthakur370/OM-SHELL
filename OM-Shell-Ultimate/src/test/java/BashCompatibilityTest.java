import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
public class BashCompatibilityTest {

    public static void main(String[] args) {
        Shell shell = new Shell();
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        shell.setStandardOutput(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));

        
        int status1 = shell.executeLine("[[ 'hello.txt' == *.txt ]]");
        check(status1 == 0, "[[ == *.txt ]] glob match");

        int status2 = shell.executeLine("[[ 'hello.txt' != *.bin ]]");
        check(status2 == 0, "[[ != *.bin ]] glob mismatch");

        
        int status3 = shell.executeLine("[[ '2026-build' =~ ^[0-9]{4}-[a-z]+$ ]]");
        check(status3 == 0, "[[ =~ regex ]] match");

        int status4 = shell.executeLine("[[ 'abc' =~ ^[0-9]+$ ]]");
        check(status4 != 0, "[[ =~ regex ]] non-match fails");

        
        int status5 = shell.executeLine("(( count = 5 + 3 ))");
        check(status5 == 0, "(( count = 5 + 3 )) succeeds");
        check("8".equals(shell.getVariable("count")), "(( count = 5 + 3 )) sets variable");

        int status6 = shell.executeLine("(( count == 8 ))");
        check(status6 == 0, "(( count == 8 )) condition true returns 0");

        int status7 = shell.executeLine("(( count == 99 ))");
        check(status7 == 1, "(( count == 99 )) condition false returns 1");

        int status8 = shell.executeLine("(( 0 ))");
        check(status8 == 1, "(( 0 )) returns 1 (false)");

        
        int status9 = shell.executeLine("let 'x = 10 * 2' 'y = x + 5'");
        check(status9 == 0, "let succeeds");
        check("20".equals(shell.getVariable("x")), "let computes x");
        check("25".equals(shell.getVariable("y")), "let computes y");

        
        shell.executeLine("eval 'msg=activated'");
        check("activated".equals(shell.getVariable("msg")), "eval sets variable");

        
        shell.setPositionalParameters(java.util.List.of("alpha", "beta", "gamma", "delta"));
        check("alpha".equals(shell.getPositionalParameter(1)), "positional param $1 before shift");
        shell.executeLine("shift 2");
        check("gamma".equals(shell.getPositionalParameter(1)), "positional param $1 after shift 2");
        check(shell.getPositionalParameters().size() == 2, "positional count after shift");

        
        String inputData = "red green blue purple\n";
        shell.setStandardInput(new ByteArrayInputStream(inputData.getBytes(StandardCharsets.UTF_8)));
        shell.executeLine("read -a colors");
        check("green".equals(shell.getIndexedArrayValue("colors", 1)), "read -a indexed array");

        String inputVars = "first_word second_word third_word and rest\n";
        shell.setStandardInput(new ByteArrayInputStream(inputVars.getBytes(StandardCharsets.UTF_8)));
        shell.executeLine("read a b c");
        check("first_word".equals(shell.getVariable("a")), "read variable a");
        check("second_word".equals(shell.getVariable("b")), "read variable b");
        check("third_word and rest".equals(shell.getVariable("c")), "read remaining to c");

        
        capturedOut.reset();
        String caseScript = """
                animal="cat"
                case "$animal" in
                    dog)
                        echo "Dog found"
                        ;;
                    cat|kitten)
                        echo "Feline found"
                        ;;
                    *)
                        echo "Other animal"
                        ;;
                esac
                """;
        shell.executeLine(caseScript);
        check(capturedOut.toString(StandardCharsets.UTF_8).contains("Feline found"), "case ... esac branch matching");

        
        shell.executeLine("{ grouped_var='in_parent'; }");
        check("in_parent".equals(shell.getVariable("grouped_var")), "command group modifies parent environment");

        
        shell.setVariable("isolated_var", "parent_val", false);
        shell.executeLine("( isolated_var='subshell_val' )");
        check("parent_val".equals(shell.getVariable("isolated_var")), "subshell variable change is isolated");

        System.out.println("BashCompatibilityTest: all checks passed successfully!");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("BashCompatibilityTest FAILED: " + description);
        }
    }
}
