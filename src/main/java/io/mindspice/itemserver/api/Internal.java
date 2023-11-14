package io.mindspice.itemserver.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.mindspice.databaseservice.client.api.OkraNFTAPI;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.itemserver.schema.ApiMint;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.schema.ApiMintReq;
import io.mindspice.itemserver.services.AvatarService;
import io.mindspice.itemserver.services.LeaderBoardService;
import io.mindspice.jxch.rpc.schemas.wallet.nft.MetaData;
import io.mindspice.jxch.transact.logging.TLogLevel;
import io.mindspice.jxch.transact.logging.TLogger;
import io.mindspice.jxch.transact.service.mint.MintItem;
import io.mindspice.jxch.transact.service.mint.MintService;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.util.JsonUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.IntStream;


@CrossOrigin
@RestController
@RequestMapping("/internal")
public class Internal {
    private final MintService mintService;
    private final AvatarService avatarService;
    private final LeaderBoardService leaderBoardService;
    private final OkraNFTAPI nftAPI;
    private final MetaData accountNFTMeta;
    private final TLogger logger;

    public Internal(
            @Qualifier("mintService") MintService mintService,
            @Qualifier("avatarService") AvatarService avatarService,
            @Qualifier("leaderBoardService") LeaderBoardService leaderBoardService,
            @Qualifier("okraNFTAPI") OkraNFTAPI nftAPI,
            @Qualifier("customLogger") TLogger customLogger,
            @Qualifier("accountNFTMeta") MetaData accountNFTMeta
    ) {
        this.mintService = mintService;
        this.avatarService = avatarService;
        this.leaderBoardService = leaderBoardService;
        this.nftAPI = nftAPI;
        this.accountNFTMeta = accountNFTMeta;
        this.logger = customLogger;
    }

    @PostMapping("/mint_card_nfts")
    public ResponseEntity<String> mintCardNfts(@RequestBody String jsonReq) throws IOException {
        try {
            ApiMintReq mintReq = JsonUtils.readValue(jsonReq, ApiMintReq.class);
            List<Card> cards = nftAPI.getCardCollection(mintReq.collection()).data().orElseThrow();
            var mints = mintReq.mint_list().stream().map(m ->
                    new MintItem(
                            m.address(),
                            m.card_uid().equals("DID")
                                    ? accountNFTMeta
                                    : cards.stream().filter(c -> c.uid().equals(m.card_uid()))
                                    .findFirst().orElseThrow().metaData()
                                    .cloneSetEdt(nftAPI.getAndIncEdt(mintReq.collection(), m.card_uid()).data().get()),
                            m.job_uuid())
            ).toList();
            mintService.submit(mints);
        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.ERROR, "Failed to accept api mint", e);
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage() + " | "
                    + Arrays.toString(e.getStackTrace()))), HttpStatus.OK);
        }
        return new ResponseEntity<>(
                JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK
        );
    }

    @PostMapping("update_avatar")
    public ResponseEntity<String> updateAvatar(@RequestBody String jsonRequest) throws IOException {
        JsonNode node = JsonUtils.readTree(jsonRequest);
        String nftId = node.get("nft_id").asText();
        int playerId = node.get("player_id").asInt();
        if (nftId == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        avatarService.submit(new Pair<>(playerId, nftId));
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/mint_did_nfts")
    public ResponseEntity<String> mintDidNfts(@RequestBody String jsonReq) throws JsonProcessingException {
        try {
            JsonNode node = JsonUtils.readTree(jsonReq);
            int amount = node.get("amount").asInt();
            String uuid = "account:" + UUID.randomUUID();
            List<MintItem> mints = IntStream.range(0, amount)
                    .mapToObj(i -> new MintItem(Settings.get().didMintToAddr, accountNFTMeta, uuid))
                    .toList();

            mintService.submit(mints);
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage())), HttpStatus.OK);
        }
    }

    @PostMapping("set_paused")
    public ResponseEntity<Integer> setPaused(@RequestBody String req) throws JsonProcessingException {
        try {
            Settings.get().isPaused = JsonUtils.readTree(req).get("is_paused").asBoolean();
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.ERROR, "/authenticate threw exception:", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/health")
    public ResponseEntity<String> health(@RequestBody String req) throws JsonProcessingException {
        try {
            String ping = JsonUtils.readTree(req).get("ping").asText();
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.newSingleNode("pong", ping)), HttpStatus.OK);
        } catch (Exception e) {
            logger.log(this.getClass(), TLogLevel.ERROR, "/health threw exception:", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/get_leaderboard")
    public ResponseEntity<String> getLeaderBoard() {
        try {
            JsonNode response = new JsonUtils.ObjectBuilder()
                    .put("daily_scores", leaderBoardService.getDailyScores())
                    .put("weekly_scores", leaderBoardService.getWeeklyScores())
                    .put("monthly_scores", leaderBoardService.getMonthlyScores())
                    .buildNode();
            return new ResponseEntity<>(JsonUtils.writeString(response), HttpStatus.OK);
        } catch (JsonProcessingException e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
