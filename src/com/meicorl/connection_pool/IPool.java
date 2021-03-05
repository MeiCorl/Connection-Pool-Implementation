package com.meicorl.connection_pool;

import java.sql.Connection;

public interface IPool {
    void showDetail();
    Connection getConnection();
    void releaseConnection(Connection connection);
}
