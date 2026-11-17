import java.io.File;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.List;

import java.util.Locale;

import java.util.Map;



public class ProcessLauncher {



    private static final boolean IS_WINDOWS = System.getProperty("os.name")

            .toLowerCase(Locale.ROOT)

            .contains("win");



    private static final String[] WINDOWS_EXTENSIONS = { "", ".exe", ".cmd", ".bat", ".com" };



    

    public static Path resolveExecutable(String commandName, Shell shell) {

        if (commandName == null || commandName.isEmpty()) {

            return null;

        }



        if (commandName.contains("/") || commandName.contains("\\")) {

            Path directPath = shell.resolvePath(commandName);

            Path resolved = checkExecutableVariants(directPath);

            if (resolved != null) {

                return resolved;

            }

        }



        String pathEnv = shell.getVariable("PATH");

        if (pathEnv == null) {

            pathEnv = System.getenv("PATH");

        }

        if (pathEnv == null) {

            pathEnv = "";

        }



        String[] directories = pathEnv.split(PatternQuote(File.pathSeparator));

        for (String dirStr : directories) {

            if (dirStr.isEmpty()) continue;

            Path dir = Path.of(dirStr);

            if (!Files.isDirectory(dir)) continue;



            Path target = dir.resolve(commandName);

            Path resolved = checkExecutableVariants(target);

            if (resolved != null) {

                return resolved;

            }

        }



        return null;

    }



    private static Path checkExecutableVariants(Path base) {

        if (IS_WINDOWS) {

            String fileName = base.getFileName().toString().toLowerCase(Locale.ROOT);

            for (String ext : WINDOWS_EXTENSIONS) {

                Path candidate = (ext.isEmpty() || fileName.endsWith(ext)) ? base : base.resolveSibling(base.getFileName() + ext);

                if (Files.isRegularFile(candidate)) {

                    return candidate;

                }

            }

        } else {

            if (Files.isRegularFile(base) && Files.isExecutable(base)) {

                return base;

            }

        }

        return null;

    }



    private static String PatternQuote(String s) {

        return java.util.regex.Pattern.quote(s);

    }



    

    public static ProcessBuilder createProcessBuilder(List<String> words, Shell shell) {

        ProcessBuilder pb = new ProcessBuilder(words);

        pb.directory(shell.getWorkingDirectory().toFile());



        Map<String, String> env = pb.environment();



        env.putAll(shell.getVariables());

        env.put("PWD", shell.getWorkingDirectory().toAbsolutePath().toString());

        env.put("OM_SHELL", "1");



        return pb;

    }

}
