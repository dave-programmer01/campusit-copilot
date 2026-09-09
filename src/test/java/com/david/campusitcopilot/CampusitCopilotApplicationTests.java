package com.david.campusitcopilot;

import com.david.campusitcopilot.chat.ChatGraph;
import com.david.campusitcopilot.config.WebConfig;
import com.david.campusitcopilot.deflection.DeflectionController;
import com.david.campusitcopilot.deflection.DeflectionRepository;
import com.david.campusitcopilot.security.ChatRateLimiterFilter;
import com.david.campusitcopilot.security.InternalEndpointFilter;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
class CampusitCopilotApplicationTests {

    @Autowired
    private BaseCheckpointSaver checkpointSaver;

    @Autowired
    private ChatGraph chatGraph;

    @Autowired
    private DeflectionRepository deflectionRepository;

    @Autowired
    private DeflectionController deflectionController;

    @Autowired
    private InternalEndpointFilter internalEndpointFilter;

    @Autowired
    private ChatRateLimiterFilter chatRateLimiterFilter;

    @Autowired
    private WebConfig webConfig;

    @Test
    void contextLoads() {
        assertNotNull(checkpointSaver);
        assertInstanceOf(PostgresSaver.class, checkpointSaver);
        assertNotNull(chatGraph);
        assertSame(checkpointSaver, chatGraph.getCheckpointSaver());
        assertNotNull(deflectionRepository);
        assertNotNull(deflectionController);
        assertNotNull(internalEndpointFilter);
        assertNotNull(chatRateLimiterFilter);
        assertNotNull(webConfig);
    }

}
