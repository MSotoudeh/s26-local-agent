package io.s26.pstnlab;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/** Explicit allowlist of fake-only operations exposed through Shizuku. */
public interface ILabService extends IInterface {
    String status() throws RemoteException;
    String preflight() throws RemoteException;
    String fakePcmTest() throws RemoteException;
    byte[] fakePcmFrame(byte[] frame) throws RemoteException;
    void destroy() throws RemoteException;

    abstract class Stub extends Binder implements ILabService {
        private static final String DESC = "io.s26.pstnlab.ILabService";
        private static final int STATUS = IBinder.FIRST_CALL_TRANSACTION;
        private static final int FAKE_PCM = IBinder.FIRST_CALL_TRANSACTION + 1;
        private static final int FAKE_FRAME = IBinder.FIRST_CALL_TRANSACTION + 2;
        private static final int PREFLIGHT = IBinder.FIRST_CALL_TRANSACTION + 3;
        // Shizuku UserService destroy transaction (reserved number).
        private static final int DESTROY = 16777115;
        protected Stub() { attachInterface(this, DESC); }
        public static ILabService asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface own = binder.queryLocalInterface(DESC);
            return own instanceof ILabService ? (ILabService) own : new Proxy(binder);
        }
        @Override public IBinder asBinder() { return this; }
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESC); return true; }
            if (code == STATUS) {
                data.enforceInterface(DESC);
                String result = status();
                reply.writeNoException(); reply.writeString(result); return true;
            }
            if (code == PREFLIGHT) {
                data.enforceInterface(DESC);
                String result = preflight();
                reply.writeNoException(); reply.writeString(result); return true;
            }
            if (code == FAKE_PCM) {
                data.enforceInterface(DESC);
                String result = fakePcmTest();
                reply.writeNoException(); reply.writeString(result); return true;
            }
            if (code == FAKE_FRAME) {
                data.enforceInterface(DESC);
                byte[] frame = data.createByteArray();
                byte[] result = fakePcmFrame(frame);
                reply.writeNoException(); reply.writeByteArray(result); return true;
            }
            if (code == DESTROY) {
                data.enforceInterface(DESC); destroy();
                if (reply != null) reply.writeNoException(); return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
        private static class Proxy implements ILabService {
            private final IBinder remote;
            Proxy(IBinder remote) { this.remote = remote; }
            @Override public IBinder asBinder() { return remote; }
            private String call(int code) throws RemoteException {
                Parcel in = Parcel.obtain(); Parcel out = Parcel.obtain();
                try {
                    in.writeInterfaceToken(DESC);
                    if (!remote.transact(code, in, out, 0)) throw new RemoteException("transact returned false");
                    out.readException(); return out.readString();
                } finally { in.recycle(); out.recycle(); }
            }
            @Override public String status() throws RemoteException { return call(STATUS); }
            @Override public String preflight() throws RemoteException { return call(PREFLIGHT); }
            @Override public String fakePcmTest() throws RemoteException { return call(FAKE_PCM); }
            @Override public byte[] fakePcmFrame(byte[] frame) throws RemoteException {
                Parcel in = Parcel.obtain(); Parcel out = Parcel.obtain();
                try {
                    in.writeInterfaceToken(DESC); in.writeByteArray(frame);
                    if (!remote.transact(FAKE_FRAME, in, out, 0)) throw new RemoteException("fake frame transact failed");
                    out.readException(); return out.createByteArray();
                } finally { in.recycle(); out.recycle(); }
            }
            @Override public void destroy() throws RemoteException { call(DESTROY); }
        }
    }
}
