import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;





public class V3ModularEngineTest {

    public static void main(String[] args) {
        System.out.println("Running OM Shell Ultimate 3.0 Master Verification Suite...");

        Shell shell = new Shell();
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        shell.setStandardOutput(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        shell.setStandardError(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));

        
        check(Main.VERSION.contains("3.0.0"), "Main version contains 3.0.0");

        
        check(Expander.expandVariables("prefix-$'A\\tB'-suffix", shell).contains("A\tB"), "ANSI-C quoting expansion");

        
        capturedOut.reset();
        shell.executeLine("cat <<< 'here-string content'");
        check(capturedOut.toString(StandardCharsets.UTF_8).contains("here-string content"), "here-string <<< redirection");

        
        shell.executeLine("numbers=([2]=two [5]=five [10]=ten)");
        check("two".equals(shell.getIndexedArrayValue("numbers", 2)), "sparse indexed array at 2");
        check("five".equals(shell.getIndexedArrayValue("numbers", 5)), "sparse indexed array at 5");
        check("ten".equals(shell.getIndexedArrayValue("numbers", 10)), "sparse indexed array at 10");
        check("3".equals(Expander.expandVariables("${#numbers[@]}", shell)), "sparse indexed array size");

        
        shell.executeLine("declare -A servers=([web]=nginx [db]=postgres [cache]=redis)");
        check("nginx".equals(shell.getAssociativeArrayValue("servers", "web")), "assoc array lookup web");
        check("postgres".equals(shell.getAssociativeArrayValue("servers", "db")), "assoc array lookup db");
        check("redis".equals(shell.getAssociativeArrayValue("servers", "cache")), "assoc array lookup cache");
        check("3".equals(Expander.expandVariables("${#servers[@]}", shell)), "assoc array length");

        
        shell.setVariable("RAW_URL", "https://api.github.com/repos/google/omsh", false);
        check("https:".equals(Expander.expandVariables("${RAW_URL%%//*}", shell)), "longest suffix removal %%");
        check("omsh".equals(Expander.expandVariables("${RAW_URL##*/}", shell)), "longest prefix removal ##");
        check("HTTPS://API.GITHUB.COM/REPOS/GOOGLE/OMSH".equals(Expander.expandVariables("${RAW_URL^^}", shell)), "uppercase ^^");
        shell.setVariable("VAR_REF", "RAW_URL", false);
        check("omsh".equals(Expander.expandVariables("${!VAR_REF##*/}", shell)) ||
              shell.getVariable("RAW_URL").equals(Expander.expandVariables("${!VAR_REF}", shell)), "indirect variable expansion");

        
        check(shell.executeLine("[[ 'package-3.0.0.jar' == *.jar ]]") == 0, "[[ == *.jar ]] glob check");
        check(shell.executeLine("[[ 'release_v3.0' =~ ^release_v[0-9]\\.[0-9]$ ]]") == 0, "[[ =~ regex ]] check");

        
        shell.executeLine("(( total = 10 * 5 + 2 ))");
        check("52".equals(shell.getVariable("total")), "(( total = 10 * 5 + 2 ))");
        check(shell.executeLine("(( total > 50 ))") == 0, "(( total > 50 )) comparison");

        shell.executeLine("let 'flag = total % 2 == 0'");
        check("1".equals(shell.getVariable("flag")), "let assignment with boolean comparison");

        
        shell.setVariable("LEVEL", "root", false);
        shell.executeLine("( LEVEL='subshell' )");
        check("root".equals(shell.getVariable("LEVEL")), "subshell variable isolation");
        shell.executeLine("{ LEVEL='grouped'; }");
        check("grouped".equals(shell.getVariable("LEVEL")), "group modifies current shell");

        
        capturedOut.reset();
        String caseCode = """
                target="beta"
                case "$target" in
                    alpha)
                        echo "Target is Alpha"
                        ;;
                    beta|gamma)
                        echo "Target is Beta or Gamma"
                        ;;
                    *)
                        echo "Target is Unknown"
                        ;;
                esac
                """;
        shell.executeLine(caseCode);
        check(capturedOut.toString(StandardCharsets.UTF_8).contains("Target is Beta or Gamma"), "case statement branch execution");

        
        shell.executeLine("set -e -u -x -o pipefail");
        check(shell.isOptionEnabled("errexit"), "errexit enabled");
        check(shell.isOptionEnabled("nounset"), "nounset enabled");
        check(shell.isOptionEnabled("xtrace"), "xtrace enabled");
        check(shell.isOptionEnabled("pipefail"), "pipefail enabled");
        shell.executeLine("set +e +u +x +o pipefail");
        check(!shell.isOptionEnabled("errexit"), "errexit disabled");

        
        shell.setStandardInput(new ByteArrayInputStream("foo bar baz qux\n".getBytes(StandardCharsets.UTF_8)));
        shell.executeLine("read first second rest");
        check("foo".equals(shell.getVariable("first")), "read first var");
        check("bar".equals(shell.getVariable("second")), "read second var");
        check("baz qux".equals(shell.getVariable("rest")), "read rest of line");

        
        shell.executeLine("trap 'echo trapped_signal' USR2");
        check("echo trapped_signal".equals(shell.getTrap("USR2")), "trap registered for USR2");
        shell.executeLine("trap - USR2");
        check(shell.getTrap("USR2") == null, "trap reset for USR2");

        System.out.println("V3ModularEngineTest: ALL CHECKS PASSED SUCCESSFULLY (100%)!");
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("V3ModularEngineTest FAILED: " + description);
        }
    }
}
