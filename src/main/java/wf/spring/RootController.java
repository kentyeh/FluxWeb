package wf.spring;

import cn.apiclub.captcha.Captcha;
import cn.apiclub.captcha.backgrounds.FlatColorBackgroundProducer;
import cn.apiclub.captcha.backgrounds.GradiatedBackgroundProducer;
import cn.apiclub.captcha.gimpy.DropShadowGimpyRenderer;
import cn.apiclub.captcha.gimpy.RippleGimpyRenderer;
import cn.apiclub.captcha.text.renderer.ColoredEdgesWordRenderer;
import cn.apiclub.captcha.text.renderer.DefaultWordRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import wf.data.MemberManager;
import wf.model.Member;
import wf.model.WebSocketRedisListener;
import wf.util.Loggers4j2;

/**
 *
 * @author Kent Yeh
 */
@Controller
public class RootController {

    private static final Logger logger = Loggers4j2.getLogger(RootController.class);

    public static final Random RAND = new SecureRandom();

    private ObjectMapper objectMapper;

    private MemberManager memberManager;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Autowired
    public void setManager(MemberManager memberManager) {
        this.memberManager = memberManager;
    }

    //<editor-fold defaultstate="collapsed" desc="Grapic preparing">
    private static final List<Color> COLORS = new ArrayList<>(2);

    private static final List<Font> FONTS = new ArrayList<>(3);
    private static final char[] DEFAULT_CHARS = {'A', 'B', 'C', 'D', 'e', 'E', 'F',
        'g', 'H', 'K', 'k', 'L', 'M', 'm', 'N', 'n', '2', '3', '4', '5', '6', '7', '8', '9',
        'N', 'P', 'p', 'R', 'S', 'T', 'U', 'V', 'W', 'w', 'X', 'x', 'Y', 'y', 'Z', 'z'};

    static {
        COLORS.add(Color.BLACK);
        COLORS.add(Color.BLUE);
        COLORS.add(Color.CYAN);
        COLORS.add(Color.GREEN);
        COLORS.add(Color.MAGENTA);
        COLORS.add(Color.ORANGE);
        COLORS.add(Color.PINK);
        COLORS.add(Color.RED);
        COLORS.add(Color.YELLOW);
        FONTS.add(new Font("Times New Roman", Font.BOLD, 24));
        FONTS.add(new Font("Times New Roman", Font.ITALIC, 24));
        FONTS.add(new Font("Courier", Font.BOLD, 24));
        FONTS.add(new Font("Courier", Font.ITALIC, 24));
        FONTS.add(new Font("Monospace", Font.BOLD, 24));
        FONTS.add(new Font("Monospace", Font.ITALIC, 24));
        FONTS.add(new Font("Arial", Font.BOLD, 32));
        FONTS.add(new Font("Arial", Font.ITALIC, 28));
    }

    private String getChptcha(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(DEFAULT_CHARS[RAND.nextInt(DEFAULT_CHARS.length)]);
        }
        return sb.toString();
    }

    private Color getRandColor(int fc, int bc) {
        if (fc > 255) {
            fc = 255;
        }
        if (bc > 255) {
            bc = 255;
        }
        int r = fc + RAND.nextInt(bc - fc);
        int g = fc + RAND.nextInt(bc - fc);
        int b = fc + RAND.nextInt(bc - fc);
        return new Color(r, g, b);
    }
    //</editor-fold>

    @Value("#{systemProperties['captcha'] }")
    private String defaultCaptcha;

    @GetMapping({"/", "/index"})
    public String index() {
        return "index";
    }

    @GetMapping("/whoami")
    public String whomai(@AuthenticationPrincipal Mono<UserDetails> principal, Model model) {
        model.addAttribute("user", principal);
        return "index";
    }

    /**
     *
     * @return PreAuthorize 回傳值必須是Mono/Flux 不可
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public Mono<String> testAdmin() {
        return Mono.just("admin");
    }

    @GetMapping("/static/captcha")
    public Mono<ResponseEntity<byte[]>> captcha(WebSession session,
            @RequestParam(name = "key", defaultValue = "captcha") String cpkey,
            @RequestParam(name = "len", defaultValue = "4") final int len) throws Exception {
        boolean rgr = RAND.nextBoolean();
        Captcha captcha = new Captcha.Builder(80, 36)
                //<editor-fold defaultstate="collapsed" desc="draw">
                .addNoise((BufferedImage bi) -> {
                    Graphics2D g = bi.createGraphics();
                    int h = bi.getHeight();
                    int w = bi.getWidth();
                    for (int i = 0; i < 155; i++) {
                        int x = RAND.nextInt(w);
                        int y = RAND.nextInt(h);
                        int xl = RAND.nextInt(10);
                        int yl = RAND.nextInt(4);
                        g.setColor(rgr ? getRandColor(32, 48) : getRandColor(0, 196));
                        g.drawLine(x, y, x + xl, y + yl);
                    }
                })
                .addText(() -> {
                    //<editor-fold defaultstate="collapsed" desc="Random Text Generate">
                    String val = defaultCaptcha == null || defaultCaptcha.trim().isEmpty() ? getChptcha(len) : defaultCaptcha.trim();
                    session.getAttributes().put(cpkey, val);
                    return val;
                    //</editor-fold>
                }, RAND.nextBoolean() ? new ColoredEdgesWordRenderer(COLORS, FONTS) : new DefaultWordRenderer(COLORS, FONTS))
                .gimp(rgr ? new RippleGimpyRenderer() : new DropShadowGimpyRenderer())
                .addBorder()
                .addBackground(RAND.nextBoolean() ? new FlatColorBackgroundProducer() : new GradiatedBackgroundProducer())
                //</editor-fold>
                .build();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(captcha.getImage(), "png", baos);
            byte[] data = baos.toByteArray();
            MultiValueMap<String, String> properties = new LinkedMultiValueMap<>();
            properties.add("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
            properties.add("Content-Type", MediaType.IMAGE_PNG_VALUE);//如果用produces在舊版FF會有產生406錯誤
            return Mono.just(new ResponseEntity<>(data, new HttpHeaders(properties), HttpStatus.OK));
        }
    }

    @GetMapping("/login")
    public Mono<String> getLoginPage() {
        return Mono.just("login");
    }

    @GetMapping("/hello/{member}")
    public String hello(@PathVariable("member") Member.Mono member, Model model) {
        model.addAttribute("user", member.get().switchIfEmpty(Mono.empty()));
        return "hello";
    }

    @PostMapping("/putmyshtoppingcart")
    public Mono<ResponseEntity<String>> putAstaff(WebSession session,
            @AuthenticationPrincipal User principal) {
        List<WebSocketRedisListener> wsrls = FluxWebSocketHandler.userListenser.get(session.getId());
        if (wsrls == null || wsrls.isEmpty()) {
            logger.error("{}沒有登錄資料", principal.getUsername());
            return Mono.just(new ResponseEntity<>("沒有登錄資料", HttpStatus.FORBIDDEN));
        } else {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("shoppingcard", "您剛把東西放進購物車");
            JsonNode jn = objectMapper.convertValue(json, JsonNode.class);
            logger.info("publish {}  {}", principal.getUsername(), jn.toString());
            return wsrls.get(0).getReactiveRedisTemplate().convertAndSend(principal.getUsername(), jn)
                    .then(Mono.just(new ResponseEntity<>("已放入購物車", HttpStatus.OK)));
        }
    }

    @GetMapping("/changePassword")
    public String getChangePassword() {
        return "changePassword";
    }

    @PostMapping("/changePassword")
    public Mono<String> changePassword(@AuthenticationPrincipal UsernamePasswordAuthenticationToken principal,
            ServerWebExchange exchange, Model model) {
        return exchange.getFormData().doOnNext(mvm -> {
            if (Objects.equal(mvm.getFirst("oldPass"), mvm.getFirst("newPass"))) {
                model.addAttribute("message", "新舊密碼不可以相同");
            } else if (!Objects.equal(mvm.getFirst("newPass"), mvm.getFirst("confirmPass"))) {
                model.addAttribute("message", "確認密碼與新密碼不符");
            }
        })
                .filter(mvm -> model.getAttribute("message") == null)
                .flatMap(mvm -> {
                    String newPass = mvm.getFirst("newPass");
                    String oldPass = mvm.getFirst("oldPass");
                    return memberManager.changePassword(principal.getName(), newPass, oldPass)
                    .doOnNext(i -> {
                        model.addAttribute("message", i > 0 ? "密碼更新成功" : "更新密碼失敗");
                    })
                    .map(i -> i > 0 ? "index" : "changePassword");
                }).switchIfEmpty(Mono.just("changePassword"));

    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/modifyMember/{member}")
    public Mono<String> memberAdminView(@PathVariable("member") Member.Mono member, Model model) {
        model.addAttribute("member", member.get());
        return Mono.just("member");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/modifyMember/{member}")
    public Mono<String> modifyMember(@PathVariable("member") Member.Mono oriMember,
            @ModelAttribute Member member, Model model) {
        //避免後面有值但第一個沒勾，導致null字串 
        Iterables.removeIf(member.getRoles(), Predicates.isNull());
        model.addAttribute("member", memberManager.saveMember(member.setNew(false)));
        return Mono.just("member");
    }

    @GetMapping("/ws")
    public String chat() {
        return "chat";
    }
}
