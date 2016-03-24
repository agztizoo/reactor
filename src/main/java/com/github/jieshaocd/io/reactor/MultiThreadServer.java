/**
 * 
 */
package com.github.jieshaocd.io.reactor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多线程模式
 * 
 * @author jayshao
 * @since 2016年3月23日
 */
public class MultiThreadServer implements Runnable {

    private final ServerSocketChannel ssc;

    private final Selector select;

    public MultiThreadServer(int port) throws IOException {
        ssc = ServerSocketChannel.open();
        select = Selector.open();
        ssc.socket().bind(new InetSocketAddress(port));
        ssc.configureBlocking(false);
        ssc.register(select, SelectionKey.OP_ACCEPT, new Acceptor(ssc));
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int count = select.select();
                if (count <= 0) {
                    continue;
                }
                Set<SelectionKey> keys = select.selectedKeys();
                Iterator<SelectionKey> itor = keys.iterator();
                while (itor.hasNext()) {
                    dispatch(itor.next());
                }
                keys.clear();
            }
        } catch (Exception e) {
        }
    }

    private void dispatch(SelectionKey key) {
        Runnable r = (Runnable) key.attachment();
        if (r != null) {
            r.run();
        }
    }

    public static void main(String[] args) throws IOException {
        MultiThreadServer server = new MultiThreadServer(8180);
        server.run();
    }

    class Acceptor implements Runnable {
        private ServerSocketChannel ssc;

        public Acceptor(ServerSocketChannel ssc) {
            this.ssc = ssc;
        }

        @Override
        public void run() {
            try {
                SocketChannel sc = ssc.accept();
                if (sc != null) {
                    new Handler(select, sc);
                }
            } catch (Exception e) {
            }
        }
    }

    static class Handler implements Runnable {
        private final SocketChannel sc;
        private final SelectionKey key;
        private ByteBuffer input = ByteBuffer.allocate(1024);
        private ByteBuffer output = ByteBuffer.allocate(1024);

        private static final ExecutorService POOL = Executors.newFixedThreadPool(5);

        public Handler(Selector select, SocketChannel sc) throws IOException {
            this.sc = sc;
            sc.configureBlocking(false);
            key = sc.register(select, 0);
            key.attach(this);
            key.interestOps(SelectionKey.OP_READ);
        }

        @Override
        public void run() {
            try {
                if (key.isReadable()) {
                    read();
                } else if (key.isWritable()) {
                    write();
                }
            } catch (Exception e) {
            }
        }

        private void read() throws IOException {
            input.clear();
            sc.read(input);
            input.flip();
            POOL.execute(new Processor());
        }

        private void write() throws IOException {
            output.flip();
            sc.write(output);
            key.cancel();
            key.channel().close();
        }

        private void process() throws UnsupportedEncodingException {
            System.out.println(new String(input.array(), "utf-8"));
            output.put("received.\n".getBytes());
            key.interestOps(SelectionKey.OP_WRITE);
            //因为在多线程中，所以这里需要唤醒select，而单线程版本是不需要的
            key.selector().wakeup();
        }

        class Processor implements Runnable {
            @Override
            public void run() {
                try {
                    process();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
