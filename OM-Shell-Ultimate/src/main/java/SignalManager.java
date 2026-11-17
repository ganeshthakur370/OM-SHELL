
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Connects a small, documented set of operating-system signals to shell traps.
 * The JDK does not expose a portable public signal API, so this class uses the
 * JDK's optional sun.misc.Signal bridge through reflection. Unsupported systems
 * simply report that native signal registration is unavailable.
 */
public class SignalManager {

    private static final String[] SUPPORTED_SIGNALS = {
            "INT", "TERM", "HUP", "QUIT", "USR1", "USR2", "ALRM", "PIPE"
    };

    private final Shell shell;
    private final Map<String, Object> registeredSignals = new LinkedHashMap<>();
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

            for (String signalName : SUPPORTED_SIGNALS) {
                try {
                    Object signal = constructor.newInstance(signalName);
                    Object handler = Proxy.newProxyInstance(
                            handlerClass.getClassLoader(),
                            new Class<?>[]{handlerClass},
                            (proxy, method, arguments) -> {
                                if (method.getName().equals("handle") && arguments != null && arguments.length == 1) {
                                    Object receivedSignal = arguments[0];
                                    String receivedName = String.valueOf(getNameMethod.invoke(receivedSignal));
                                    signalExecutor.execute(() -> shell.handleSignal(receivedName));
                                }
                                return null;
                            });
                    Object previousHandler = handleMethod.invoke(null, signal, handler);
                    registeredSignals.put(signalName, previousHandler);
                    available = true;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // This signal may not exist on the current operating system.
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean supports(String signalName) {
        return registeredSignals.containsKey(signalName.toUpperCase());
    }

    public void restorePreviousHandler(String signalName) {
        Object previousHandler = registeredSignals.get(signalName.toUpperCase());
        if (previousHandler == null) return;
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
            Object signal = signalClass.getConstructor(String.class).newInstance(signalName.toUpperCase());
            signalClass.getMethod("handle", signalClass, handlerClass).invoke(null, signal, previousHandler);
            registeredSignals.remove(signalName.toUpperCase());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The platform does not allow restoring this signal handler.
        }
    }

    public void close() {
        signalExecutor.shutdownNow();
    }
}
