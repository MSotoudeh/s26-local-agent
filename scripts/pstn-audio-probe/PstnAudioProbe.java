public class PstnAudioProbe {
    public static void main(String[] args) {
        System.out.println("PROBE_SCOPE=read_only_no_call_no_audio");
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            Class<?> binderType = Class.forName("android.os.IBinder");
            Object binder = manager.getMethod("getService", String.class).invoke(null, "audio");
            if (binder == null) {
                System.out.println("AUDIO_SERVICE=unavailable");
                return;
            }
            Class<?> stub = Class.forName("android.media.IAudioService$Stub");
            Object audio = stub.getMethod("asInterface", binderType).invoke(null, binder);
            System.out.println("AUDIO_SERVICE=connected");
            Class<?> iface = Class.forName("android.media.IAudioService");
            java.lang.reflect.Method found = null;
            for (java.lang.reflect.Method method : iface.getMethods()) {
                if ("isPstnCallAudioInterceptable".equals(method.getName())) {
                    found = method;
                    break;
                }
            }
            if (found == null) {
                System.out.println("INTERCEPTABLE_API=not_exposed");
                return;
            }
            System.out.println("INTERCEPTABLE_API=present parameters=" + found.getParameterCount());
            if (found.getParameterCount() == 0) {
                System.out.println("PSTN_CALL_AUDIO_INTERCEPTABLE=" + found.invoke(audio));
            } else {
                System.out.println("PSTN_CALL_AUDIO_INTERCEPTABLE=unknown_method_parameters");
            }
        } catch (Throwable error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            System.out.println("PROBE_ERROR_TYPE=" + cause.getClass().getName());
        }
    }
}
