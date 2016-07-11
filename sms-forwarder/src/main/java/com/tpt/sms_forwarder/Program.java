package com.tpt.sms_forwarder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.*;
import java.util.Properties;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created on 2016/2/24.
 */
public class Program implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger("[sms]");
    private static final Charset utf8_charset = Charset.forName("utf-8");
    private final Connection connection;
    private final String table;
    private final String smsUrl;
    private final CloseableHttpClient client;


    public Program(Connection connection, String table,String smsUrl, CloseableHttpClient client) {
        this.connection = connection;
        this.table = table;
        this.smsUrl = smsUrl;
        this.client = client;
    }

    @Override
    public void close() throws IOException {
        DbUtils.closeQuietly(connection);
    }

    public static void main(String[] args) throws IOException, SQLException {
        if(args.length != 1) {
            System.out.println("config file is missing.");
            return;
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println("'"+ args[0]+"' is not found.");
            return;
        }
        if (!file.isFile()) {
            System.out.println("'"+ args[0]+"' is not found.");
            return;
        }
        Properties props = new Properties();
        props.load(new FileInputStream(file));

        String driverClass = Enforce.ThrowIfNullOrEmpty(props.getProperty("dbClass"), "driver class is empty.");
        String url = Enforce.ThrowIfNullOrEmpty(props.getProperty("dbUrl"), "db url is empty.");
        String user = Enforce.ThrowIfNullOrEmpty(props.getProperty("dbUser"), "db user is empty.");
        String password = Enforce.ThrowIfNullOrEmpty(props.getProperty("dbPassword"), "db password is empty.");
        String table = Enforce.ThrowIfNullOrEmpty(props.getProperty("dbTable"), "db table is empty.");
        String smsUrl= Enforce.ThrowIfNullOrEmpty(props.getProperty("smsUrl"), "sms url is empty.");

        int interval = 1000;
        try {
            String s = props.getProperty("poll_interval");
            if(null != s && !s.isEmpty()) {
                interval = Integer.parseInt(s);
                if(interval < 10) {
                    interval = 10;
                }
            }
        } catch (NumberFormatException e){
            interval = 1000;
        }
        String query = "SELECT id, phone, message, max_retries, retries FROM " + table + " WHERE send_at IS NULL AND (max_retries IS NULL OR retries IS NULL OR max_retries > retries)";
        if(!DbUtils.loadDriver(driverClass)) {
            throw new RuntimeException("'"+driverClass+"' is not found.");
        }
        Connection conn = DbUtils.open(url, user, password);
        Program program = new Program(conn, table, smsUrl,
                HttpClientBuilder.create()
                .useSystemProperties()
                .build());

        logger.info("start ok.");
        try {
            //noinspection InfiniteLoopStatement
            for (; ; ) {
                if (program.run(query) == 0) {
                    Thread.sleep(interval);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            program.close();
        }
    }

    private int run(String query) throws SQLException {
        int count = 0;
        Statement statement = null;
        ResultSet resultSet = null;

        try {
            statement = connection.createStatement();
            resultSet = statement.executeQuery(query);
            // String[] names = columnNames(resultSet)
            while (resultSet.next()) {
                Message msg = readMessage(resultSet);
                if(msg.getMaxRetries() <= msg.getRetries()) {
                    continue;
                }
                count ++;
                boolean sendOk = false;
                try {
                    logger.info("send message " + Integer.toString(msg.getId()));
                    sendMessage(msg);
                    sendOk = true;
                } catch (Exception e) {
                    logger.info("failed to send message " + Integer.toString(msg.getId()), e);
                    saveError(msg, e);
                }
                if(sendOk) {
                    saveOk(msg);
                }
            }
        }finally {
            DbUtils.closeQuietly(null, statement, resultSet);
        }
        return count;
    }

    private void saveOk(Message msg) throws SQLException {
        PreparedStatement update = connection.prepareStatement("UPDATE " + table + " SET send_at = ? WHERE id = ?");
        update.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
        update.setInt(2, msg.getId());
        update.executeUpdate();
    }

    private void saveError(Message msg, Exception e) throws SQLException {
        String error = e.getMessage();

        while (error.getBytes(utf8_charset).length > 199) {
            if(error.length() > 199) {
                error = error.substring(0, 199);
            } else {
                error = error.substring(0, error.length()-1);
            }
        }

        PreparedStatement update = connection.prepareStatement("UPDATE " + table + " SET failed_at = ?, last_error = ?, retries = ? WHERE id = ?");
        update.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
        update.setString(2, error);
        update.setInt(3, msg.getRetries() + 1);
        update.setInt(4, msg.getId());
        update.executeUpdate();
    }

    private void sendMessage(Message msg) throws IOException {
        HttpPost request = new HttpPost(smsUrl);
        request.setHeader("Accept", "application/json");
        request.setHeader("Accept-Charset", "utf-8");
        request.setHeader("Content-Type", "application/json;charset=UTF-8");
        request.setHeader("Connection", "Keep-Alive");
        request.setEntity(new StringEntity(msg.toJSON(), ContentType.APPLICATION_JSON));

        CloseableHttpResponse response = client.execute(request);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK
                    && statusCode != HttpStatus.SC_ACCEPTED
                    && statusCode != HttpStatus.SC_CREATED ) {
                throw new RuntimeException(errorResponse(response));
            }
        } finally {
            closeContent(response);
            response.close();
        }
    }

    private Message readMessage(ResultSet resultSet) throws SQLException {
        //id, phone, message, max_retries, retries
        Message msg = new Message();
        msg.setId(resultSet.getInt(1));
        msg.setPhone(resultSet.getString(2));
        msg.setMessage(resultSet.getString(3));
        msg.setMaxRetries(readInt(resultSet.getInt(4), 3));
        msg.setRetries(readInt(resultSet.getInt(5), 0));
        return msg;
    }

    private static int readInt(Number number, int defaultValue) {
        if(null == number) {
            return defaultValue;
        }
        return number.intValue();
    }

    private static String errorResponse(HttpResponse response) throws IOException {
        InputStream content = response.getEntity().getContent();
        try {
            byte[] bytes = ByteStreams.toByteArray(content);
            String result = utf8_charset.decode(ByteBuffer.wrap(bytes)).toString();
            if (Strings.isNullOrEmpty(result)) {
                return String.format("Server returned unexpected error because of `%s`", response.getStatusLine());
            } else {
                return String.format("Server returned unexpected error because of `%s:%s`", response.getStatusLine(), result);
            }
        } finally {
            content.close();
        }
    }


    private static void closeContent(CloseableHttpResponse response) throws IOException {
        if (null != response.getEntity() && null != response.getEntity().getContent()) {
            response.getEntity().getContent().close();
        }
    }
//
//    private static String[] columnNames(ResultSet rs) throws SQLException {
//        ResultSetMetaData rsmd =  rs.getMetaData();
//        int column_count =  rsmd.getColumnCount();
//        String[] columnNames = new String[column_count];
//        for (int i = 1; i <= column_count; i++) {
//            String columnName = rsmd.getColumnLabel(i);
//            columnNames[i - 1] = columnName;
//        }
//        return columnNames;
//    }
}
