package io.mindspice.itemserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mindspice.itemserver.schema.PlayerScore;
import io.mindspice.mindlib.util.JsonUtils;
import org.junit.jupiter.api.Test;


public class JsonTest {


    @Test
    void playerScoreTest() throws JsonProcessingException {
        var ps = new PlayerScore("test");
        ps.addResult(true);
        ps.addResult(true);
        ps.addResult(false);
        // check to see if it output winRatio
        System.out.println(JsonUtils.writePretty(ps));
    }
}
