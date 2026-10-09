# bist-advisor

[![Java CI](https://github.com/hyperpostulate/bist-advisor/actions/workflows/maven.yml/badge.svg)](https://github.com/hyperpostulate/bist-advisor/actions/workflows/maven.yml) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

BIST'te işlem gören hisseler için yapay zeka destekli portföy yönetim asistanı. Portföyünüzü oluşturun, makine öğrenmesi modellerimiz sizin için al/sat/tut önerileri üretsin. Üç farklı risk seviyesi (temkinli, dengeli, agresif) arasından seçim yapın, öneriler buna göre şekillensin. Web arayüzü veya komut satırı ile kolayca kullanın.

CLI ve Web arayüzlü, SMILE ML modelleri (RandomForest / SVM / KNN) ve Yahoo Finance canlı verisi kullanan Spring Boot uygulaması.

---

## İçindekiler

- [Gereksinimler](#gereksinimler)
- [Kurulum](#kurulum)
- [Derleme ve Test](#derleme-ve-test)
- [CI/CD](#cicd)
- [Mimari](#mimari)
- [Bileşenler](#bileşenler)
- [Kullanım Örnekleri](#kullanım-örnekleri)
- [API Uç Noktaları](#api-uç-noktaları)
- [Testler](#testler)
- [Hata Yönetimi](#hata-yönetimi)
- [Lisans](#lisans)
- [Geliştirici](#geliştirici)
- [Katkıda Bulunma](#katkıda-bulunma)

---

## Gereksinimler

| Gereksinim | Sürüm |
|------------|-------|
| Java | 25+ |
| Maven | 3.8+ |

---

## Kurulum

### Maven

Artifact (`org.mesutormanli:bist-advisor:0.1.0`) henüz uzak bir Maven deposuna yayımlanmamıştır
(projede `distributionManagement` tanımı yok), bu nedenle doğrudan bağımlılık olarak çözülemez.
Yerel deponuza kurmak için önce `mvn clean install` çalıştırın, ardından şunu ekleyin:

```xml
<dependency>
    <groupId>org.mesutormanli</groupId>
    <artifactId>bist-advisor</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Manuel Derleme

```bash
git clone https://github.com/hyperpostulate/bist-advisor.git
cd bist-advisor
mvn clean install
```

---

## Derleme ve Test

```bash
mvn clean test                    # Tam derleme + testler
mvn clean package                 # Derleme + test + paketleme
mvn test -Dtest=BistAdvisorTest   # Tek test sınıfı
```

### Docker

```bash
docker build -t bist-advisor .               # İmajı oluştur
docker run -p 8080:8080 bist-advisor         # Konteyneri çalıştır
```

Web arayüzü `http://localhost:8080` adresinde kullanılabilir olur.

```bash
docker run -d -p 8080:8080 --name bist-advisor bist-advisor  # Arka planda çalıştır
docker run -d -p 8080:8080 -v $(pwd)/state.yaml:/app/state.yaml -v $(pwd)/cache:/app/cache bist-advisor  # Kalıcı veri ile
docker stop bist-advisor              # Konteyneri durdur
docker rm bist-advisor                # Konteyneri kaldır
```

---

## CI/CD

GitHub Actions iş akışı (`.github/workflows/`):

- **maven.yml**: Her push ve pull request'te master branch'ine çalışır. Ubuntu-latest, Eclipse Temurin 25. Komut: `mvn -B package`

---

## Mimari

```
org.mesutormanli.bistadvisor
├── advisor/
│   ├── AllocationPlanner.java             # Saf karar motoru (SAT/AL tutarlılığı, slot + bütçe dağıtımı)
│   ├── DailyAdvisor.java                  # Günlük öneri orkestratörü (veri + model + planlayıcı)
│   └── ScoreGate.java                     # Model türüne göre yorumlanmış skor eşikleri
├── cli/
│   └── AdvisorCommands.java               # CLI komutları (init/run/confirm/status/train)
├── config/
│   ├── AdvisorMode.java                   # Yatırım modu enum (CONSERVATIVE/BALANCED/AGGRESSIVE)
│   ├── AnalysisType.java                  # Analiz tipi enum (TECHNICAL/FUNDAMENTAL/TECHNICAL_FUNDAMENTAL)
│   ├── AppConfig.java                     # Uygulama konfigürasyonu
│   └── ModelType.java                     # ML model tipi enum (RANDOM_FOREST/SVM/KNN)
├── data/
│   ├── BistIndices.java                   # BIST endeks-hisse eşleştirme (.properties)
│   ├── CacheStore.java                    # Fiyat serisi önbelleği (CSV dosya, tazeleme pencereli)
│   ├── MarketTime.java                    # Ortak piyasa saat dilimi + tazelik penceresi
│   └── YahooClient.java                   # Yahoo Finance OHLCV + temel veri çekici (crumb, throttle, TTL önbellek)
├── features/
│   ├── FeatureVector.java                 # 11 boyutlu özellik vektörü (analiz tipine göre 6/5/11 kesilir)
│   └── TechnicalFeatures.java             # Teknik göstergeler (RSI/SMA/MACD/volatilite)
├── model/
│   ├── ClassSpace.java                    # Sınıf-etiket eşlemesi (güvenli tahmin)
│   ├── FeatureFrame.java                  # Özellik matrisi + etiket dönüştürücü
│   ├── KnnStrategy.java                   # KNN sınıflandırıcı (SMILE)
│   ├── Labeler.java                       # Getiri bazlı etiketleyici (AL/SAT/TUT)
│   ├── ModelStrategy.java                 # ML strateji arayüzü
│   ├── ModelStrategyFactory.java          # Strateji fabrikası
│   ├── ModelTrainer.java                  # Model eğitimi + bellek-içi önbellek
│   ├── RandomForestStrategy.java          # RandomForest one-vs-rest (SMILE)
│   └── SvmStrategy.java                   # SVM one-vs-rest (SMILE Gaussian kernel)
├── portfolio/
│   ├── PortfolioService.java              # Portföy servisi (state.yaml kalıcılık)
│   ├── PortfolioState.java                # Portföy durumu (sermaye/nakit/mod/model/analiz/pozisyonlar)
│   └── Position.java                      # Pozisyon modeli (sembol/lot/maliyet)
├── web/
│   ├── AdvisorController.java             # REST API kontrolcüsü (/api/*)
│   └── StaticPageConfig.java              # Statik dosya sunumu (index.html)
└── BistAdvisorApplication.java            # Spring Boot giriş noktası
```

### Temel Bileşenler

| Bileşen | Açıklama |
|---------|----------|
| `DailyAdvisor` | Veri yükler, model tahminlerini üretir ve karar mantığını `AllocationPlanner`'a devreder; sembol başına tek özellik hesabı |
| `AllocationPlanner` | Saf karar motoru: SAT/TUT + AL listesi, slot/bütçe dağıtımı (birim teste alınabilir) |
| `ScoreGate` | Model türüne göre eşik yorumu (KNN'de oy ızgarasına yuvarlama) |
| `AdvisorCommands` | CLI komut arayüzü: `init`, `run`, `confirm`, `status`, `train` |
| `AdvisorMode` | 3 yatırım modu: TEMKİNLİ (%25 risk), DENGELİ (%50), AGRESİF (%75) |
| `AnalysisType` | 3 analiz tipi: YALNIZCA_TEKNIK (6 teknik metrik), YALNIZCA_TEMEL (5 temel metrik), TEKNIK_TEMEL (11 metrik) |
| `YahooClient` | Yahoo Finance'den OHLCV (fiyat) + temel veri (F/K, PD/DD, temettü, ROE, büyüme) çeker; crumb'ı her denemede yeniler, istekleri throttle eder, temel veriyi günlük TTL ile önbellekler |
| `BistIndices` | BIST-30/50/100 ve sektör endekslerini sembol listeleriyle tanımlar |
| `CacheStore` | Fiyat serilerini `cache/` dizininde CSV olarak önbelleğe alır |
| `MarketTime` | Tek piyasa saat dilimi (`Europe/Istanbul`) + hafta sonu/tatil toleranslı tazelik penceresi |
| `FeatureVector` | 11 özellik (analiz tipine göre 6 teknik / 5 temel / 11 kesilir): RSI, SMA-20/50 oranı, MACD, volatilite, hacim, F/K, PD/DD, temettü, büyüme, ROE |
| `ModelStrategy` | ML model arayüzü: `train(features, labels, analysisType)`, `predict(features)` → {sınıf, skor} |
| `ClassSpace` | Sınıf-etiket eşlemesi: eksik sınıflarda güvenli tahmin (olasılık dizisi boyutu tuzağını önler) |
| `RandomForestStrategy` | One-vs-rest RandomForest (gözlenen her sınıf için ikili sınıflandırıcı) |
| `SvmStrategy` | One-vs-rest SVM (Gaussian kernel) |
| `KnnStrategy` | k-NN sınıflandırıcı (k=5) |
| `Labeler` | N günlük getiriye göre etiketleme: >%5 → AL, <-%5 → SAT, arada → TUT |
| `ModelTrainer` | Canlı veriyle eğitim (analiz tipinin metrik kümesiyle), TİP:ANALİZ:ENDEKS anahtarlı bellek-içi önbellek |
| `PortfolioService` | state.yaml okuma/yazma, açık nakit muhasebesi (gerçekleşen kâr/zarar nakde yansır), kısmi satış, portföy kısıtları (maks 5 pozisyon) |
| `AdvisorController` | REST API: portföy okuma/güncelleme, analiz, işlem onayı, konfigürasyon |
| `BistAdvisorApplication` | Web modu (varsayılan) veya CLI modu (`--cli` / bilinen komut adı) |

### Bütçe ve Nakit Modeli

`budget` alanı **toplam sermaye katkısıdır** (kullanıcının koyduğu para), `cash` alanı ise **açık nakit**. Alım/satım işlemleri nakdi doğrudan değiştirir:

```
alım:  nakit −= lot × alış fiyatı
satım: nakit += lot × satış fiyatı
toplam değer (equity) = nakit + Σ (lot × güncel fiyat)
```

Bu modelde **gerçekleşen kâr/zarar nakde yansır**: 240 TL'den alıp 250 TL'den satmak 1.000 TL kârı serbest nakde ekler; zararlı satış nakdi azaltır. Kısmi satış desteklenir: satılan lot kadar pozisyon küçülür, ortalama maliyet korunur.

Manuel portföy güncellemeleri (arayüzden pozisyon ekleme/silme) nakdi maliyet farkı kadar kaydırır; pozisyon silme maliyet bedeliyle satılmış gibi nakde döner — gerçekleşen kâr/zarar için işlem onayını kullanın. Bütçe alanının değiştirilmesi para yatırma/çekme sayılır.

`state.yaml` dosyasında `cash` alanı her zaman bulunur; yüklenirken alan değerleri aynen korunur, herhangi bir türetme ya da dönüştürme yapılmaz.

### Bağımlılıklar

| Bağımlılık | Sürüm | Amaç |
|------------|-------|------|
| Spring Boot Web | 4.0.0 | REST/MVC çerçevesi |
| Spring Shell | 4.0.2 | Bağımlılık olarak mevcut; kabuk runner'ı devre dışı (CLI girişi `CommandLineRunner` ile yürür) |
| SMILE Core | 6.2.3 | ML algoritmaları (RF/SVM/KNN) |
| Jackson YAML | - | state.yaml kalıcılığı |
| Spring Boot Test | - | Test çerçevesi |
| JUnit Jupiter | - | Birim testler |

---

## Bileşenler

### AdvisorController

Konfigürasyon, portföy yönetimi, analiz ve işlem onayı sağlayan REST kontrolcüsü.

| Yöntem | Uç Nokta | Açıklama | HTTP Durumu |
|--------|----------|----------|-------------|
| `GET` | `/api/config` | Desteklenen modlar/modeller/analiz tipleri/endeksler + seçili değerler | 200 |
| `GET` | `/api/portfolio` | Mevcut portföy durumu | 200 |
| `POST` | `/api/portfolio` | Portföyü güncelle (bütçe/mod/model/analiz tipi/endeks/pozisyonlar) | 200 (`{"status":"ok\|warning\|error"}`) |
| `GET` | `/api/portfolio-view` | Portföy + güncel fiyatlar + toplam değerler | 200 |
| `POST` | `/api/analyze` | Günlük analizi çalıştır | 200 |
| `POST` | `/api/confirm` | İşlemleri onayla | 200 |
| `GET` | `/api/pending` | Bekleyen AL/SAT önerileri | 200 |

### AdvisorMode

Üç yatırım modu ve yapılandırılabilir eşikler:

| Mod | Risk % | Al Eşik | Stop Loss | Sat Skor Eşik |
|-----|--------|---------|-----------|---------------|
| TEMKİNLİ | 25% | 0.75 | 10% | 0.30 |
| DENGELİ | 50% | 0.60 | 15% | 0.25 |
| AGRESİF | 75% | 0.50 | 25% | 0.20 |

Risk yüzdesi, kullanılabilir nakdin alım adaylarına dağıtılan oranıdır. Modlar CLI/API'de `CONSERVATIVE`, `BALANCED`, `AGGRESSIVE` adlarıyla ya da etiketleriyle (`TEMKINLI`, `DENGELI`, `AGRESIF`) referans alınabilir; model anahtarları `random_forest`, `svm`, `knn`.

### AnalysisType

Analizde hangi metrik kümesinin (hem eğitim hem tahmin) kullanılacağını belirler; seçim `state.yaml` içindeki `analysisType` alanında persist edilir:

| Analiz Tipi | Kullanılan Metrikler | Boyut |
|-------------|----------------------|-------|
| YALNIZCA_TEKNIK | Teknik göstergeler (1–6) | 6 |
| YALNIZCA_TEMEL | Temel göstergeler (7–11) | 5 |
| TEKNIK_TEMEL | Tüm metrikler (1–11) | 11 |

Model, tahminle aynı analiz tipiyle eğitilir; önbellek anahtarı (`TİP:ANALİZ:ENDEKS`) sayesinde farklı metrik kümeleriyle eğitilmiş modeller birbirine karışmaz. **YALNIZCA_TEKNIK** modunda temel gösterge verisi hiç okunmaz/çekilmez; böylece güncel tarihli temel göstergelerin geçmişe uygulanmasından doğan geleceğe sızıntı (look-ahead bias) ortadan kalkar. Temel gösterge içeren modlarda analiz raporuna güncel tarihli temel veriye dair bir uyarı notu eklenir. Varsayılan `TECHNICAL_FUNDAMENTAL`'dır; `analysisType` alanı olmayan eski `state.yaml` dosyaları bu varsayılanla açılır. CLI/API'de `TECHNICAL`, `FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL` adlarıyla, anahtarlarıyla (`technical`, `fundamental`, `technical_fundamental`), etiketleriyle (`TEKNIK`, `TEMEL`, `TEKNIK_TEMEL`) ya da takma adlarıyla (`YALNIZCA_TEKNIK`, `YALNIZCA_TEMEL`, `TEKNIK_VE_TEMEL`) referans alınabilir.

### FeatureVector

11 boyutlu özellik vektörü. `normalize()` her özniteliği sabit ölçeklerle `[0, 1]` aralığına indirir ve uç değerleri kırpar; eğitim ve tahmin bu normalize edilmiş vektörle yapılır. Vektör seçilen analiz tipine göre kesilir: teknik küme ilk 6 sütunu, temel küme son 5 sütunu kullanır; eğitim matrisi ve tahmin vektörü her zaman aynı sütun düzenini paylaşır.

| # | Özellik | Kaynak |
|---|---------|--------|
| 1 | RSI (14) | Teknik |
| 2 | SMA-20 Oranı | Teknik |
| 3 | SMA-50 Oranı | Teknik |
| 4 | MACD | Teknik |
| 5 | Volatilite (20) | Teknik |
| 6 | Hacim Oranı (20) | Teknik |
| 7 | F/K | Temel |
| 8 | PD/DD | Temel |
| 9 | Temettü Verimi | Temel |
| 10 | Kâr Büyümesi | Temel |
| 11 | ROE | Temel |

### Labeler

Ufuk `bist.model.label-horizon-days` ile yapılandırılır (varsayılan: 20 gün).

N=20 günlük getiri bazlı etiketleme:
- Getiri > %5 → AL (0)
- Getiri < -%5 → SAT (1)
- Arada → TUT (2)

### YahooClient

Yahoo Finance'den hem OHLCV fiyat serisi (`v8/finance/chart/`) hem de temel verileri (`quoteSummary` + crumb/cookie) çeker. Crumb gerektiren isteklerde URL her denemede güncel crumb ile kurulur; 401'de crumb/çerez yenilenir. Rate-limit (429) ve 5xx hatalarında katlanarak backoff ile 3 kez yeniden dener, tüm isteklerde `bist.scrape.delay-ms` gecikmesi uygular. Temel veriler gün bazlı TTL ile bellekte önbelleklenir (eğitim ve tahmin aynı sembol için veri çekimini tekrarlamaz). Fiyat çıktısı: `tarih,kapanis,hacim`.

### Veri Tazeliği ve "Veri Yok" Durumu

Fiyat önbelleği (`cache/price_*.csv`) son barı `Europe/Istanbul` saat diliminde, **hafta sonu/tatil toleranslı** (son bar ≥ bugün − 4 gün) bir pencereyle taze sayar; o gün barı oluşmayacakken tüm seriler gereksiz yere yeniden indirilmez. Fiyat verisi alınamayan sembol için **sahte fiyat üretilmez**: portföy görünümünde `currentPrice`/`pnlPct`/`pnlTl` alanları `null` döner ve arayüzde "veri yok" gösterilir; analizde böyle pozisyonlar için karar üretilmez ve uyarı satırı basılır.

---

## Kullanım Örnekleri

### Web Arayüzünü Başlatma

```bash
java -jar target/bist-advisor-0.1.0.jar
```
Tarayıcıda `http://localhost:8080` açılır. Tek sayfa arayüz:

1. **Mevcut Portföy**: Pozisyon ekleme/çıkarma, toplam sermaye güncelleme
2. **Günlük Analiz**: Endeks/mod/analiz tipi/model seçimi, analiz çalıştırma
3. **İşlem Onayı**: AL/SAT önerilerini onaylama

### CLI: Portföy Başlatma

```bash
java -jar target/bist-advisor-0.1.0.jar init --budget=50000 --mode=BALANCED --analiz=TECHNICAL --model=RANDOM_FOREST --pos=THYAO:100:240,ASELS:50:351
```

`--budget` toplam sermaye katkısıdır (kullanıcının koyduğu para). Örnek portföyde maliyet 24.000 + 17.550 = 41.550 TL olur, nakit 8.450 TL olarak kurulur. `--analiz` analiz tipini seçer (`TECHNICAL`, `FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL`; bkz. [AnalysisType](#analysistype)); `--analysis` takma adı da geçerlidir. Verilmezse `TECHNICAL_FUNDAMENTAL` kullanılır ve `state.yaml` içindeki `analysisType` alanına yazılır.

> **Çalışma modu:** İlk argüman bilinen bir komut (`init/run/confirm/status/train`) ya da `--cli` bayrağı olduğunda CLI modu başlar; `--cli` sonradan da kullanılabilir (örn. `java -jar app.jar --cli train`). Diğer argümanlar (örn. `--spring.profiles.active=prod`, `--server.port=9090`) Spring yapılandırmasıdır ve uygulama **Web modunda** açılır. Bilinmeyen bir komut adı verilirse uygulama hata ile çıkar.

### CLI: Günlük Analiz

```bash
java -jar target/bist-advisor-0.1.0.jar run
```

### CLI: İşlem Onaylama

```bash
java -jar target/bist-advisor-0.1.0.jar confirm "THYAO,SAT,50,245.5" "AKBNK,AL,200,185.0"
```

### CLI: Portföy Durumu

```bash
java -jar target/bist-advisor-0.1.0.jar status
```

Çıktı `Mod`/`Model`/`Analiz` satırlarıyla birlikte kayıtlı ayarları ve portföy özetini gösterir.

### CLI: Model Eğitimi

```bash
java -jar target/bist-advisor-0.1.0.jar train
```

Eğitim, kayıtlı analiz tipinin metrik kümesiyle yapılır; analiz tipi değişince model otomatik olarak yeniden eğitilir.

### API: Analiz

```bash
curl -X POST http://localhost:8080/api/analyze
```

### API: Portföy Görünümü

```bash
curl http://localhost:8080/api/portfolio-view
```

---

## API Uç Noktaları

Tüm uç noktalar `/api` altında sunulur.

| Uç Nokta | Yöntem | Açıklama | Yanıt |
|----------|--------|----------|-------|
| `/api/config` | GET | Konfigürasyon (modlar/modeller/analiz tipleri/endeksler) | JSON |
| `/api/portfolio` | GET | Portföy durumu | `PortfolioState` |
| `/api/portfolio` | POST | Portföyü güncelle | `{"status":"ok\|warning\|error"}` |
| `/api/portfolio-view` | GET | Portföy + güncel fiyatlar | JSON |
| `/api/analyze` | POST | Günlük analiz çalıştır | `AnalysisResult` |
| `/api/confirm` | POST | İşlemleri onayla | `{"status":"ok","applied":"N","failed":"M"}` |
| `/api/pending` | GET | Bekleyen AL/SAT önerileri | JSON dizi |

### Analiz Yanıt Yapısı

```json
{
  "holdings": [
    {"index": 1, "symbol": "THYAO", "action": "SAT", "lots": 100, "price": 245.5, "score": 0.35, "note": "+2.30% | skor=0.35"},
    {"index": 2, "symbol": "BOZUK", "action": "TUT", "lots": 10, "price": null, "score": null, "note": "veri yok"}
  ],
  "buys": [
    {"index": 3, "symbol": "AKBNK", "action": "AL", "lots": 200, "price": 185.0, "score": 0.72, "note": "skor=0.72"}
  ],
  "availableCash": 12500.0,
  "positionCount": 2,
  "maxPositions": 5,
  "buySlots": 2,
  "warnings": ["Fiyat verisi alinamadi: BOZUK"]
}
```

`price`/`score` alanları veri/tahmin yoksa `null` döner (sahte değer üretilmez). `buySlots` doldurulabilecek **yeni** pozisyon sayısını verir; mevcut pozisyonlara ekleme yapmak slot harcamaz. Temel gösterge içeren analiz tiplerinde (`FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL`) `warnings` listesine temel göstergelerin güncel tarihli olduğuna dair bir not eklenir; `TECHNICAL` modunda eklenmez.

---

## Testler

Testler JUnit Jupiter ile yazılmıştır ve ağa bağımlılığı yoktur. Girdiler sentetik fiyat serileri, gömülü Yahoo JSON örneği ve geçici dizine yazılan `state.yaml` dosyalarıdır.

### Test Sınıfları

| Test Sınıfı | Ne Test Eder |
|-------------|--------------|
| `BistAdvisorTest` | Teknik göstergeler (RSI, volatilite), etiketleyici (AL sınıflandırması), Yahoo JSON ayrıştırma |
| `FeatureVectorTest` | Öznitelik normalizasyonu: ölçekleme, uç değer kırpma, immutability, analiz tipine göre boyut kesme |
| `AnalysisTypeTest` | Analiz tipi çözümleme: anahtar/takma ad eşlemeleri, bilinmeyen girdide varsayılan, metrik kümesi bayrakları |
| `ModelStrategiesTest` | Model stratejileri: sınıf ayrımı, eksik/tek sınıf güvenliği, KNN k=5 davranışı, analiz tipi boyutlarıyla eğitim |
| `AllocationPlannerTest` | Karar kuralları: SAT/AL çakışmaması, slot muhasebesi, satış sermayesinin bütçeye katılması, "veri yok" davranışı, skor doldurma |
| `ScoreGateTest` | Model bazlı eşik yorumu: KNN'de oy ızgarasına yuvarlama + asgari komşu anlaşması |
| `PortfolioServiceTest` | Nakit muhasebesi: gerçekleşen kâr/zararın nakde yansıması, kısmi/tam satış, negatif nakit, manuel güncelleme mutabakatı, analiz tipi persist, `state.yaml` yükleme |
| `CacheStoreTest` | Fiyat önbelleği: hafta sonu/tatil toleranslı tazelik penceresi, bozuk dosya güvenliği |
| `CliModeDetectionTest` | CLI/Web ayrımı: komut tespiti, `--cli` bayrağı, Spring argümanlarının Web modunu bozmaması |

### Test Çalıştırma

```bash
# Tüm testler
mvn test

# Tek test sınıfı
mvn test -Dtest=BistAdvisorTest
```

### Test Detayları

`BistAdvisorTest` üç test senaryosu içerir:

- `technicalFeaturesFromSeries()`: 60 günlük sentetik seride RSI (0-100) ve volatilite (≥0) doğrulaması
- `labelerAssignsClasses()`: %100 fiyat artışında AL etiketi atanması
- `priceScraperParsesYahooFixture()`: Yahoo Finance JSON ayrıştırma doğrulaması

`FeatureVectorTest` normalizasyon sözleşmesini doğrular:

- `normalizeDegerleriOlcekler()`: özniteliklerin sabit ölçeklerle [0,1]'e indirilmesi
- `normalizeAsiriDegerleriKisar()`: uç değerlerin [0,1]'e kırpılması
- `fromBarsHamDegerleriUretirVeNormalizeAyriNesneDoner()`: `normalize()`'ın değişmez (immutable) yeni nesne döndürmesi
- `analizTipiMetrikKumeleriniKesar()`: teknik (ilk 6) ve temel (son 5) alt kümelerinin kesilmesi
- `kolonAdlariDegerDuzeniyleEslesir()`: kolon adlarının değer dizisiyle sıradaşlığı
- `dimensionAnalizTipiBasinaDogruBoyutuDoner()`: analiz tipi başına 6/5/11 boyut sayısı

`ModelStrategiesTest` üç modelin ortak davranışını doğrular (model tipi başına parametrize):

- `ayirtEdiciSiniflariOgrenir()`: AL/SAT/TUT kümelerinin ayırt edilmesi
- `eksikSinifHatasiFirlatmaz()`: eğitim setinde bir sınıf eksikken istisna fırlatılmaması
- `tekSinifliEgitimSabitTahminDoner()`: tek sınıflı eğitimde sabit tahmin
- `knnBesKomsuIleKararVerir()`: KNN'in k=5 ile çoğunluk sınıfını seçmesi (k=1 olsaydı tersi olurdu)
- `classSpaceEtiketleriGeriDonusturur()`: `ClassSpace` sıkıştırma/geri dönüşüm eşlemesi
- `analizTipineGoreKesilmisBoyutlarlaCalisir()`: üç analiz tipinin matris genişlikleriyle eğitim/tahmin

`AnalysisTypeTest` çözümleme sözleşmesini doğrular:

- `fromKeyAnahtarTakmaAdVeEtiketleriCozer()`: anahtar, etiket ve takma adların çözümlenmesi
- `fromKeyBilinmeyenGirdideVarsayilanaDuser()`: bilinmeyen girdide `TECHNICAL_FUNDAMENTAL` varsayılanı
- `metrikKumesiBayraklariDogru()`: teknik/temel kullanım bayrakları (yalnız teknik modda temelin hiç okunmaması)

`PortfolioServiceTest` portföy muhasebesini doğrular (geçici `state.yaml` dosyasıyla):

- `initPortfolioNakdiSermayedenKurar()`: sermaye − maliyet = nakit kurgusu
- `kismiSatisPozisyonuKuculturVeGerceklesmisKarNakdeYansir()`: kısmi satış + gerçekleşen kârın nakde işlenmesi
- `tamSatisPozisyonuKaldirirVeKariYansitir()` / `zararliSatisNakdiAzaltir()`: tam satışta kâr ve zarar muhasebesi
- `fazlaSatisReddedilir()` / `nakitUzerindeAlimReddedilir()`: lot ve nakit sınırları
- `negatifNakitDogrulamaylaBildirilir()`: sermaye aşımı uyarısı
- `updatePortfolioButceDegisikliginiNakdeIsler()` / `updatePortfolioPozisyonFarkiniMaliyetKadarNakdeIsler()`: manuel güncelleme mutabakatı
- `analizTipiInitIleAyarlanirVeYamlaYazilir()`: analiz tipinin ayarlanıp `state.yaml`'a yazılması
- `updatePortfolioAnalizTipiniGunceller()`: analiz tipi güncelleme + `null`'da koruma + takma ad çözümü
- `analizTipiAlanOlmayanYamldaVarsayilanaDuser()`: `analysisType` alanı olmayan eski YAML'da varsayılana düşülmesi
- `yamlDosyasiAlanlariAynenYuklenir()`: `state.yaml` alanlarının aynen yüklenmesi

`AllocationPlannerTest` karar kurallarını doğrular:

- `satIsaretiAyniSemboluAlListesineKoymaz()`: stop-loss edilen sembolün aynı turda AL'a girmemesi
- `mevcutPozisyonaEklemeSlotHarcamazVeYeniAdayDisaridaKalir()` / `acilanSlotYeniPozisyonaKullanilir()`: slot muhasebesi
- `satislarinSerbestBiraktigiSermayeAlimButcesineEklenir()`: satış tutarının alım bütçesine katılması
- `fiyatVerisiOlmayanPozisyondaSahteFiyatUretilmez()`: "veri yok" davranışı ve uyarısı
- `holdingSkoruDoldurulurVeNotYuzdeyiTasir()`: skor/not içerikleri
- `esikAltiAlimAdayiEle()` / `modelSkoruEsigiGecmeyenPozisyonSatilmaz()` / `modelSkoruEsigiGecenPozisyonSatilir()`: eşik kapıları

`ScoreGateTest` eşik yorumlamasını doğrular: KNN'de eşiklerin oy ızgarasına (1/5 paylar) tavanlı yuvarlanması, tek komşu oyunun karar üretememesi, RandomForest/SVM'de temel eşiğin korunması.

`CacheStoreTest` önbellek tazelik kuralını doğrular: tolerans penceresi içindeki barın taze, eskisinin bayat sayılması; bozuk dosya güvenliği; yazma/okuma yuvarlanması.

`CliModeDetectionTest` çalışma modu ayrımını doğrular: bilinen komutların ve `--cli` bayrağının CLI başlatması, Spring argümanlarının (`--spring.profiles.active=...`) Web modunu bozmaması.

---

## Hata Yönetimi

| Senaryo | HTTP Durumu |
|---------|-------------|
| Başarılı analiz | 200 OK |
| Kaynak bulunamadı | 200 OK (boş liste) |
| Fiyat verisi alınamadı | 200 OK (`price: null`, "veri yok" — sahte fiyat üretilmez) |
| Geçersiz portföy verisi | 200 OK (`{"status":"error"}`, kayıt yapılmaz) |
| Geçersiz analiz tipi değeri | 200 OK (sessiz varsayılana düşülür: `TECHNICAL_FUNDAMENTAL`) |
| Kural ihlali (ör. negatif nakit, fazla lot) | 200 OK (`{"status":"warning"}`, kayıt yapılır) |
| Model eğitilemedi (endeks verisi yok) | 500 Internal Server Error |
| Yahoo API hatası | Sessiz atlanır, boş veri döndürülür; etkilenen semboller uyarı listesinde |
| state.yaml okunamaz | Boş portföy ile başlatılır |

Servis katmanı hataları loglanır; fiyat/temel veri alınamazsa boş veya varsayılan değerlerle devam edilir.

---

## Lisans

Bu proje GNU General Public License v3.0 altında lisanslanmıştır. Detaylı bilgi için [LICENSE](LICENSE) dosyasına bakın.

---

## Geliştirici

**Mesut ORMANLI**

- E-posta: [mesutormanli@gmail.com](mailto:mesutormanli@gmail.com)
- GitHub: [@hyperpostulate](https://github.com/hyperpostulate)

---

## Katkıda Bulunma

1. Depoyu fork edin
2. Bir özellik dalı oluşturun (`git checkout -b feature/new-feature`)
3. Değişikliklerinizi commit edin (`git commit -m 'Add new feature'`)
4. Dalı itin (`git push origin feature/new-feature`)
5. Bir Pull Request oluşturun

---

---

---

# bist-advisor

[![Java CI](https://github.com/hyperpostulate/bist-advisor/actions/workflows/maven.yml/badge.svg)](https://github.com/hyperpostulate/bist-advisor/actions/workflows/maven.yml) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

AI-powered portfolio management assistant for BIST-listed stocks. Build your portfolio and let our machine learning models generate buy/sell/hold recommendations tailored to your risk preference. Choose from three risk modes (conservative, balanced, aggressive) to match your investment style. Use via web interface or command line.

Spring Boot application with CLI and Web interfaces, SMILE ML models (RandomForest / SVM / KNN), and Yahoo Finance live data.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Build & Test](#build--test)
- [CI/CD](#cicd-1)
- [Architecture](#architecture)
- [Components](#components)
- [Usage Examples](#usage-examples)
- [API Endpoints](#api-endpoints)
- [Testing](#testing)
- [Error Handling](#error-handling)
- [License](#license)
- [Developer](#developer)
- [Contributing](#contributing)

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 25+ |
| Maven | 3.8+ |

---

## Installation

### Maven

The artifact (`org.mesutormanli:bist-advisor:0.1.0`) is not published to any remote Maven
repository (the project defines no `distributionManagement`), so it cannot be resolved as a
plain dependency. Install it locally first with `mvn clean install`, then add:

```xml
<dependency>
    <groupId>org.mesutormanli</groupId>
    <artifactId>bist-advisor</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Manual Build

```bash
git clone https://github.com/hyperpostulate/bist-advisor.git
cd bist-advisor
mvn clean install
```

---

## Build & Test

```bash
mvn clean test                    # Full build + tests
mvn clean package                 # Build + tests + package
mvn test -Dtest=BistAdvisorTest   # Single test class
```

### Docker

```bash
docker build -t bist-advisor .               # Build image
docker run -p 8080:8080 bist-advisor         # Run container
```

The web UI is available at `http://localhost:8080`.

```bash
docker run -d -p 8080:8080 --name bist-advisor bist-advisor  # Run in background
docker run -d -p 8080:8080 -v $(pwd)/state.yaml:/app/state.yaml -v $(pwd)/cache:/app/cache bist-advisor  # With persistent data
docker stop bist-advisor              # Stop container
docker rm bist-advisor                # Remove container
```

---

## CI/CD

GitHub Actions workflow (`.github/workflows/`):

- **maven.yml**: Runs on every push and pull request to master. Ubuntu-latest, Eclipse Temurin 25. Command: `mvn -B package`

---

## Architecture

```
org.mesutormanli.bistadvisor
├── advisor/
│   └── DailyAdvisor.java                  # Daily recommendation engine
├── cli/
│   └── AdvisorCommands.java               # CLI commands (init/run/confirm/status/train)
├── config/
│   ├── AdvisorMode.java                   # Investment mode enum (CONSERVATIVE/BALANCED/AGGRESSIVE)
│   ├── AnalysisType.java                  # Analysis type enum (TECHNICAL/FUNDAMENTAL/TECHNICAL_FUNDAMENTAL)
│   ├── AppConfig.java                     # Application configuration
│   └── ModelType.java                     # ML model enum (RANDOM_FOREST/SVM/KNN)
├── data/
│   ├── BistIndices.java                   # BIST index-to-symbol mapping from .properties
│   ├── CacheStore.java                    # Price series cache (CSV files)
│   └── YahooClient.java                   # Yahoo Finance OHLCV + fundamentals fetcher
├── features/
│   ├── FeatureVector.java                 # 11-dimensional feature vector (sliced 6/5/11 per analysis type)
│   └── TechnicalFeatures.java             # Technical indicators (RSI/SMA/MACD/volatility)
├── model/
│   ├── ClassSpace.java                    # Label-index mapping (safe predictions)
│   ├── FeatureFrame.java                  # Feature matrix + label converter
│   ├── KnnStrategy.java                   # KNN classifier (SMILE)
│   ├── Labeler.java                       # Return-based labeler (BUY/SELL/HOLD)
│   ├── ModelStrategy.java                 # ML strategy interface
│   ├── ModelStrategyFactory.java          # Strategy factory
│   ├── ModelTrainer.java                  # Model training + in-memory cache
│   ├── RandomForestStrategy.java          # RandomForest one-vs-rest (SMILE)
│   └── SvmStrategy.java                   # SVM one-vs-rest (SMILE Gaussian kernel)
├── portfolio/
│   ├── PortfolioService.java              # Portfolio service (state.yaml persistence)
│   ├── PortfolioState.java                # Portfolio state (budget/mode/model/analysis type/positions)
│   └── Position.java                      # Position model (symbol/lots/cost)
├── web/
│   ├── AdvisorController.java             # REST API controller (/api/*)
│   └── StaticPageConfig.java              # Static file serving (index.html)
└── BistAdvisorApplication.java            # Application entry point
```

### Core Components

| Component | Description |
|-----------|-------------|
| `DailyAdvisor` | Generates daily BUY/SELL/HOLD recommendations by combining portfolio and ML model |
| `AdvisorCommands` | CLI command interface: `init`, `run`, `confirm`, `status`, `train` |
| `AdvisorMode` | 3 investment modes: CONSERVATIVE (25% risk), BALANCED (50%), AGGRESSIVE (75%) |
| `AnalysisType` | 3 analysis types: TECHNICAL (6 technical metrics), FUNDAMENTAL (5 fundamental metrics), TECHNICAL_FUNDAMENTAL (11 metrics) |
| `YahooClient` | Fetches OHLCV prices + fundamentals (P/E, P/B, dividend, ROE, growth) from Yahoo Finance |
| `BistIndices` | Defines BIST-30/50/100 and sector indices with symbol lists |
| `CacheStore` | Caches fetched price series as CSV files in `cache/` directory |
| `FeatureVector` | 11 features (sliced as 6 technical / 5 fundamental / 11 per analysis type): RSI, SMA-20/50 ratio, MACD, volatility, volume, P/E, P/B, dividend, growth, ROE |
| `ModelStrategy` | ML model interface: `train(features, labels, analysisType)`, `predict(features)` → {class, score} |
| `ClassSpace` | Label-index mapping: safe predictions when classes are missing (avoids the probability-vector size pitfall) |
| `RandomForestStrategy` | One-vs-rest RandomForest (one binary classifier per observed class) |
| `SvmStrategy` | One-vs-rest SVM with Gaussian kernel |
| `KnnStrategy` | k-NN classifier (k=5) |
| `Labeler` | N-day return labeling: >5% → BUY, <-5% → SELL, else → HOLD |
| `ModelTrainer` | Live data training (with the analysis type's metric subset), TYPE:ANALYSIS:INDEX-keyed in-memory cache |
| `PortfolioService` | state.yaml read/write, budget-cash reconciliation (budget = total capital, cash is derived), partial sells, portfolio constraints (max 5 positions) |
| `AdvisorController` | REST API: portfolio read/update, analysis, confirmation, configuration |
| `BistAdvisorApplication` | Web mode (no args) or CLI mode (with args) |

### Budget & Cash Model

The `budget` field is the **total capital** (cash + positions' cost basis). Available cash is derived automatically:

```
cash = budget − Σ (lots × average cost)
```

Buy/sell transactions never change total capital; they only redistribute it between cash and cost basis. Partial sells are supported: the position shrinks by the sold lots while the average cost is preserved.

### Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot Web | 4.0.0 | REST/MVC framework |
| Spring Shell | 4.0.2 | CLI command interface |
| SMILE Core | 6.2.3 | ML algorithms (RF/SVM/KNN) |
| Jackson YAML | - | state.yaml persistence |
| Spring Boot Test | - | Test framework |
| JUnit Jupiter | - | Unit testing |

---

## Components

### AdvisorController

REST controller providing configuration, portfolio management, analysis, and transaction confirmation.

| Method | Endpoint | Description | HTTP Status |
|--------|----------|-------------|-------------|
| `GET` | `/api/config` | Get supported modes/models/analysis types/indices + current selection | 200 |
| `GET` | `/api/portfolio` | Get current portfolio state | 200 |
| `POST` | `/api/portfolio` | Save portfolio (budget/mode/model/analysis type/index/positions) | 200 (`{"status":"ok\|warning\|error"}`) |
| `GET` | `/api/portfolio-view` | Get portfolio with current prices and totals | 200 |
| `POST` | `/api/analyze` | Run daily analysis | 200 |
| `POST` | `/api/confirm` | Confirm transactions | 200 |
| `GET` | `/api/pending` | Get pending BUY/SELL recommendations | 200 |

### AdvisorMode

Three investment modes with configurable thresholds:

| Mode | Risk % | Buy Threshold | Stop Loss | Sell Score Threshold |
|------|--------|---------------|-----------|---------------------|
| CONSERVATIVE | 25% | 0.75 | 10% | 0.30 |
| BALANCED | 50% | 0.60 | 15% | 0.25 |
| AGGRESSIVE | 75% | 0.50 | 25% | 0.20 |

The risk percentage is the share of available cash distributed across buy candidates. Modes can be referenced in the CLI/API as `CONSERVATIVE`, `BALANCED`, `AGGRESSIVE` or by their labels (`TEMKINLI`, `DENGELI`, `AGRESIF`); model keys are `random_forest`, `svm`, `knn`.

### AnalysisType

Determines which metric subset is used for the analysis (both training and prediction); the selection is persisted in the `analysisType` field of `state.yaml`:

| Analysis Type | Metrics Used | Dimensionality |
|---------------|--------------|----------------|
| TECHNICAL | Technical indicators (1–6) | 6 |
| FUNDAMENTAL | Fundamental indicators (7–11) | 5 |
| TECHNICAL_FUNDAMENTAL | All metrics (1–11) | 11 |

The model is trained with the same analysis type as the prediction; the cache key (`TYPE:ANALYSIS:INDEX`) keeps models trained on different metric subsets apart. In **TECHNICAL** mode fundamental data is never read/fetched, eliminating the look-ahead bias caused by applying current-dated fundamentals to history. In modes that use fundamentals, a warning note about the fundamentals being current-dated is appended to the analysis report. The default is `TECHNICAL_FUNDAMENTAL`; legacy `state.yaml` files without an `analysisType` field open with this default. In the CLI/API the type can be referenced as `TECHNICAL`, `FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL`, by key (`technical`, `fundamental`, `technical_fundamental`), by label (`TEKNIK`, `TEMEL`, `TEKNIK_TEMEL`) or by alias (`YALNIZCA_TEKNIK`, `YALNIZCA_TEMEL`, `TEKNIK_VE_TEMEL`).

### FeatureVector

11-dimensional feature vector. `normalize()` scales every feature into `[0, 1]` with fixed divisors and clamps extreme values; training and prediction both run on this normalized vector. The vector is sliced per the selected analysis type: the technical subset uses the first 6 columns, the fundamental subset the last 5; the training matrix and the prediction vector always share the same column order.

| # | Feature | Source |
|---|---------|--------|
| 1 | RSI (14) | Technical |
| 2 | SMA-20 Ratio | Technical |
| 3 | SMA-50 Ratio | Technical |
| 4 | MACD | Technical |
| 5 | Volatility (20) | Technical |
| 6 | Volume Ratio (20) | Technical |
| 7 | P/E | Fundamental |
| 8 | P/B | Fundamental |
| 9 | Dividend Yield | Fundamental |
| 10 | Earnings Growth | Fundamental |
| 11 | ROE | Fundamental |

### Labeler

The horizon is configurable via `bist.model.label-horizon-days` (default: 20 days).

Return-based labeling with N=20 day horizon:
- Return > 5% → BUY (0)
- Return < -5% → SELL (1)
- Otherwise → HOLD (2)

### YahooClient

Fetches both OHLCV prices (`v8/finance/chart/`) and fundamentals (`quoteSummary` with crumb/cookie) from Yahoo Finance. Exponential backoff with 3 retries for rate-limiting (429) and 5xx errors. Price output: `date,close,volume`.

---

## Usage Examples

### Starting Web UI

```bash
java -jar target/bist-advisor-0.1.0.jar
```
Open `http://localhost:8080` in a browser. The single-page app provides:

1. **Current Portfolio**: Add/remove positions, update total capital
2. **Daily Analysis**: Select index/mode/analysis type/model, run analysis
3. **Transaction Confirmation**: Review and confirm BUY/SELL recommendations

### CLI: Initialize Portfolio

```bash
java -jar target/bist-advisor-0.1.0.jar init --budget=50000 --mode=BALANCED --analiz=TECHNICAL --model=RANDOM_FOREST --pos=THYAO:100:240,ASELS:50:351
```

`--budget` is the total capital (cash + positions' cost basis). In the sample portfolio the cost basis is 24,000 + 17,550 = 41,550 TL, leaving 8,450 TL of cash. `--analiz` selects the analysis type (`TECHNICAL`, `FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL`; see [AnalysisType](#analysistype)); the `--analysis` alias also works. When omitted, `TECHNICAL_FUNDAMENTAL` is used and persisted in the `analysisType` field of `state.yaml`.

### CLI: Daily Analysis

```bash
java -jar target/bist-advisor-0.1.0.jar run
```

### CLI: Confirm Transactions

```bash
java -jar target/bist-advisor-0.1.0.jar confirm "THYAO,SAT,50,245.5" "AKBNK,AL,200,185.0"
```

### CLI: Portfolio Status

```bash
java -jar target/bist-advisor-0.1.0.jar status
```

The output reports the stored settings (`Mod`/`Model`/`Analiz` lines) along with the portfolio summary.

### CLI: Train Model

```bash
java -jar target/bist-advisor-0.1.0.jar train
```

Training uses the metric subset of the stored analysis type; changing the analysis type retrains the model automatically.

### API: Analysis

```bash
curl -X POST http://localhost:8080/api/analyze
```

### API: Portfolio View

```bash
curl http://localhost:8080/api/portfolio-view
```

---

## API Endpoints

All endpoints are served under `/api`.

| Endpoint | Method | Description | Response |
|----------|--------|-------------|----------|
| `/api/config` | GET | Configuration (modes/models/analysis types/indices) | JSON |
| `/api/portfolio` | GET | Portfolio state | `PortfolioState` |
| `/api/portfolio` | POST | Update portfolio | `{"status":"ok\|warning\|error"}` |
| `/api/portfolio-view` | GET | Portfolio with current prices | JSON |
| `/api/analyze` | POST | Run daily analysis | `AnalysisResult` |
| `/api/confirm` | POST | Confirm transactions | `{"status":"ok","applied":"N","failed":"M"}` |
| `/api/pending` | GET | Pending BUY/SELL recommendations | JSON array |

### Analysis Response Structure

```json
{
  "holdings": [
    {"index": 1, "symbol": "THYAO", "action": "SAT", "lots": 100, "price": 245.5, "score": 0.0, "note": "+2.30% | score=0.35"}
  ],
  "buys": [
    {"index": 2, "symbol": "AKBNK", "action": "AL", "lots": 200, "price": 185.0, "score": 0.72, "note": "score=0.72"}
  ],
  "availableCash": 12500.0,
  "positionCount": 2,
  "maxPositions": 5,
  "buySlots": 2
}
```

When the analysis type uses fundamentals (`FUNDAMENTAL`, `TECHNICAL_FUNDAMENTAL`), a note about the fundamentals being current-dated is appended to the `warnings` array; in `TECHNICAL` mode no such note is added.

---

## Testing

Tests use JUnit Jupiter with no network dependency. Inputs are synthetic price series, an embedded Yahoo JSON sample, and `state.yaml` files written to a temp directory.

### Test Classes

| Test Class | What It Tests |
|------------|---------------|
| `BistAdvisorTest` | Technical features (RSI, volatility), Labeler (BUY classification), Yahoo JSON parsing |
| `FeatureVectorTest` | Feature normalization: scaling, clamping of extremes, immutability, per-analysis-type slicing |
| `AnalysisTypeTest` | Analysis type resolution: key/alias mappings, fallback default on unknown input, metric-subset flags |
| `ModelStrategiesTest` | Model strategies: class separation, missing/single-class safety, KNN k=5 behavior, training with analysis-type dimensions |
| `PortfolioServiceTest` | Portfolio accounting: partial sells, budget validation, analysis type persistence, `state.yaml` loading |

### Running Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=BistAdvisorTest
```

### Test Details

`BistAdvisorTest` provides three test scenarios:

- `technicalFeaturesFromSeries()`: Validates RSI (0-100 range) and volatility (≥0) from a synthetic 60-day series
- `labelerAssignsClasses()`: Verifies 100% price increase assigns BUY label
- `priceScraperParsesYahooFixture()`: Validates Yahoo Finance JSON parsing with a fixture

`FeatureVectorTest` verifies the normalization contract:

- `normalizeDegerleriOlcekler()`: features scaled into [0,1] with fixed divisors
- `normalizeAsiriDegerleriKisar()`: extreme values clamped into [0,1]
- `fromBarsHamDegerleriUretirVeNormalizeAyriNesneDoner()`: `normalize()` returns a new immutable instance
- `analizTipiMetrikKumeleriniKesar()`: technical (first 6) and fundamental (last 5) subsets are sliced out
- `kolonAdlariDegerDuzeniyleEslesir()`: column names match the value array order
- `dimensionAnalizTipiBasinaDogruBoyutuDoner()`: 6/5/11 dimensionality per analysis type

`ModelStrategiesTest` verifies the shared behavior of all three models (parameterized per model type):

- `ayirtEdiciSiniflariOgrenir()`: BUY/SELL/HOLD clusters are distinguished
- `eksikSinifHatasiFirlatmaz()`: no exception when a class is missing from the training set
- `tekSinifliEgitimSabitTahminDoner()`: constant prediction for single-class training
- `knnBesKomsuIleKararVerir()`: KNN picks the majority class with k=5 (k=1 would pick the other)
- `classSpaceEtiketleriGeriDonusturur()`: `ClassSpace` compress/decompress round-trip
- `analizTipineGoreKesilmisBoyutlarlaCalisir()`: training/prediction with the matrix widths of all three analysis types

`AnalysisTypeTest` verifies the resolution contract:

- `fromKeyAnahtarTakmaAdVeEtiketleriCozer()`: keys, labels and aliases are resolved
- `fromKeyBilinmeyenGirdideVarsayilanaDuser()`: unknown input falls back to `TECHNICAL_FUNDAMENTAL`
- `metrikKumesiBayraklariDogru()`: technical/subset usage flags (fundamentals never read in technical-only mode)

`PortfolioServiceTest` verifies portfolio accounting (with a temp `state.yaml`):

- `kismiSatisPozisyonuKuculturVeNakitTuretilir()`: partial sell + cash derivation
- `fazlaSatisReddedilir()` / `tamSatisPozisyonuKaldirir()`: lot limits and position closing
- `nakitUzerindeAlimReddedilir()`: buys beyond available cash are rejected
- `butceAsimiDogrulamaylaBildirilir()`: capital-overspend warning
- `analizTipiInitIleAyarlanirVeYamlaYazilir()`: analysis type set via init and written to `state.yaml`
- `updatePortfolioAnalizTipiniGunceller()`: analysis type update + preservation on `null` + alias resolution
- `analizTipiAlanOlmayanYamldaVarsayilanaDuser()`: legacy YAML without an `analysisType` field falls back to the default
- `yamlDosyasiAlanlariAynenYuklenir()`: `state.yaml` fields are loaded as-is

---

## Error Handling

| Scenario | HTTP Status |
|----------|-------------|
| Successful analysis | 200 OK |
| Resource not found | 200 OK (empty list) |
| Invalid portfolio payload | 200 OK (`{"status":"error"}`, nothing is saved) |
| Invalid analysis type value | 200 OK (silently falls back to `TECHNICAL_FUNDAMENTAL`) |
| Rule violation (e.g. cash overrun, excess lots) | 200 OK (`{"status":"warning"}`, saved) |
| Model cannot be trained (no index data) | 500 Internal Server Error |
| Yahoo API failure | Silently skipped, empty data returned |
| state.yaml unreadable | Starts with empty portfolio |

Service-layer errors are logged; when price/fundamental data cannot be fetched, processing continues with empty or default values.

---

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for details.

---

## Developer

**Mesut ORMANLI**

- Email: [mesutormanli@gmail.com](mailto:mesutormanli@gmail.com)
- GitHub: [@hyperpostulate](https://github.com/hyperpostulate)

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -m 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Create a Pull Request
