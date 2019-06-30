package com.example.aidlserver;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
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
    private CopyOnWriteArrayList<Book> list = new CopyOnWriteArrayList<>();

    private CopyOnWriteArrayList<IOnNewBookArrivedListener> listenerList = new CopyOnWriteArrayList<>();

    // 判断服务是否还活着（AtomicBoolean，高并发下只有一个线程可以访问）
    private AtomicBoolean isServiceDestoryed = new AtomicBoolean(false);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        list.add(new Book(0, "name0"));
        list.add(new Book(1, "name1"));

        // 开启一个线程，每个5s添加一本新书
        new Thread(new ServiceWorker()).start();
    }

    public void onDestroy() {
        isServiceDestoryed.set(true);
        super.onDestroy();
    }

    private class ServiceWorker implements Runnable{
        @Override
        public void run() {

            while (!isServiceDestoryed.get()){
                try {

                    Thread.sleep(3000);

                    int bookId = list.size() + 1;
                    String bookName = "name"+bookId;
                    Book newBook = new Book(bookId,bookName);
                    list.add(newBook);

                    // 回调监听
                    for (int i = 0; i < listenerList.size(); i++) {
                        if (listenerList.get(i)==null){
                            Log.e("","----0");
                        }else{
                            Log.e("","----1");
                            listenerList.get(i).onNewBookArrivedListener(newBook);
                        }
                    }

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
        @Override
        public List<Book> getBookList() throws RemoteException {
            return list;
        }

        @Override
        public void addBook(Book book) throws RemoteException {
            list.add(book);
        }

        @Override
        public void registerListener(IOnNewBookArrivedListener listener) throws RemoteException {
            if (!listenerList.contains(listener)) {
                listenerList.add(listener);
                Log.e("", "----listener register success");
            } else {
                Log.e("", "----listener already exist");
            }
            Log.e("", "----register, listenerList size: " + listenerList.size());
        }

        @Override
        public void unregisterListener(IOnNewBookArrivedListener listener) throws RemoteException {
            if (listenerList.contains(listener)) {
                listenerList.remove(listener);
                Log.e("", "----listener unregister success");
            } else {
                Log.e("", "----listener not found");
            }
            Log.e("", "----unregister, current listenerList size: " + listenerList.size());
        }
    };

}
