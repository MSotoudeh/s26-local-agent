package io.projectir.mobile;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShellService extends IInterface {
    String exec(String command, String cwd, String envJson, int timeoutSeconds) throws RemoteException;
    void destroy() throws RemoteException;

    abstract class Stub extends Binder implements IShellService {
        private static final String DESCRIPTOR = "io.projectir.mobile.IShellService";
        private static final int TRANSACTION_EXEC = IBinder.FIRST_CALL_TRANSACTION;
        // Shizuku reserves this transaction for UserService teardown.
        private static final int TRANSACTION_DESTROY = 16777115;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShellService asInterface(IBinder binder) {
            if (binder == null) return null;
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IShellService) return (IShellService) local;
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code == TRANSACTION_EXEC) {
                data.enforceInterface(DESCRIPTOR);
                String command = data.readString();
                String cwd = data.readString();
                String envJson = data.readString();
                int timeoutSeconds = data.readInt();
                String result = exec(command, cwd, envJson, timeoutSeconds);
                reply.writeNoException();
                reply.writeString(result);
                return true;
            }
            if (code == TRANSACTION_DESTROY) {
                data.enforceInterface(DESCRIPTOR);
                destroy();
                if (reply != null) reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IShellService {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public String exec(String command, String cwd, String envJson, int timeoutSeconds)
                    throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(command);
                    data.writeString(cwd);
                    data.writeString(envJson);
                    data.writeInt(timeoutSeconds);
                    remote.transact(TRANSACTION_EXEC, data, reply, 0);
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void destroy() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    remote.transact(TRANSACTION_DESTROY, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
