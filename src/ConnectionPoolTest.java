import com.meicorl.connection_pool.IPool;
import com.meicorl.connection_pool.PoolFactory;

import java.sql.Connection;
import java.util.ArrayList;

public class ConnectionPoolTest implements Runnable {
    private static final String jdbcDriver = "com.mysql.cj.jdbc.Driver";
    private static final String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/test?serverTimezone=Asia/Shanghai";
    private static final String userName = "root";
    private static final String password = "";

    private static IPool pool;

    private int sleepTime;

    ConnectionPoolTest(int sleepTime) {
        this.sleepTime = sleepTime;
    }

    @Override
    public void run() {
        try {
            Connection connection = pool.getConnection();
            System.out.println(Thread.currentThread().getName() + "    connection is null: " + (connection == null));

            Thread.sleep(sleepTime);
            if (connection != null) {
                pool.releaseConnection(connection);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        int threadNum = 300;
        pool = PoolFactory.getInstance(jdbcDriver, jdbcUrl, userName, password, 10);

        long start = System.currentTimeMillis();
        ArrayList<Thread> l = new ArrayList<>(100);
        for (int i = 0; i < threadNum; i++)
            l.add(new Thread(new ConnectionPoolTest(50)));

        for (int i = 0; i < threadNum; i++)
            l.get(i).start();

        for(int i = 0; i<threadNum; i++ )
            l.get(i).join();
        long end = System.currentTimeMillis();
        System.out.println("Time used: " + (end - start) + "ms");
    }
}
