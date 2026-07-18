# bist-advisor

![Java CI](https://github.com/hyperpostulate/bist-advisor/actions/workflows/maven.yml/badge.svg) ![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)

BIST Portföy Danışmanı — Makine öğrenmesi destekli, web ve CLI arayüzlü yatırım asistanı. Yahoo Finance üzerinden canlı veri çeker, 3 ML modeli (RandomForest / SVM / KNN) ile AL/SAT/TUT sinyali üretir ve portföy yönetimi sunar.

---

## İçindekiler

- [Gereksinimler](#gereksinimler)
- [Kurulum](#kurulum)
- [Derleme ve Test](#derleme-ve-test)
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
|---|---|
| Java | 25+ |
| Maven | 3.8+ |

---

## Kurulum

### Maven

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
docker build -t bist-advisor .               # Çok aşamalı imaj oluştur
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



## Mimari

```
org.mesutormanli.bistadvisor
├── advisor/
│   └── DailyAdvisor.java                  # Günlük öneri motoru
├── cli/
│   └── AdvisorCommands.java               # CLI komutları (init/run/confirm/status/train)
├── config/
│   ├── AdvisorMode.java                   # Yatırım modu enum (CONSERVATIVE/BALANCED/AGGRESSIVE)
│   ├── AppConfig.java                     # Uygulama konfigürasyonu (cache/state/model varsayılanı)
│   └── ModelType.java                     # ML model tipi enum (RANDOM_FOREST/SVM/KNN)
├── data/
│   ├── BistIndices.java                   # BIST endeks-hisse eşleştirme (.properties)
│   ├── CacheStore.java                    # Fiyat serisi önbelleği (CSV dosya)
│   ├── PriceScraper.java                  # Yahoo Finance OHLCV çekici (query2.finance.yahoo.com)
│   └── YahooFundamentalsScraper.java      # Yahoo Finance temel veri çekici (quoteSummary + crumb)
├── features/
│   ├── FeatureVector.java                 # 11 boyutlu özellik vektörü (teknik + temel)
│   └── TechnicalFeatures.java             # Teknik göstergeler (RSI/SMA/MACD/volatilite/hacim)
├── model/
│   ├── FeatureFrame.java                  # Özellik matrisi + etiket dönüştürücü
│   ├── KnnStrategy.java                   # KNN sınıflandırıcı (SMILE)
│   ├── Labeler.java                       # Getiri bazlı etiketleyici (AL/SAT/TUT)
│   ├── ModelStrategy.java                 # ML strateji arayüzü
│   ├── ModelStrategyFactory.java          # Strateji fabrikası (tip -> strateji)
│   ├── ModelTrainer.java                  # Model eğitimi + bellek-içi önbellek
│   ├── RandomForestStrategy.java          # RandomForest one-vs-rest (SMILE)
│   └── SvmStrategy.java                   # SVM one-vs-rest (SMILE Gaussian kernel)
├── portfolio/
│   ├── PortfolioService.java              # Portföy servisi (state.yaml okuma/yazma)
│   ├── PortfolioState.java                # Portföy durumu (bütçe/mod/model/pozisyonlar)
│   └── Position.java                      # Pozisyon modeli (sembol/lot/maliyet)
├── web/
│   ├── AdvisorController.java             # REST API (/api/*)
│   └── StaticPageConfig.java              # Statik dosya sunumu (index.html)
└── BistAdvisorApplication.java            # Spring Boot giriş noktası
```

### Temel Bileşenler

| Bileşen | Açıklama |
|---|---|
| `DailyAdvisor` | Günlük AL/SAT/TUT önerilerini üretir, portföy ve ML modelini birleştirir |
| `AdvisorCommands` | CLI komut arayüzü: `init`, `run`, `confirm`, `status`, `train` |
| `AdvisorMode` | 3 yatırım modu: TEMKİNLİ (%25 risk), DENGELİ (%50), AGRESİF (%75) |
| `PriceScraper` | Yahoo Finance'den 1 yıllık günlük OHLCV serisi çeker |
| `YahooFundamentalsScraper` | Yahoo Finance'den F/K, PD/DD, temettü verimi, ROE, büyüme çeker |
| `BistIndices` | BIST-30, BIST-100, BIST-50 ve sektör endekslerini tanımlar |
| `CacheStore` | Çekilen fiyat serilerini `cache/` dizininde CSV olarak saklar |
| `FeatureVector` | 11 özellik: RSI, SMA-20/50 oranı, MACD, volatilite, hacim, F/K, PD/DD, temettü, büyüme, ROE |
| `ModelStrategy` | ML model arayüzü: `train(features, labels)`, `predict(features)` → {sınıf, skor} |
| `RandomForestStrategy` | One-vs-rest RandomForest (3 ayrı binary RF) |
| `SvmStrategy` | One-vs-rest SVM (Gaussian kernel) |
| `KnnStrategy` | k-NN sınıflandırıcı (k=5) |
| `Labeler` | N günlük getiriye göre: >%5 → AL, <-%5 → SAT, arada → TUT |
| `ModelTrainer` | Canlı veriyle eğitim, EnumMap ile bellek-içi önbellek |
| `PortfolioService` | state.yaml okuma/yazma, portföy kısıtları (maks 5 pozisyon) |
| `AdvisorController` | REST API: portfolio CRUD, analiz, onay, konfigürasyon |
| `BistAdvisorApplication` | Web modu (args yok) veya CLI modu (args var) |

### Bağımlılıklar

| Bağımlılık | Sürüm | Amaç |
|---|---|---|
| Spring Boot Web | 4.0.0 | REST/MVC çerçevesi |
| Spring Shell | 4.0.2 | CLI komut arayüzü |
| SMILE Core | 6.2.3 | ML algoritmaları (RF/SVM/KNN) |
| Jackson YAML | - | state.yaml okuma/yazma |
| Spring Boot Test | - | Test çerçevesi |
| JUnit Jupiter | - | Birim testler |

---

## Bileşenler

### DailyAdvisor

Günlük öneri motoru. Portföydeki pozisyonları değerlendirir ve yeni alım fırsatlarını tespit eder.

| Metod | Açıklama |
|---|---|
| `analyze()` | Tüm portföy + yeni alım önerilerini üretir |
| `currentPrices()` | Portföydeki sembollerin güncel kapanış fiyatlarını döndürür |

### AdvisorController

REST API kontrolcüsü (`/api/*`).

| Yöntem | Uç Nokta | Açıklama | HTTP Durumu |
|---|---|---|---|
| `GET` | `/api/config` | Konfigürasyon (modlar/modeller/endeksler) | 200 |
| `GET` | `/api/portfolio` | Portföy durumu | 200 |
| `GET` | `/api/portfolio-view` | Portföy + güncel fiyatlar | 200 |
| `POST` | `/api/portfolio` | Portföyü kaydet | 200 |
| `POST` | `/api/analyze` | Günlük analiz çalıştır | 200 |
| `POST` | `/api/confirm` | İşlemleri onayla | 200 |
| `GET` | `/api/pending` | Bekleyen AL/SAT önerileri | 200 |

### AdvisorMode

3 yatırım modu:

| Mod | Risk % | Al Eşik | Stop Loss | Sat Skor Eşik |
|---|---|---|---|---|
| TEMKİNLİ (CONSERVATIVE) | 25% | 0.75 | 10% | 0.30 |
| DENGELİ (BALANCED) | 50% | 0.60 | 15% | 0.25 |
| AGRESİF (AGGRESSIVE) | 75% | 0.50 | 25% | 0.20 |

### PriceScraper

Yahoo Finance `query2.finance.yahoo.com/v8/finance/chart/` üzerinden günlük OHLCV verisi çeker. Rate-limit (429) için katlanarak backoff ile 4 kez yeniden dener. Her satır formatı: `tarih,acilis,yuksek,dusuk,kapanis,hacim`.

### YahooFundamentalsScraper

Yahoo Finance `quoteSummary` API'sinden temel veri çeker. Crumb + cookie oturum yönetimi ile çalışır. Çekilen alanlar: F/K (trailingPE/forwardPE), PD/DD, temettü verimi, ROE, kâr büyümesi.

### FeatureVector

11 boyutlu özellik vektörü. Min-max normalizasyon uygulanır (0..1).

| # | Özellik | Kaynak |
|---|---|---|
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

Getiri bazlı etiketleme: N=20 gün sonrası getiri >%5 → AL (0), <-%5 → SAT (1), arada → TUT (2).

---

## Kullanım Örnekleri

### Web Modu (Varsayılan)

```bash
java -jar target/bist-advisor-0.1.0.jar
```
Tarayıcıda `http://localhost:8080` açılır. Tek sayfa arayüz:

1. **Mevcut Portföy**: Pozisyon ekleme/çıkarma, bütçe güncelleme
2. **Günlük Analiz**: Endeks/mod/model seçimi, analiz çalıştırma
3. **İşlem Onayı**: AL/SAT önerilerini onaylama (lot/fiyat düzenlenebilir)

### CLI Modu

```bash
# Portföy başlatma
java -jar target/bist-advisor-0.1.0.jar init --budget=50000 --mode=BALANCED --model=RANDOM_FOREST --pos=THYAO:100:240,ASELS:50:351

# Günlük analiz
java -jar target/bist-advisor-0.1.0.jar run

# İşlem onaylama
java -jar target/bist-advisor-0.1.0.jar confirm "THYAO,SAT,50,245.5" "AKBNK,AL,200,185.0"

# Portföy durumu
java -jar target/bist-advisor-0.1.0.jar status

# Model eğitimi (canlı veriyle)
java -jar target/bist-advisor-0.1.0.jar train
```

---

## API Uç Noktaları

Tüm API uç noktaları `/api` altında sunulur.

| Uç Nokta | Yöntem | Açıklama |
|---|---|---|
| `/api/config` | GET | Desteklenen modlar/modeller/endeksler + seçili değerler |
| `/api/portfolio` | GET | Mevcut portföy durumu |
| `/api/portfolio` | POST | Portföyü güncelle (bütçe/mod/model/endeks/pozisyonlar) |
| `/api/portfolio-view` | GET | Portföy + güncel fiyatlar + toplam değerler |
| `/api/analyze` | POST | Günlük analizi çalıştır (portföy önerileri + alım sinyalleri) |
| `/api/confirm` | POST | İşlemleri onayla (gönderilen işlemleri portföye işle) |
| `/api/pending` | GET | Bekleyen AL/SAT önerileri (onay ekranı için) |

### Analiz Yanıt Yapısı

```json
{
  "holdings": [
    {"index": 1, "symbol": "THYAO", "action": "SAT", "lots": 100, "price": 245.5, "score": 0.0, "note": "+2.30% | skor=0.35"}
  ],
  "buys": [
    {"index": 2, "symbol": "AKBNK", "action": "AL", "lots": 200, "price": 185.0, "score": 0.72, "note": "skor=0.72"}
  ],
  "availableCash": 12500.0,
  "positionCount": 2,
  "maxPositions": 5,
  "buySlots": 2
}
```

---

## Testler

### Test Sınıfları

| Test Sınıfı | Ne Test Eder |
|---|---|
| `BistAdvisorTest` | Teknik göstergeler, etiketleyici, Yahoo JSON ayrıştırma |

### Test Çalıştırma

```bash
# Tüm testler
mvn test

# Tek test sınıfı
mvn test -Dtest=BistAdvisorTest
```

### Test İçeriği

- `technicalFeaturesFromSeries()`: RSI (0-100) ve volatilite (≥0) doğrulaması
- `labelerAssignsClasses()`: %100 yükselişte AL etiketi atanması
- `priceScraperParsesYahooFixture()`: Yahoo JSON'ından doğru parse etme

---

## Hata Yönetimi

| Senaryo | HTTP Durumu |
|---|---|
| Başarılı analiz | 200 OK |
| Kaynak bulunamadı | 404 Not Found (portföy boşsa) |
| Geçersiz istek | 400 Bad Request |
| Yahoo API hatası | Sessiz atlanır, demo veriye düşülür |
| state.yaml okunamaz | Boş portföy ile başlatılır |

Servis katmanı hataları loglanır, kullanıcıya demo veri veya boş yanıt döndürülür.

---

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE.txt](LICENSE.txt) for details.

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
