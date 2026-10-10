package org.mesutormanli.bistadvisor.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mesutormanli.bistadvisor.adapters.indices.BistIndices;
import org.mesutormanli.bistadvisor.application.AnalysisResult;
import org.mesutormanli.bistadvisor.application.AnalysisService;
import org.mesutormanli.bistadvisor.application.PortfolioService;
import org.mesutormanli.bistadvisor.application.PortfolioViewService;
import org.mesutormanli.bistadvisor.application.Recommendation;
import org.mesutormanli.bistadvisor.domain.advisor.RiskProfile;
import org.mesutormanli.bistadvisor.domain.advisor.Signal;
import org.mesutormanli.bistadvisor.domain.portfolio.PortfolioSnapshot;
import org.mesutormanli.bistadvisor.domain.portfolio.Position;
import org.mesutormanli.bistadvisor.domain.portfolio.ValidationResult;
import org.mesutormanli.bistadvisor.domain.settings.AnalysisType;
import org.mesutormanli.bistadvisor.domain.settings.ModelType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AdvisorController} API şemasının testleri: uç nokta gövdelerindeki JSON
 * anahtar adlarının arayüz sözleşmesiyle ({@code index.html}) birebir aynı
 * kalması ve taslak/işlem uçlarının davranışının korunması.
 * <p>
 * Uygulama servisleri Mockito ile taklit edilir; controller
 * {@code MockMvc standaloneSetup} ile kurulur, böylece yalnız HTTP katmanının
 * serileştirme davranışı sınanır. Hiçbir test ağa çıkmaz.
 */
class AdvisorControllerTest {

    private PortfolioService portfolioService;
    private AnalysisService analysisService;
    private PortfolioViewService portfolioViewService;
    private BistIndices bistIndices;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        analysisService = mock(AnalysisService.class);
        portfolioViewService = mock(PortfolioViewService.class);
        bistIndices = mock(BistIndices.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdvisorController(
                portfolioService, analysisService, portfolioViewService, bistIndices)).build();
    }

    /**
     * {@code GET /api/config} gövdesinin arayüz anahtarlarını taşıması.
     * <p>
     * Senaryo: servis seçimleri BALANCED / RANDOM_FOREST / TECHNICAL_FUNDAMENTAL /
     * BIST_30 döner. Beklenen davranış: yanıtta {@code modes}, {@code models},
     * {@code analysisTypes}, {@code indices}, {@code currentMode},
     * {@code currentModel}, {@code currentAnalysisType} ve {@code currentIndex}
     * anahtarları doğru değerlerle bulunur.
     */
    @Test
    void configYapilandirmayiArayuzAnahtarlariylaDonderir() throws Exception {
        when(portfolioService.riskProfile()).thenReturn(RiskProfile.BALANCED);
        when(portfolioService.modelType()).thenReturn(ModelType.RANDOM_FOREST);
        when(portfolioService.analysisType()).thenReturn(AnalysisType.TECHNICAL_FUNDAMENTAL);
        when(portfolioService.selectedIndex()).thenReturn("BIST_30");
        when(bistIndices.indexNames()).thenReturn(List.of("BIST_30", "BIST_50"));

        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modes").isArray())
                .andExpect(jsonPath("$.models").isArray())
                .andExpect(jsonPath("$.analysisTypes").isArray())
                .andExpect(jsonPath("$.indices[0]").value("BIST_30"))
                .andExpect(jsonPath("$.currentMode").value("BALANCED"))
                .andExpect(jsonPath("$.currentModel").value("RANDOM_FOREST"))
                .andExpect(jsonPath("$.currentAnalysisType").value("TECHNICAL_FUNDAMENTAL"))
                .andExpect(jsonPath("$.currentIndex").value("BIST_30"));
    }

    /**
     * {@code GET /api/portfolio} gövdesinin durum dosyası alanlarını taşıması.
     * <p>
     * Senaryo: anlık görüntü tek THYAO pozisyonuyla döner. Beklenen davranış:
     * yanıtta {@code budget}, {@code cash}, {@code advisorMode}, {@code modelType},
     * {@code analysisType}, {@code selectedIndex}, {@code positions} ve
     * {@code lastRunDate} alanları bulunur; pozisyon altındaki {@code symbol},
     * {@code lots} ve {@code avgCost} alanları korunur.
     */
    @Test
    void portfolioAnlikGorunumuAlanAdlariylaDonderir() throws Exception {
        PortfolioSnapshot s = new PortfolioSnapshot();
        s.budget = 50_000;
        s.cash = 8_450;
        s.advisorMode = "BALANCED";
        s.modelType = "RANDOM_FOREST";
        s.analysisType = "TECHNICAL";
        s.selectedIndex = "BIST_50";
        s.positions = new ArrayList<>(List.of(new Position("THYAO", 10, 240)));
        s.lastRunDate = "2026-10-11";
        when(portfolioService.snapshot()).thenReturn(s);

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").value(50_000.0))
                .andExpect(jsonPath("$.cash").value(8_450.0))
                .andExpect(jsonPath("$.advisorMode").value("BALANCED"))
                .andExpect(jsonPath("$.modelType").value("RANDOM_FOREST"))
                .andExpect(jsonPath("$.analysisType").value("TECHNICAL"))
                .andExpect(jsonPath("$.selectedIndex").value("BIST_50"))
                .andExpect(jsonPath("$.positions[0].symbol").value("THYAO"))
                .andExpect(jsonPath("$.positions[0].lots").value(10))
                .andExpect(jsonPath("$.positions[0].avgCost").value(240.0))
                .andExpect(jsonPath("$.lastRunDate").value("2026-10-11"));
    }

    /**
     * Geçersiz taslağın {@code error} ile reddedilip kaydedilmemesi.
     * <p>
     * Senaryo: negatif bütçeli taslak POST edilir. Beklenen davranış: yanıt
     * {@code {"status":"error"}} ve {@code message} alanında
     * {@code "Gecersiz butce: -5.0"} bulunur; {@code update} hiç çağrılmaz.
     */
    @Test
    void gecersizTaslakHataDonderirVeKaydetmez() throws Exception {
        mockMvc.perform(post("/api/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":-5,\"positions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Gecersiz butce: -5.0"));

        verify(portfolioService, never()).update(anyDouble(), any(), any(), any(), any(), any());
    }

    /**
     * Geçerli taslağın {@code ok} ile kaydedilmesi ve kanonik analiz tipiyle
     * servise devredilmesi.
     * <p>
     * Senaryo: tam alanlı geçerli taslak POST edilir, doğrulama sorunsuz döner.
     * Beklenen davranış: yanıtta yalnızda {@code {"status":"ok"}} bulunur
     * ({@code message} serileştirilmez) ve servise analiz tipi enum adıyla
     * ({@code TECHNICAL}) aktarılır.
     */
    @Test
    void gecerliTaslakOkDonderirVeKanonikTipIleKaydeder() throws Exception {
        when(portfolioService.validate()).thenReturn(ValidationResult.OK);
        when(bistIndices.containsIndex("BIST_30")).thenReturn(true);

        mockMvc.perform(post("/api/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"budget":50000,"advisorMode":"BALANCED","modelType":"RANDOM_FOREST",
                                "analysisType":"TECHNICAL","selectedIndex":"BIST_30",
                                "positions":[{"symbol":"THYAO","lots":10,"avgCost":240.0}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").doesNotExist());

        verify(portfolioService).update(eq(50_000.0), eq("BALANCED"), eq("RANDOM_FOREST"),
                eq("TECHNICAL"), eq("BIST_30"), any());
    }

    /**
     * Kural ihlali bulunan kaydın {@code warning} ile dönmesi.
     * <p>
     * Senaryo: taslak biçimsel olarak geçerli ama kuralları ihlal ediyor (örn.
     * negatif nakit). Beklenen davranış: yanıt {@code {"status":"warning"}} ve
     * ihlal metniyle {@code message} alanını taşır.
     */
    @Test
    void kuralIhaliKayittaWarningDonderir() throws Exception {
        when(portfolioService.validate()).thenReturn(
                new ValidationResult(List.of("Negatif nakit (-1000 TL)")));

        mockMvc.perform(post("/api/portfolio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"budget":1000,"positions":[{"symbol":"THYAO","lots":10,"avgCost":200.0}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("warning"))
                .andExpect(jsonPath("$.message").value("Negatif nakit (-1000 TL)"));
    }

    /**
     * {@code POST /api/confirm} gövdesinde uygulanan/reddedilen sayaçlarının
     * tutulması.
     * <p>
     * Senaryo: iki işlemden biri uygulanır, diğeri reddedilir. Beklenen davranış:
     * yanıt {@code {"status":"ok","applied":1,"failed":1}} olur.
     */
    @Test
    void confirmIslemSonuclariniSayar() throws Exception {
        when(portfolioService.apply("THYAO", "AL", 10, 240.0)).thenReturn(true);
        when(portfolioService.apply("GARAN", "SAT", 5, 100.0)).thenReturn(false);

        mockMvc.perform(post("/api/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"symbol":"THYAO","action":"AL","lots":10,"price":240.0},
                                {"symbol":"GARAN","action":"SAT","lots":5,"price":100.0}]"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    /**
     * {@code POST /api/analyze} yanıtındaki öneri anahtarlarının korunması.
     * <p>
     * Senaryo: holding ve alım önerileri içeren analiz sonucu döner. Beklenen
     * davranış: yanıtta {@code holdings}, {@code buys}, {@code availableCash},
     * {@code positionCount}, {@code maxPositions}, {@code buySlots} ve
     * {@code warnings} anahtarları; öneri kalemlerinde ise {@code index},
     * {@code symbol}, {@code action}, {@code lots}, {@code price}, {@code score}
     * ve {@code note} anahtarları bulunur (sinyal metin etiketiyle yazılır).
     */
    @Test
    void analizYanitiOneriAnahtalariniKorur() throws Exception {
        Recommendation sell = new Recommendation(1, "THYAO", Signal.SAT, 60, 250.0, 0.42, "+4.17%");
        Recommendation buy = new Recommendation(2, "AKBNK", Signal.AL, 500, 10.0, 0.90, "risk payi");
        when(analysisService.analyze()).thenReturn(new AnalysisResult(
                List.of(sell), List.of(buy), 1_234.5, 1, 5, 2, List.of("uyari")));

        mockMvc.perform(post("/api/analyze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].index").value(1))
                .andExpect(jsonPath("$.holdings[0].symbol").value("THYAO"))
                .andExpect(jsonPath("$.holdings[0].action").value("SAT"))
                .andExpect(jsonPath("$.holdings[0].lots").value(60))
                .andExpect(jsonPath("$.holdings[0].price").value(250.0))
                .andExpect(jsonPath("$.holdings[0].score").value(0.42))
                .andExpect(jsonPath("$.holdings[0].note").value("+4.17%"))
                .andExpect(jsonPath("$.buys[0].action").value("AL"))
                .andExpect(jsonPath("$.availableCash").value(1_234.5))
                .andExpect(jsonPath("$.positionCount").value(1))
                .andExpect(jsonPath("$.maxPositions").value(5))
                .andExpect(jsonPath("$.buySlots").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("uyari"));
    }

    /**
     * {@code GET /api/pending} gövdesinin yalnız uygulanabilir işlemleri döndürmesi.
     * <p>
     * Senaryo: holding SAT, holding TUT ve alım AL önerileri içeren analiz sonucu
     * döner. Beklenen davranış: yanıt listesinde 2 kalemi bulunur — SAT holding ve
     * AL alım; TUT holding dışarıda kalır.
     */
    @Test
    void pendingYalnizUygulanabilirIslemleriDonderir() throws Exception {
        Recommendation sell = new Recommendation(1, "THYAO", Signal.SAT, 60, 250.0, 0.42, "+4.17%");
        Recommendation hold = new Recommendation(2, "ASELS", Signal.TUT, 50, 351.0, 0.60, "+2.00%");
        Recommendation buy = new Recommendation(3, "AKBNK", Signal.AL, 500, 10.0, 0.90, "risk payi");
        when(analysisService.analyze()).thenReturn(new AnalysisResult(
                List.of(sell, hold), List.of(buy), 0, 2, 5, 0, List.of()));

        String body = mockMvc.perform(get("/api/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        assertEquals(2, com.jayway.jsonpath.JsonPath.parse(body).read("$", List.class).size());
    }
}