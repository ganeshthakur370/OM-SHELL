import java.lang.reflect.Constructor;

import java.lang.reflect.Method;

import java.lang.reflect.Proxy;

import java.util.*;

import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;



public class SignalManager {



    public static final Map<String, Integer> SIGNAL_NUMBERS = new LinkedHashMap<>();



    static {

        SIGNAL_NUMBERS.put("HUP", 1);

        SIGNAL_NUMBERS.put("INT", 2);

        SIGNAL_NUMBERS.put("QUIT", 3);

        SIGNAL_NUMBERS.put("ILL", 4);

        SIGNAL_NUMBERS.put("TRAP", 5);

        SIGNAL_NUMBERS.put("ABRT", 6);

        SIGNAL_NUMBERS.put("BUS", 7);

        SIGNAL_NUMBERS.put("FPE", 8);

        SIGNAL_NUMBERS.put("KILL", 9);

        SIGNAL_NUMBERS.put("USR1", 10);

        SIGNAL_NUMBERS.put("SEGV", 11);

        SIGNAL_NUMBERS.put("USR2", 12);

        SIGNAL_NUMBERS.put("PIPE", 13);

        SIGNAL_NUMBERS.put("ALRM", 14);

        SIGNAL_NUMBERS.put("TERM", 15);

        SIGNAL_NUMBERS.put("CHLD", 17);

        SIGNAL_NUMBERS.put("CONT", 18);

        SIGNAL_NUMBERS.put("STOP", 19);

        SIGNAL_NUMBERS.put("TSTP", 20);

        SIGNAL_NUMBERS.put("WINCH", 28);

    }



    private final Shell shell;

    private final Map<String, Object> registeredSignals = new LinkedHashMap<>();

    private final ConcurrentLinkedQueue<String> pendingSignals = new ConcurrentLinkedQueue<>();

    private final ExecutorService signalExecutor;

    private boolean available;



    public SignalManager(Shell shell) {

        this.shell = shell;

        this.signalExecutor = Executors.newSingleThreadExecutor(task -> {

            Thread thread = new Thread(task, "om-shell-signal-handler");

            thread.setDaemon(true);

            return thread;

        });

        registerOperatingSystemSignals();

    }



    private void registerOperatingSystemSignals() {

        try {

            Class<?> signalClass = Class.forName("sun.misc.Signal");

            Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");

            Constructor<?> constructor = signalClass.getConstructor(String.class);

            Method handleMethod = signalClass.getMethod("handle", signalClass, handlerClass);

            Method getNameMethod = signalClass.getMethod("getName");



            for (String signalName : SIGNAL_NUMBERS.keySet()) {

                if (signalName.equals("KILL") || signalName.equals("STOP")) {

                    continue;

                }

                try {

                    Object signal = constructor.newInstance(signalName);

                    Object handler = Proxy.newProxyInstance(

                            handlerClass.getClassLoader(),

                            new Class<?>[]{handlerClass},

                            (proxy, method, arguments) -> {

                                if (method.getName().equals("handle") && arguments != null && arguments.length == 1) {

                                    Object receivedSignal = arguments[0];

                                    String receivedName = String.valueOf(getNameMethod.invoke(receivedSignal));

                                    queueSignal(receivedName);

                                    signalExecutor.execute(() -> shell.handleSignal(receivedName));

                                }

                                return null;

                            });

                    Object previousHandler = handleMethod.invoke(null, signal, handler);

                    registeredSignals.put(signalName, previousHandler);

                    available = true;

                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {



                }

            }

        } catch (ReflectiveOperationException | LinkageError ignored) {

            available = false;

        }

    }



    public void queueSignal(String signalName) {

        pendingSignals.add(signalName.toUpperCase());

    }



    public List<String> drainPendingSignals() {

        List<String> drained = new ArrayList<>();

        String sig;

        while ((sig = pendingSignals.poll()) != null) {

            drained.add(sig);

        }

        return drained;

    }



    public boolean isAvailable() {

        return available;

    }



    public boolean supports(String signalName) {

        String upper = normalizeSignalName(signalName);

        return registeredSignals.containsKey(upper) || upper.equals("EXIT") || upper.equals("DEBUG") || upper.equals("ERR");

    }



    public static String normalizeSignalName(String sig) {

        if (sig == null) return "";

        String s = sig.toUpperCase().trim();

        if (s.startsWith("SIG")) {

            s = s.substring(3);

        }

        if (s.equals("0")) {

            return "EXIT";

        }

        return s;

    }



    public static List<String> getAvailableSignalNames() {

        return new ArrayList<>(SIGNAL_NUMBERS.keySet());

    }



    public void restorePreviousHandler(String signalName) {

        String name = normalizeSignalName(signalName);

        Object previousHandler = registeredSignals.get(name);

        if (previousHandler == null) return;

        try {

            Class<?> signalClass = Class.forName("sun.misc.Signal");

            Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");

            Object signal = signalClass.getConstructor(String.class).newInstance(name);

            signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, signal, previousHandler);

            registeredSignals.remove(name);

        } catch (ReflectiveOperationException | LinkageError ignored) {

        }

    }



    public void close() {

        signalExecutor.shutdownNow();

    }

}
