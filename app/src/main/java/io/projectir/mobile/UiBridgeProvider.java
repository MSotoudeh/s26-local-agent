package io.projectir.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

import org.json.JSONException;
import org.json.JSONObject;

public final class UiBridgeProvider extends ContentProvider {
    private static final int SHELL_UID = 2000;
    private static final String RESULT = "result";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        enforceCaller();
        JSONObject result;
        try {
            String path = uri == null ? "" : uri.getLastPathSegment();
            UiAccessibilityService service = UiAccessibilityService.instance();
            if ("status".equals(path)) {
                result = status(service);
            } else if ("state".equals(path)) {
                result = service == null
                        ? unavailable("accessibility-not-connected")
                        : ok(service.snapshotJson());
            } else {
                result = unavailable("unsupported-query");
            }
        } catch (Exception e) {
            result = failure(e);
        }

        MatrixCursor cursor = new MatrixCursor(new String[]{RESULT});
        cursor.addRow(new Object[]{result.toString()});
        return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        Bundle out = new Bundle();
        UiAccessibilityService service = UiAccessibilityService.instance();
        JSONObject result;

        try {
            if ("status".equals(method)) {
                result = status(service);
            } else if (service == null) {
                result = unavailable("accessibility-not-connected");
            } else if ("state".equals(method)) {
                result = ok(service.snapshotJson());
            } else if ("click_text".equals(method)) {
                result = action(service.clickExactText(arg), "click_text");
            } else if ("click_resource".equals(method)) {
                result = action(service.clickResource(arg), "click_resource");
            } else if ("input_focused".equals(method)) {
                result = action(service.inputFocused(arg), "input_focused");
            } else if ("global".equals(method)) {
                int globalAction = Integer.parseInt(arg == null ? "0" : arg);
                result = action(service.performGlobalAction(globalAction), "global");
            } else {
                result = unavailable("unsupported-method");
            }
        } catch (Exception e) {
            result = failure(e);
        }

        out.putString(RESULT, result.toString());
        return out;
    }

    private JSONObject status(UiAccessibilityService service) {
        if (service == null) return unavailable("accessibility-not-connected");
        return ok(service.statusJson());
    }

    private JSONObject ok(JSONObject payload) {
        JSONObject out = new JSONObject();
        put(out, "ok", true);
        put(out, "result", payload);
        return out;
    }

    private JSONObject action(boolean success, String actionName) {
        JSONObject out = new JSONObject();
        put(out, "ok", success);
        put(out, "action", actionName);
        return out;
    }

    private JSONObject unavailable(String reason) {
        JSONObject out = new JSONObject();
        put(out, "ok", false);
        put(out, "error", reason);
        return out;
    }

    private JSONObject failure(Exception error) {
        JSONObject out = new JSONObject();
        put(out, "ok", false);
        put(out, "error", error.getClass().getSimpleName());
        put(out, "message", String.valueOf(error.getMessage()));
        return out;
    }

    private void enforceCaller() {
        int uid = Binder.getCallingUid();
        int own = Process.myUid();
        if (uid != own && uid != SHELL_UID) {
            throw new SecurityException("Caller uid " + uid + " is not allowed");
        }
    }

    private static void put(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException e) {
            throw new IllegalStateException("JSON encode failed for " + key, e);
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Use ContentProvider.call");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
