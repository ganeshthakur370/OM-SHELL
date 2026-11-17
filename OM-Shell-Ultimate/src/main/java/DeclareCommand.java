import java.io.InputStream;

import java.io.OutputStream;

import java.io.PrintStream;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;

import java.util.List;

import java.util.Map;

import java.util.TreeMap;



public class DeclareCommand implements BuiltinCommand {



    @Override

    public int execute(List<String> args, InputStream stdin, OutputStream stdout,

                       OutputStream stderr, Shell shell) {

        PrintStream output = new PrintStream(stdout, true, StandardCharsets.UTF_8);

        PrintStream error = new PrintStream(stderr, true, StandardCharsets.UTF_8);



        boolean indexed = false;

        boolean associative = false;

        boolean integer = false;

        boolean readonly = false;

        boolean export = false;

        boolean print = false;



        List<String> rawTokens = new ArrayList<>();

        int i = 0;



        while (i < args.size()) {

            String arg = args.get(i);

            if (arg.equals("--")) {

                i++;

                while (i < args.size()) {

                    rawTokens.add(args.get(i++));

                }

                break;

            }

            if (arg.startsWith("-") && arg.length() > 1 && !arg.contains("=")) {

                boolean isOption = true;

                for (int j = 1; j < arg.length(); j++) {

                    char c = arg.charAt(j);

                    switch (c) {

                        case 'a' -> indexed = true;

                        case 'A' -> associative = true;

                        case 'i' -> integer = true;

                        case 'r' -> readonly = true;

                        case 'x' -> export = true;

                        case 'p' -> print = true;

                        default -> isOption = false;

                    }

                }

                if (isOption) {

                    i++;

                    continue;

                }

            }

            rawTokens.add(arg);

            i++;

        }



        List<String> operands = new ArrayList<>();

        int idx = 0;

        while (idx < rawTokens.size()) {

            String tok = rawTokens.get(idx);

            int parenIdx = tok.indexOf("=(");

            if (parenIdx >= 0 && !tok.endsWith(")")) {

                StringBuilder combined = new StringBuilder(tok);

                idx++;

                while (idx < rawTokens.size()) {

                    String nextTok = rawTokens.get(idx++);

                    combined.append(" ").append(nextTok);

                    if (nextTok.endsWith(")")) {

                        break;

                    }

                }

                operands.add(combined.toString());

            } else {

                operands.add(tok);

                idx++;

            }

        }



        if (operands.isEmpty() || (print && operands.isEmpty())) {

            for (Map.Entry<String, TreeMap<Integer, String>> e : shell.getIndexedArrays().entrySet()) {

                StringBuilder sb = new StringBuilder();

                sb.append("declare -a ").append(e.getKey()).append("=(");

                boolean first = true;

                for (Map.Entry<Integer, String> elem : e.getValue().entrySet()) {

                    if (!first) sb.append(" ");

                    sb.append("[").append(elem.getKey()).append("]=\"").append(escape(elem.getValue())).append("\"");

                    first = false;

                }

                sb.append(")");

                output.println(sb);

            }

            for (Map.Entry<String, java.util.LinkedHashMap<String, String>> e : shell.getAssociativeArrays().entrySet()) {

                StringBuilder sb = new StringBuilder();

                sb.append("declare -A ").append(e.getKey()).append("=(");

                boolean first = true;

                for (Map.Entry<String, String> elem : e.getValue().entrySet()) {

                    if (!first) sb.append(" ");

                    sb.append("[").append(elem.getKey()).append("]=\"").append(escape(elem.getValue())).append("\"");

                    first = false;

                }

                sb.append(")");

                output.println(sb);

            }

            for (Map.Entry<String, String> e : shell.getVariables().entrySet()) {

                String k = e.getKey();

                String v = e.getValue();

                StringBuilder flags = new StringBuilder();

                if (shell.isReadonly(k)) flags.append("r");

                if (shell.isInteger(k)) flags.append("i");

                if (shell.isExported(k)) flags.append("x");

                String flagStr = flags.length() == 0 ? "--" : "-" + flags;

                output.println("declare " + flagStr + " " + k + "=\"" + escape(v) + "\"");

            }

            return 0;

        }



        if (print) {

            for (String varName : operands) {

                if (shell.isIndexedArray(varName)) {

                    StringBuilder sb = new StringBuilder();

                    sb.append("declare -a ").append(varName).append("=(");

                    boolean first = true;

                    for (Map.Entry<Integer, String> elem : shell.getIndexedArray(varName).entrySet()) {

                        if (!first) sb.append(" ");

                        sb.append("[").append(elem.getKey()).append("]=\"").append(escape(elem.getValue())).append("\"");

                        first = false;

                    }

                    sb.append(")");

                    output.println(sb);

                } else if (shell.isAssociativeArray(varName)) {

                    StringBuilder sb = new StringBuilder();

                    sb.append("declare -A ").append(varName).append("=(");

                    boolean first = true;

                    for (Map.Entry<String, String> elem : shell.getAssociativeArray(varName).entrySet()) {

                        if (!first) sb.append(" ");

                        sb.append("[").append(elem.getKey()).append("]=\"").append(escape(elem.getValue())).append("\"");

                        first = false;

                    }

                    sb.append(")");

                    output.println(sb);

                } else if (shell.hasVariable(varName)) {

                    String v = shell.getVariable(varName);

                    StringBuilder flags = new StringBuilder();

                    if (shell.isReadonly(varName)) flags.append("r");

                    if (shell.isInteger(varName)) flags.append("i");

                    if (shell.isExported(varName)) flags.append("x");

                    String flagStr = flags.length() == 0 ? "--" : "-" + flags;

                    output.println("declare " + flagStr + " " + varName + "=\"" + escape(v) + "\"");

                } else {

                    error.println("omsh: declare: " + varName + ": not found");

                    return 1;

                }

            }

            return 0;

        }



        for (String op : operands) {

            int eq = op.indexOf('=');

            boolean append = false;

            String name;

            String value = null;

            if (eq >= 0) {

                if (eq > 0 && op.charAt(eq - 1) == '+') {

                    append = true;

                    name = op.substring(0, eq - 1);

                } else {

                    name = op.substring(0, eq);

                }

                value = op.substring(eq + 1);

            } else {

                name = op;

            }



            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {

                error.println("omsh: declare: `" + name + "': not a valid identifier");

                return 1;

            }



            if (indexed) shell.ensureIndexedArray(name);

            if (associative) shell.ensureAssociativeArray(name);

            if (integer) shell.setIntegerAttribute(name);

            if (readonly) shell.setReadonly(name);

            if (export) shell.setExported(name);



            if (value != null) {

                if (value.startsWith("(") && value.endsWith(")")) {

                    String inner = value.substring(1, value.length() - 1).trim();

                    shell.handleCompoundArrayAssignment(name, inner, append);

                } else {

                    if (shell.isAssociativeArray(name)) {

                        shell.setAssociativeArrayValue(name, "0", value);

                    } else if (shell.isIndexedArray(name)) {

                        shell.setIndexedArrayValue(name, 0, value);

                    } else {

                        shell.setVariable(name, value, export);

                    }

                }

            } else {

                if (indexed) shell.ensureIndexedArray(name);

                if (associative) shell.ensureAssociativeArray(name);

            }

        }

        return 0;

    }



    private String escape(String s) {

        if (s == null) return "";

        return s.replace("\\", "\\\\").replace("\"", "\\\"");

    }



    @Override public String getName() { return "declare"; }

    @Override public String getDescription() { return "Declare or display shell variables and arrays"; }

    @Override public String getUsage() { return "declare [-aAfFiprx] [name[=value] ...]"; }

}
