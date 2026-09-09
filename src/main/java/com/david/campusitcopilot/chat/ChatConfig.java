package com.david.campusitcopilot.chat;

import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;

@Configuration
public class ChatConfig {

    @Bean
    public BaseCheckpointSaver checkpointSaver(DataSource dataSource) throws SQLException {
        return PostgresSaver.builder()
                .datasource(dataSource)
                .stateSerializer(new ObjectStreamStateSerializer<>(ConversationState::new))
                .createTables(true)
                .build();
    }
}
