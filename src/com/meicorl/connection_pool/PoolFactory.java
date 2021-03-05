package com.meicorl.connection_pool;

import java.util.concurrent.ConcurrentHashMap;

public class PoolFactory {
    private static final ConcurrentHashMap<Integer, IPool> poolMap = new ConcurrentHashMap<>();

    public static IPool getInstance(String jdbcDriver, String jdbcUrl, String userName, String password, int size) {
        int key = (jdbcDriver + jdbcUrl).hashCode();
        if (!poolMap.containsKey(key)) {
            synchronized (PoolFactory.class) {
                if (!poolMap.containsKey(key)) {
                    IPool pool = new ConcurrentPool(jdbcDriver, jdbcUrl, userName, password, size);
                    poolMap.put(key, pool);
                }
            }
        }
        return poolMap.get(key);
    }
}
