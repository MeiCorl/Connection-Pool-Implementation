package com.meicorl.connection_pool;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ConcurrentPool implements IPool {
    /**
     * 数据库驱动
     */
    private final String jdbcDriver;

    /**
     * 数据库连接
     */
    private final String jdbcUrl;

    /**
     * 数据库用户名
     */
    private final String userName;

    /**
     * 数据库密码
     */
    private final String password;

    /**
     * 连接池大小
     */
    private final AtomicInteger size;

    /**
     * 最大活跃连接数
     */
    private final int MAX_ACTIVE = 100;

    private final ConcurrentLinkedQueue<Connection> idleConnections;

    private final ConcurrentLinkedQueue<Connection> activeConnections;

    private final ConcurrentLinkedQueue<Connection> bufferConnections;

    private final ConcurrentLinkedQueue<Thread> waitingQueue;

    public ConcurrentPool(String jdbcDriver, String jdbcUrl, String userName, String password, int size) {
        this.jdbcDriver = jdbcDriver;
        this.jdbcUrl = jdbcUrl;
        this.userName = userName;
        this.password = password;
        this.size = new AtomicInteger(size);
        this.idleConnections = new ConcurrentLinkedQueue<>();
        this.activeConnections = new ConcurrentLinkedQueue<>();
        this.bufferConnections = new ConcurrentLinkedQueue<>();
        this.waitingQueue = new ConcurrentLinkedQueue<>();

        init();
    }

    private void init() {
        try {
            // 1. 注册数据库连接信息
            Driver driver = (Driver) Class.forName(jdbcDriver).newInstance();
            DriverManager.registerDriver(driver);

            // 2. 初始化连接池
            initConnectionPool();
        } catch (IllegalAccessException | InstantiationException | ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private void initConnectionPool() throws SQLException {
        for (int i = 0; i < size.get(); i++) {
            Connection connection = DriverManager.getConnection(jdbcUrl, userName, password);
            connection.isValid(2000);
            idleConnections.offer(connection);
        }
    }

    @Override
    public void showDetail() {
        System.out.println("pool size: " + size);
        System.out.println("idle size: " + idleConnections.size());
        System.out.println("active size: " + activeConnections.size());
        System.out.println("buffer size: " + bufferConnections.size());
        System.out.println();
    }

    /**
     * 从连接池获取链接，首先从空闲连接池获取，若没有则看是否可以创建新的链接
     *
     * @return 返回一个数据库链接，出错返回null
     */
    @Override
    public Connection getConnection() {
        Connection connection = idleConnections.poll();
        if (connection != null) {
            try {
                /* 检查连接是否有效，无效则重连 */
                if (!connection.isValid(2000)) {
                    connection = DriverManager.getConnection(jdbcUrl, userName, password);
                }
                activeConnections.offer(connection);
                return connection;
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        } else if (size.incrementAndGet() <= MAX_ACTIVE) {
            try {
                connection = DriverManager.getConnection(jdbcUrl, userName, password);
                connection.isValid(2000);
                activeConnections.offer(connection);
                return connection;
            } catch (SQLException e) {
                size.getAndIncrement();
                e.printStackTrace();
                return null;
            }
        } else {
            size.decrementAndGet();
            /* 无空闲链接且最大活跃连接数已到最大值，将当前线程阻塞并加入等待队列，直到有新的空闲链接或者超时 */
            waitingQueue.offer(Thread.currentThread());
            LockSupport.parkNanos((long) (3 * Math.pow(10, 9)));  // 当前线程沉睡等待，超时时间3s
            try {
                connection = bufferConnections.poll();
                /* 检查连接是否有效，无效则重连 */
                if (connection != null  && !connection.isValid(2000)) {
                    connection = DriverManager.getConnection(jdbcUrl, userName, password);
                }
                return connection;
            } catch (SQLException  e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * 释放链接, 若当前有线程在等待，则直接把链接给第一个等待的线程
     *
     * @param connection 待回收的链接
     */
    @Override
    public void releaseConnection(Connection connection) {
        // 先检查等待队列中是否有线程在等待可用链接
        Thread t = waitingQueue.poll();
        while (t != null) {
            // 检查线程是否依旧存活(可能因为什么异常或者等待超时已经退出)
            if (t.isAlive()) {
                bufferConnections.offer(connection);
                LockSupport.unpark(t);  // 唤醒等待线程
                return;
            }
            t = waitingQueue.poll();
        }

        // 没有急需连接的等待线程则将链接从活跃队列转移至空闲队列
        if (activeConnections.remove(connection)) {
            idleConnections.offer(connection);
        }
    }
}
