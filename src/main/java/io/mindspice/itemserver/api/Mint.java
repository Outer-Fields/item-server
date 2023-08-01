package io.mindspice.itemserver.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.mindspice.databaseservice.client.api.OkraGameAPI;
import io.mindspice.itemserver.Schema.ApiMint;
import io.mindspice.itemserver.Schema.Card;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.monitor.BlockchainMonitor;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.mindlib.util.JsonUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;


@CrossOrigin
@RestController
public class Endpoints {
    private final MintService mintService;
    private final List<Card> cardList;
    public static final TypeReference<List<ApiMint>> API_MINT_LIST = new TypeReference<>() { };

    public Endpoints(
            @Qualifier("mintService") MintService mintService,
            @Qualifier("cardList") List<Card> cardList) {
        this.mintService = mintService;
        this.cardList = cardList;

    }

    @PostMapping("/mint_account_nft")
    public ResponseEntity<String> mintAccountNft(@RequestBody String jsonReq) throws JsonProcessingException {
        JsonNode node = JsonUtils.readTree(jsonReq);
        int playerId = node.get("player_id").asInt();
        String address = node.get("address").asText();
        MintItem mintItem = new MintItem(
                address,
                Settings.getAccountMintMetaData(),
                "account:" + playerId + ":" + UUID.randomUUID().toString()
        );
        mintService.submit(mintItem);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @PostMapping("/mint_card_nfts")
    public ResponseEntity<String> mintCardNfts(@RequestBody String jsonReq) throws IOException {
        JsonNode node = JsonUtils.readTree(jsonReq);
        List<ApiMint> mints = JsonUtils.readJson(node.traverse(), API_MINT_LIST);
        List<MintItem> mintItems;
        try {
            mintItems = mints.stream()
                    .map(m -> new MintItem(
                                 m.address(),
                                 cardList.stream()
                                         .filter(c -> c.uid().equals(m.cardUID()))
                                         .findFirst().orElseThrow().metaData(),
                                 m.jobUUID()
                         )
                    ).toList();
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage())), HttpStatus.OK);
        }
        mintService.submit(mintItems);
        return new ResponseEntity<>(
                JsonUtils.writeString(JsonUtils.successMsg(JsonUtils.newEmptyNode())), HttpStatus.OK
        );
    }


}
