package org.hello.london.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hello.london.core.Notify;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Topics {

    private DataSource postgres;

    public Topics(DataSource postgres) {
        this.postgres = postgres;
    }

    public long publish(String topic, byte[] payload) throws Exception {
        Connection conn = postgres.getConnection();
        long lastMsgId = -1;
        conn.setAutoCommit(false);
        try {
            // increase last message's id
            PreparedStatement update = conn.prepareStatement("UPDATE topics SET lastmsgid = lastmsgid + 1 WHERE id = ? RETURNING lastmsgid");
            try {
                update.setString(1, topic);
                ResultSet rs = update.executeQuery();
                if (rs.next()) {
                    lastMsgId = rs.getLong(1);
                }
                rs.close();
            } finally {
                update.close();
            }
            if (lastMsgId == -1) {
                // create topic
                PreparedStatement insert = conn.prepareStatement("INSERT INTO topics(id, lastmsgid) VALUES (?, ?)");
                try {
                    insert.setString(1, topic);
                    insert.setLong(2, 1);
                    boolean ok = insert.executeUpdate() == 1;
                    if (!ok) {
                        throw new SQLException("Create topic failed.");
                    }
                } finally {
                    insert.close();
                }
                lastMsgId = 1;
            }
            // notify through postgres' channel
            PreparedStatement notify = conn.prepareStatement("SELECT pg_notify(?, ?)");
            try {
                notify.setString(1, "mqtt");
                Notify notice = new Notify(topic, lastMsgId, payload);
                ObjectMapper mapper = new ObjectMapper();
                notify.setString(2, mapper.writeValueAsString(notice));
                notify.executeQuery().close();
            } finally {
                notify.close();
            }
            conn.commit();
            return lastMsgId;
        } catch (Exception e) {
            conn.rollback();
            throw new RuntimeException("Publish failed.", e);
        } finally {
            conn.close();
        }
    }

}