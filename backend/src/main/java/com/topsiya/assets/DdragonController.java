package com.topsiya.assets;

import com.topsiya.store.AppProperties;
import com.topsiya.store.FileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Data Dragon(챔피언 한글 이름·아이콘)을 서버가 대신 받아서 파일로 저장해 둔다.
 * 발표장 인터넷이 끊겨도 한 번 본 챔피언 아이콘은 그대로 나온다.
 *
 * 정해진 세 가지 주소만 허용한다 (아무 주소나 대신 받아오는 통로가 되지 않도록).
 */
@RestController
@RequestMapping("/api/ddragon")
public class DdragonController {

    private static final Logger log = LoggerFactory.getLogger(DdragonController.class);
    private static final Pattern VERSION = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private static final Pattern CHAMPION_ID = Pattern.compile("[A-Za-z0-9]{1,40}");
    private static final String CATEGORY = "ddragon";

    private final RestClient client;
    private final FileStore store;
    private final AppProperties app;

    public DdragonController(FileStore store, AppProperties app) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(10_000);
        this.client = RestClient.builder()
                .baseUrl("https://ddragon.leagueoflegends.com")
                .requestFactory(requestFactory)
                .build();
        this.store = store;
        this.app = app;
    }

    /** 패치 버전 목록: 새 패치가 나오니 매번 받아보고, 실패하면 저장본 */
    @GetMapping("/versions.json")
    public ResponseEntity<byte[]> versions() {
        byte[] body = fresh("versions.json", () -> download("/api/versions.json"));
        return respond(body, MediaType.APPLICATION_JSON, Duration.ZERO);
    }

    /** 챔피언 한글 이름 (패치 버전별로 고정이라 한 번만 받는다) */
    @GetMapping("/{version}/champion.json")
    public ResponseEntity<byte[]> champions(@PathVariable String version) {
        if (!VERSION.matcher(version).matches()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = once(version + "-champion-ko.json",
                () -> download("/cdn/" + version + "/data/ko_KR/champion.json"));
        return respond(body, MediaType.APPLICATION_JSON, Duration.ofDays(7));
    }

    /** 챔피언 아이콘 */
    @GetMapping("/{version}/icon/{championId}")
    public ResponseEntity<byte[]> icon(@PathVariable String version, @PathVariable String championId) {
        if (!VERSION.matcher(version).matches() || !CHAMPION_ID.matcher(championId).matches()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = once(version + "-" + championId + ".png",
                () -> download("/cdn/" + version + "/img/champion/" + championId + ".png"));
        return respond(body, MediaType.IMAGE_PNG, Duration.ofDays(7));
    }

    /** 소환사의 협곡 미니맵 이미지 (데스 위치 표시용) */
    @GetMapping("/{version}/map")
    public ResponseEntity<byte[]> map(@PathVariable String version) {
        if (!VERSION.matcher(version).matches()) {
            return ResponseEntity.badRequest().build();
        }
        byte[] body = once(version + "-map11.png", () -> download("/cdn/" + version + "/img/map/map11.png"));
        return respond(body, MediaType.IMAGE_PNG, Duration.ofDays(30));
    }

    private byte[] download(String path) {
        return client.get().uri(path).retrieve().body(byte[].class);
    }

    private byte[] once(String fileName, Supplier<byte[]> call) {
        Optional<byte[]> saved = store.readBytes(CATEGORY, fileName);
        if (saved.isPresent() || app.demoMode()) {
            return saved.orElse(null);
        }
        return downloadAndSave(fileName, call);
    }

    private byte[] fresh(String fileName, Supplier<byte[]> call) {
        if (!app.demoMode()) {
            byte[] body = downloadAndSave(fileName, call);
            if (body != null) {
                return body;
            }
        }
        return store.readBytes(CATEGORY, fileName).orElse(null);
    }

    private byte[] downloadAndSave(String fileName, Supplier<byte[]> call) {
        try {
            byte[] body = call.get();
            if (body != null && body.length > 0) {
                store.writeBytes(CATEGORY, fileName, body);
                return body;
            }
        } catch (RuntimeException e) {
            log.warn("Data Dragon 다운로드 실패 ({}): {}", fileName, e.getMessage());
        }
        return null;
    }

    private static ResponseEntity<byte[]> respond(byte[] body, MediaType type, Duration maxAge) {
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        CacheControl cache = maxAge.isZero() ? CacheControl.noCache() : CacheControl.maxAge(maxAge);
        return ResponseEntity.ok().contentType(type).cacheControl(cache).body(body);
    }
}
