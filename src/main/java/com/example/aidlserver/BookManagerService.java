package com.example.aidlserver;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by haif on 2019/6/26.
 */

public class BookManagerService extends Service {

    // CopyOnWriteArrayList，支持并发读/写
    // 注意，AIDL中能够使用的List只有ArrayList，这里的CopyOnWriteArrayList为什么可以？这是因为Binder中会按照List的规范去访问数据并最终形成一个新的ArrayList传给客户端
    private CopyOnWriteArrayList<Book> bookList = new CopyOnWriteArrayList<>();

    // 使用RemoteCallbackList，这个类是专门用来删除跨进程listener的接口
//    private CopyOnWriteArrayList<IOnNewBookArrivedListener> listenerList = new CopyOnWriteArrayList<>();
    private RemoteCallbackList<IOnNewBookArrivedListener> listenerList = new RemoteCallbackList<>();

    // 判断服务是否还活着（AtomicBoolean，高并发下只有一个线程可以访问）
    private AtomicBoolean isServiceDestoryed = new AtomicBoolean(false);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.e("", "----service onBinder == null? " + mBinder == null ? "true" : "false");
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        bookList.add(new Book(0, "name0"));
        bookList.add(new Book(1, "name1"));

        // 开启一个线程，每个5s添加一本新书
        new Thread(new ServiceWorker()).start();
    }

    public void onDestroy() {
        isServiceDestoryed.set(true);
        super.onDestroy();
    }

    private class ServiceWorker implements Runnable {
        @Override
        public void run() {

            while (!isServiceDestoryed.get()) {
                try {

                    Thread.sleep(3000);

                    int bookId = bookList.size() + 1;
                    String bookName = "name" + bookId;
                    Book newBook = new Book(bookId, bookName);
                    bookList.add(newBook);

                    // 回调监听
                    int n = listenerList.beginBroadcast();
                    for (int i = 0; i < n; i++) {
                        IOnNewBookArrivedListener listener = listenerList.getBroadcastItem(i);
                        if (listener != null)
                            // 调用客户端的方法，如果客户端的方法是耗时操作，那么这里就必须开启子线程
                            listener.onNewBookArrivedListener(newBook);
                    }
                    listenerList.finishBroadcast();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    // 服务端暴露给客户端的方法，在AIDL中声明了，需要在服务端实现（new AIDL文件名.Stub()）
    private Binder mBinder = new IBookManager.Stub() {

        // 权限验证（通过验证的服务端才可以连接），这里采用双重验证
        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            // 先采用permission验证
            int check = checkCallingOrSelfPermission("com.example.aidlserver.ACCESS_CONNECT_SERVICE");
            if (check == PackageManager.PERMISSION_DENIED) {
                Log.e("", "----service0 false");
                return false;
            }

            // 再验证包名，比如包名必须是"com.example.aidlserver"开头
            String packageName = null;
            String[] packages = getPackageManager().getPackagesForUid(getCallingUid());
            if (packages != null && packages.length > 0) {
                packageName = packages[0];
            }
            if (!packageName.startsWith("com.example")) {
                Log.e("", "----service1 false");
                return false;
            }
            return super.onTransact(code, data, reply, flags);
        }

        @Override
        public List<Book> getBookList() throws RemoteException {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return bookList;
        }

        @Override
        public void addBook(Book book) throws RemoteException {
            bookList.add(book);
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void registerListener(IOnNewBookArrivedListener listener) throws RemoteException {
            listenerList.register(listener);
            Log.e("", "----register size " + listenerList.getRegisteredCallbackCount());
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
        @Override
        public void unregisterListener(IOnNewBookArrivedListener listener) throws RemoteException {
            listenerList.unregister(listener);
            Log.e("", "----register size " + listenerList.getRegisteredCallbackCount());
        }
    };

}
