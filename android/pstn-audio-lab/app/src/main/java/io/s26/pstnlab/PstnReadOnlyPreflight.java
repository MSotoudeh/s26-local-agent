package io.s26.pstnlab;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.IBinder;
import android.os.Process;
import android.telecom.TelecomManager;
import org.json.JSONObject;

/**
 * Read-only capability probe, intentionally unable to instantiate AudioRecord
 * or AudioTrack, start a call, touch audio routes or change AppOps.
 *
 * preflight_ok means "prerequisites observed", NOT live PSTN audio works.
 */
public final class PstnReadOnlyPreflight {
    private PstnReadOnlyPreflight() {}

    private static boolean methodPresent(Class<?> klass, String method, Class<?> parameter) {
        try { klass.getMethod(method, parameter); return true; }
        catch (Throwable e) { return false; }
    }

    private static boolean has(Context context, String permission) {
        try {
            return context != null &&
                context.checkPermission(permission, Process.myPid(), Process.myUid())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) { return false; }
    }

    /** Never mutate live audio routing or call state from this method. */
    public static String inspect(Context context) {
        JSONObject result = new JSONObject();
        try {
            boolean shell = Process.myUid() == 2000;
            boolean contextPresent = context != null;
            boolean canIntercept = has(context, "android.permission.CALL_AUDIO_INTERCEPTION");
            boolean capture = has(context, "android.permission.CAPTURE_AUDIO_OUTPUT");
            boolean modifyPhone = has(context, "android.permission.MODIFY_PHONE_STATE");
            boolean recordAudio = has(context, "android.permission.RECORD_AUDIO");
            boolean rx = methodPresent(AudioManager.class,
                "getCallDownlinkExtractionAudioRecord", AudioFormat.class);
            boolean tx = methodPresent(AudioManager.class,
                "getCallUplinkInjectionAudioTrack", AudioFormat.class);
            boolean rec = methodPresent(AudioRecord.Builder.class,
                "setCallRedirectionMode", int.class);
            boolean track = methodPresent(AudioTrack.Builder.class,
                "setCallRedirectionMode", int.class);
            Boolean interceptable = null;
            try {
                Class<?> serviceManager = Class.forName("android.os.ServiceManager");
                IBinder binder = (IBinder) serviceManager
                    .getMethod("getService", String.class).invoke(null, "audio");
                if (binder != null) {
                    Class<?> stub = Class.forName("android.media.IAudioService$Stub");
                    Object service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
                    Object value = Class.forName("android.media.IAudioService")
                        .getMethod("isPstnCallAudioInterceptable").invoke(service);
                    if (value instanceof Boolean) interceptable = (Boolean) value;
                }
            } catch (Throwable ignored) { }

            Boolean inCall = null;
            Integer audioMode = null;
            String telecomError = null;
            String audioError = null;
            try {
                if (contextPresent) {
                    TelecomManager telecom = context.getSystemService(TelecomManager.class);
                    if (telecom != null) inCall = telecom.isInCall();
                }
            } catch (Throwable t) { telecomError = t.getClass().getSimpleName(); }
            try {
                if (contextPresent) {
                    AudioManager audio = context.getSystemService(AudioManager.class);
                    if (audio != null) audioMode = audio.getMode();
                }
            } catch (Throwable t) { audioError = t.getClass().getSimpleName(); }

            result.put("scope", "READ_ONLY_NO_CALL_NO_AUDIO");
            result.put("uid", Process.myUid());
            result.put("shell", shell);
            result.put("context_available", contextPresent);
            result.put("call_audio_interception_granted", canIntercept);
            result.put("capture_audio_output_granted", capture);
            result.put("modify_phone_state_granted", modifyPhone);
            result.put("record_audio_granted", recordAudio);
            result.put("redirect_helper_downlink", rx);
            result.put("redirect_helper_uplink", tx);
            result.put("record_redirection_builder", rec);
            result.put("track_redirection_builder", track);
            result.put("pstn_interceptable", interceptable == null ? JSONObject.NULL : interceptable);
            result.put("in_call", inCall == null ? JSONObject.NULL : inCall);
            result.put("audio_mode", audioMode == null ? JSONObject.NULL : audioMode);
            result.put("telecom_query_error", telecomError == null ? JSONObject.NULL : telecomError);
            result.put("audio_query_error", audioError == null ? JSONObject.NULL : audioError);
            result.put("audio_record_instances_opened", 0);
            result.put("audio_track_instances_opened", 0);
            result.put("real_audio_enabled", false);
            result.put("prerequisite_gate", shell && contextPresent && canIntercept && modifyPhone
                && rx && tx && rec && track && Boolean.TRUE.equals(interceptable));
            result.put("live_capture_or_injection_verified", false);
        } catch (Throwable e) {
            try {
                result.put("scope", "READ_ONLY_NO_CALL_NO_AUDIO");
                result.put("prerequisite_gate", false);
                result.put("error_type", e.getClass().getSimpleName());
            } catch (Exception ignored) {}
        }
        return result.toString();
    }
}
