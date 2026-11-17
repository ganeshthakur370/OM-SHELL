
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;


public class ArrayAndSignalSmokeTest {

    public static void main(String[] args) {
        Shell shell = new Shell();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        shell.setStandardOutput(new PrintStream(captured, true, StandardCharsets.UTF_8));

        shell.executeLine("colors=(red green blue)");
        check(shell.getIndexedArrayValue("colors", 1).equals("green"), "indexed array lookup");
        shell.executeLine("colors[1]=purple");
        check(shell.getIndexedArrayValue("colors", 1).equals("purple"), "indexed array assignment");
        check(Expander.expandVariables("${#colors[@]}", shell).equals("3"), "indexed array length");
        check(Expander.expandVariables("${colors[@]}", shell).equals("red purple blue"), "indexed array expansion");
        shell.executeLine("colors+=(gold silver)");
        check(shell.getIndexedArray("colors").size() == 5, "indexed array append");
        shell.executeLine("unset colors[1]");
        check(shell.getIndexedArrayValue("colors", 1).isEmpty(), "indexed array element unset");

        shell.executeLine("declare -A temperatures=([cold]=2 [warm]=8)");
        check(shell.getAssociativeArrayValue("temperatures", "warm").equals("8"), "associative array lookup");
        check(Expander.expandVariables("${!temperatures[@]}", shell).equals("cold warm"), "associative array keys");
        check(Expander.expandVariables("${#temperatures[@]}", shell).equals("2"), "associative array length");

        shell.registerTrap("USR1", "echo signal-received");
        shell.handleSignal("USR1");
        check(captured.toString(StandardCharsets.UTF_8).contains("signal-received"), "signal trap dispatch");

        if (shell.getSignalManager().supports("USR1")) {
            shell.registerTrap("USR1", "echo native-signal-received");
            try {
                Process signalSender = new ProcessBuilder("kill", "-USR1",
                        Long.toString(ProcessHandle.current().pid())).start();
                check(signalSender.waitFor() == 0, "send operating-system signal");
                long deadline = System.currentTimeMillis() + 1500;
                while (!captured.toString(StandardCharsets.UTF_8).contains("native-signal-received")
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }
                check(captured.toString(StandardCharsets.UTF_8).contains("native-signal-received"),
                        "operating-system signal reaches trap");
            } catch (Exception exception) {
                throw new AssertionError("Operating-system signal test failed", exception);
            }
        }

        System.out.println("ArrayAndSignalSmokeTest: all checks passed");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("Failed: " + name);
    }
}
