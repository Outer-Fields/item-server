package io.mindspice.itemserver.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.mindspice.databaseservice.client.schema.Card;
import io.mindspice.itemserver.schema.ApiMint;
import io.mindspice.itemserver.Settings;
import io.mindspice.itemserver.services.AvatarService;
import io.mindspice.jxch.transact.jobs.mint.MintItem;
import io.mindspice.jxch.transact.jobs.mint.MintService;
import io.mindspice.mindlib.data.tuples.Pair;
import io.mindspice.mindlib.util.JsonUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;


@CrossOrigin
@RestController
@RequestMapping("/internal")
public class Internal {
    private final MintService mintService;
    private final AvatarService avatarService;

    private final List<Card> cardList;
    public static final TypeReference<List<ApiMint>> API_MINT_LIST = new TypeReference<>() { };

    public Internal(
            @Qualifier("mintService") MintService mintService,
            @Qualifier("avatarService") AvatarService avatarService,
            @Qualifier("cardList") List<Card> cardList
    ) {
        this.mintService = mintService;
        this.avatarService = avatarService;
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
            mintItems = mints.stream().map(
                    m -> new MintItem(
                            m.address(),
                            cardList.stream()
                                    .filter(c -> c.uid().equals(m.cardUID()))
                                    .findFirst().orElseThrow().metaData(),
                            m.jobUUID())
            ).toList();
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(JsonUtils.writeString(JsonUtils.errorMsg(e.getMessage())), HttpStatus.OK);
        }
        mintService.submit(mintItems);
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

//    @PostMapping("update_did")
//    public ResponseEntity<String> update_did(@RequestBody String jsonRequest) throws IOException {
//        JsonNode node = JsonUtils.readTree(jsonRequest);
//        String uuid = node.get("uuid").asText();
//        String offer = node.get("offer").asText();
//
//        if (offer == null) {
//            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
//        }
//        avatarUpdateService.submit(new Pair<>(uuid, offer));
//        return new ResponseEntity<>(HttpStatus.OK);
//    }

}
