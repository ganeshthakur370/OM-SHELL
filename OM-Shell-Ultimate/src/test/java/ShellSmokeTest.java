
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * End-to-end smoke test suite verifying OM Shell's core functionality in pure Java.
 */
public class ShellSmokeTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Running OM Shell Pure Java smoke tests...");
        Shell shell = new Shell();

        Path tempDir = Files.createTempDirectory("omsh_test_");
        shell.setWorkingDirectory(tempDir);

        try {
            // 1. Alias test
            shell.executeLine("alias sayhello='echo hello from alias'");
            String aliasOutput = shell.executeSubshell("sayhello").trim();
            check(aliasOutput.equals("hello from alias"), "alias expansion: got '" + aliasOutput + "'");
            pass("aliases");

            // 2. User-defined variables
            shell.executeLine("USER_NAME=\"OM Shell\"");
            check("OM Shell".equals(shell.getVariable("USER_NAME")), "variable assignment");
            String varOutput = shell.executeSubshell("echo $USER_NAME").trim();
            check(varOutput.equals("OM Shell"), "variable expansion: got '" + varOutput + "'");
            pass("user-defined variables");

            // 3. Command substitution
            String subOutput = shell.executeSubshell("printf 'command substitution works'").trim();
            check(subOutput.equals("command substitution works"), "command substitution");
            pass("command substitution");

            // 4. If / else control flow
            shell.executeLine("if [ -d \"" + tempDir.toString().replace("\\", "/") + "\" ]; then IF_RESULT=\"if works\"; else IF_RESULT=\"failed\"; fi");
            check("if works".equals(shell.getVariable("IF_RESULT")), "if / else conditional");
            pass("if / else");

            // 5. For loop
            shell.executeLine("FOR_RESULT=\"\"");
            shell.executeLine("for value in alpha beta gamma; do FOR_RESULT=\"${FOR_RESULT}${value} \"; done");
            check("alpha beta gamma ".equals(shell.getVariable("FOR_RESULT")), "for loop: got '" + shell.getVariable("FOR_RESULT") + "'");
            pass("for loop");

            // 6. While loop & arithmetic
            shell.executeLine("COUNTER=0");
            shell.executeLine("while [ \"$COUNTER\" -lt 3 ]; do COUNTER=$((COUNTER + 1)); done");
            check("3".equals(shell.getVariable("COUNTER")), "while loop & arithmetic: got '" + shell.getVariable("COUNTER") + "'");
            pass("while loop");

            // 7. Pipelines
            String pipeOutput = shell.executeSubshell("printf 'pear\\napple\\nplum\\n' | grep apple | wc -l | tr -d ' '").trim();
            check("1".equals(pipeOutput), "pipeline: got '" + pipeOutput + "'");
            pass("pipelines");

            // 8. Output redirection
            shell.executeLine("printf 'redirect test\\n' > output.txt");
            Path outputFile = tempDir.resolve("output.txt");
            check(Files.exists(outputFile), "redirect file creation");
            String fileContent = Files.readString(outputFile).trim();
            check("redirect test".equals(fileContent), "redirect content: got '" + fileContent + "'");
            pass("redirection");

            // 9. Exit status
            shell.executeLine("false");
            check(shell.getLastExitCode() == 1, "false exit status: got " + shell.getLastExitCode());
            shell.executeLine("true");
            check(shell.getLastExitCode() == 0, "true exit status: got " + shell.getLastExitCode());
            pass("exit status");

            // 10. Command not found status
            int missingStatus = shell.executeLine("nonexistent-command-xyz-123 >/dev/null 2>&1");
            check(missingStatus == 127, "command not found status: got " + missingStatus);
            pass("error handling and command-not-found status");

            // 11. Background process and wait
            shell.executeLine("sleep 0.1 &");
            long bgPid = shell.getLastBackgroundPid();
            check(bgPid > 0, "background job pid");
            int waitStatus = shell.executeLine("wait " + bgPid);
            check(waitStatus == 0, "wait status: got " + waitStatus);
            pass("background process and wait");

            System.out.println();
            System.out.println("All OM Shell Pure Java smoke tests passed successfully!");
        } finally {
            // Cleanup temp dir
            try {
                try (var walk = Files.walk(tempDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void pass(String name) {
        System.out.println("PASS: " + name);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
