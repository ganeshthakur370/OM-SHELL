import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.ArrayList;

import java.util.List;



public class WcCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout, OutputStream stderr, Shell shell) {

        PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        boolean countLines = false;

        boolean countWords = false;

        boolean countBytes = false;

        List<String> files = new ArrayList<>();



        for (String arg : args) {

            if (arg.equals("-l") || arg.equals("--lines")) {

                countLines = true;

            } else if (arg.equals("-w") || arg.equals("--words")) {

                countWords = true;

            } else if (arg.equals("-c") || arg.equals("--bytes")) {

                countBytes = true;

            } else if (arg.startsWith("-")) {



                for (int i = 1; i < arg.length(); i++) {

                    char c = arg.charAt(i);

                    if (c == 'l') countLines = true;

                    else if (c == 'w') countWords = true;

                    else if (c == 'c') countBytes = true;

                }

            } else {

                files.add(arg);

            }

        }



        if (!countLines && !countWords && !countBytes) {

            countLines = true;

            countWords = true;

            countBytes = true;

        }



        if (files.isEmpty()) {

            if (stdin != null) {

                try {

                    byte[] data = stdin.readAllBytes();

                    printStats(data, countLines, countWords, countBytes, "", out);

                } catch (Exception e) {

                    err.println("omsh: wc: " + e.getMessage());

                    return 1;

                }

            }

            return 0;

        }



        int status = 0;

        long totalLines = 0, totalWords = 0, totalBytes = 0;



        for (String fileStr : files) {

            Path path = shell.resolvePath(fileStr);

            if (!Files.exists(path)) {

                err.println("omsh: wc: " + fileStr + ": No such file or directory");

                status = 1;

                continue;

            }

            try {

                byte[] data = Files.readAllBytes(path);

                long[] stats = computeStats(data);

                totalLines += stats[0];

                totalWords += stats[1];

                totalBytes += stats[2];

                printFormatted(stats[0], stats[1], stats[2], countLines, countWords, countBytes, fileStr, out);

            } catch (Exception e) {

                err.println("omsh: wc: " + fileStr + ": " + e.getMessage());

                status = 1;

            }

        }



        if (files.size() > 1) {

            printFormatted(totalLines, totalWords, totalBytes, countLines, countWords, countBytes, "total", out);

        }



        return status;

    }



    private void printStats(byte[] data, boolean lines, boolean words, boolean bytes, String label, PrintStream out) {

        long[] stats = computeStats(data);

        printFormatted(stats[0], stats[1], stats[2], lines, words, bytes, label, out);

    }



    private void printFormatted(long l, long w, long b, boolean countLines, boolean countWords, boolean countBytes, String label, PrintStream out) {

        StringBuilder sb = new StringBuilder();

        if (countLines) sb.append(l).append(" ");

        if (countWords) sb.append(w).append(" ");

        if (countBytes) sb.append(b).append(" ");

        if (!label.isEmpty()) sb.append(label);

        out.println(sb.toString().trim());

    }



    private long[] computeStats(byte[] data) {

        long lines = 0;

        long words = 0;

        long bytes = data.length;

        boolean inWord = false;



        for (byte b : data) {

            if (b == '\n') {

                lines++;

            }

            if (Character.isWhitespace((char) b)) {

                inWord = false;

            } else {

                if (!inWord) {

                    words++;

                    inWord = true;

                }

            }

        }



        return new long[]{lines, words, bytes};

    }



    @Override

    public String getName() {

        return "wc";

    }



    @Override

    public String getDescription() {

        return "Print newline, word, and byte counts";

    }



    @Override

    public String getUsage() {

        return "wc [-l] [-w] [-c] [FILE...]";

    }

}
