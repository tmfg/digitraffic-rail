# SIRI-SX

*Tämän dokumentin kirjoittamisessa on käytetty AI:ta*

*Lähde: [docs/SIRI-SX.md](https://github.com/tmfg/digitraffic-rail-private/blob/master/docs/SIRI-SX.md) (GitHub). Päivitä tämä sivu, kun lähde muuttuu.*

# SIRI-SX — Situation Exchange

**SIRI-SX** (Situation Exchange) julkaisee tekstimuotoisia häiriö- ja matkustajatiedotteita: samoja viestejä, jotka näytetään asemien ja junien näytöillä ja kuulutetaan. Yleiskuva on sivulla SIRI.

- Rajapinta: `GET /api/v1/siri/sx` (`application/xml`)
- Profiili: Nordic SIRI-SX v1.1 (SIRI 2.0 -skeema)
- GTFS-Realtime-vastine: `Alert`
- Tila: tuotannossa

## 1. Miten SIRI-SX tuotetaan

### 1.1 Datalähde

Lähteenä ovat **RAMI-viestit**. Viestejä on kahta lajia:

- **Asemaviesti** (`SCHEDULED_MESSAGE`, tunnus alkaa `SHM`): kohteena yksi tai useampi asema.
- **Junaviesti** (`MONITORED_JOURNEY_SCHEDULED_MESSAGE`, tunnus alkaa `MVM`): kohteena tietty juna (junanumero + lähtöpäivä).

Vain kunkin viestin uusin versio otetaan huomioon. Viestillä on ruututeksti (fi/sv/en) ja/tai puhuttu teksti. Mikä tahansa kieli voi puuttua (noin 15 %:lla viesteistä on vain puhuttu teksti).

Situaatiot luetaan lähteestä joka kierroksella ja koko tiedosto rakennetaan niistä. Omaa situaatiotaulua ei ole. `SituationNumber` on `FTR:SituationNumber:<RAMI-viestin tunnus>`. Tunnus on julkinen eikä saa muuttua julkaisun jälkeen.

Asemat ratkaistaan ketjulla: RAMI:n lyhytkoodi (esim. `HKI`) → asema → UIC-koodi → PETI-pysäkki (UIC + 1 000 000). Laituria RAMI ei kerro, joten viitataan koko pysäkkiin (`StopPlace`).

### 1.2 Generointi

- Päivittäjä generoi koko dokumentin kerran 60 sekunnissa (`updater.siri.sx.fixed-rate-ms`).
- XML validoidaan SIRI 2.0 -skeemaa vasten. Virheellistä dokumenttia ei tallenneta.
- Tulos on deterministinen (viestit tunnuksen mukaan, kielet kiinteässä järjestyksessä, ei kellonaikoja viestien sisällä). Jos sisältö on sama kuin edellisessä tallennetussa tiedostossa, uutta tiedostoa ei tallenneta vaan päivitetään vain edellisen aikaleima.

### 1.3 Säännöt

- **Milloin viesti julkaistaan.** Viesti julkaistaan heti, kun se on luotu, myös silloin kun sen voimassaolo ei ole vielä alkanut. Alkamisaika on `ValidityPeriod`-kentässä, joten dokumentin lukija päättää, milloin viesti näytetään. Viesti pysyy dokumentissa, kunnes se päättyy (`end_validity`). RAMI-viestillä on aina päättymisaika (`end_validity` on `NOT NULL`), joten avoimia viestejä ei esiinny.
- **Poistettu viesti.** Viesti, jolla `deleted` on asetettu, julkaistaan tilassa `closed` 5 tunnin ajan poistohetkestä (profiilin vaatimus), minkä jälkeen se jätetään pois. Sama pätee, kun viesti poistettiin ennen voimassaolon alkua: silloin suljettu jakso alkaa poistohetkestä. Viestiä, joka poistettiin päättymishetkellä tai sen jälkeen, ei julkaista.
- **Teksti.** `Summary` on ruututeksti, enintään 160 merkkiä (profiilin raja). Pidempi teksti katkaistaan viimeisen kokonaisen sanan jälkeen ja perään lisätään `...`. Sanaa ei koskaan katkaista. `Description` on koko teksti, ja se annetaan vain kielelle, jonka `Summary` katkaistiin.
- **Kielet.** fi, sv ja en, `xml:lang` isoilla kirjaimilla (`FI`, `SV`, `EN`), koska SIRI 2.0 -skeema hyväksyy vain isot. Kieli, jolla ei ole tekstiä, jätetään pois. Jos ruututekstiä ei ole, käytetään puhuttua tekstiä. Jos kumpaakaan ei ole, viesti pudotetaan.
- **Kohde.** Asemaviesti → `StopPlaces` (löydetyt PETI-pysäkit). Junaviesti → `VehicleJourneys` (julkaistu NeTEx-vuoro, linja ja liikennöitsijä kuten ET:ssä). Viesti pudotetaan ja lasketaan lokiin, jos sille ei löydy yhtään pysäkkiä tai jos juna ei ole julkaistuissa vuoroissa.
- **`ReportType`** on aina `general`. RAMI:ssa ei ole vastaavaa kenttää, eikä tekstejä voi luotettavasti jakaa häiriöihin ja yleisiin tiedotteisiin, joten arvausta ei tehdä.
- **Ei julkaista.** Junaviestit, jotka alkavat sanoilla "Seuraava pysähdys", ovat pelkkää näyttötietoa eivätkä situaatioita.

### 1.4 Junaviestit ja NeTEx-matkaviitteet

Junaviesti tarvitsee tallennetun matkaviitteen julkaistusta NeTEx-datasta (`FramedVehicleJourneyRef`). Ilman sitä viesti pudotetaan (`no_journey`).

- Matkaviitteet tallennetaan SIRI:tä varten päiville tänään−2 … tänään+14 (`updater.netex.persist-journeys.lookback-days=2`, `lookahead-days=14`; vanhat versiot säilytetään 7 päivää). Pisin RAMI-viestin ennakko historiassa on 10 päivää, joten 14 päivää kattaa ne varmuudella.
- NeTEx-paketin XML kattaa noin 423 päivää, eikä se muutu tästä.
- Viesti kauempana tulevaisuudessa olevalle lähtöpäivälle ei julkaista vielä. Se ilmestyy sen NeTEx-ajon jälkeen, joka tallentaa kyseisen päivän. Tämä on normaalia eikä virhe.
- Asemaviestit julkaistaan heti luomisen jälkeen lähtöpäivästä riippumatta.
- Matkaviitteet luetaan vain jos junaviestejä on.

## 2. Virhetilanteet

| Tilanne | Mitä tapahtuu |
| --- | --- |
| PETI-pysäkit eivät ole saatavilla | Kierros epäonnistuu, mitään uutta ei tallenneta. Edellinen tiedosto pysyy tarjolla |
| Julkaistut NeTEx-matkat puuttuvat (`MISSING`), junaviestin lähtöpäivä on tänään tai huomenna eikä sille ole matkoja (`EMPTY`) tai uusin matka on yli 30 tuntia vanha (`STALE`) | Junaviestit pudotetaan, asemaviestit julkaistaan. Kierros kirjataan `partial`-tilaan (`rail.siri.sx.journeys_unavailable`) |
| Junaviestin juna ei ole julkaistuissa matkoissa (esim. lähtöpäivä on tallennetun ikkunan ulkopuolella) | Vain kyseinen viesti pudotetaan (`no_journey`), muut julkaistaan |
| Asemaviestin asemalle ei löydy PETI-pysäkkiä (esim. linjan vaihde `KTN`, `OLLI`) | Asema jätetään pois ja lasketaan lokiin. Jos yhtään pysäkkiä ei löydy, viesti pudotetaan |
| Tuottaja ei saa validia XML:ää | Tiedostoa ei tallenneta |

Huomaa: NeTEx-matkojen puuttuessa dokumentti pysyy tuoreena (`x-is-fresh=true`), joten puuttuvat junaviestit näkyvät vain lokissa ja `SIRI_SX_TRAINS`-kohdassa rajapinnassa `/api/v1/last-updated`.

## 3. Käyttö

- Rajapinta: [rata.digitraffic.fi/api/v1/siri/sx](https://rata.digitraffic.fi/api/v1/siri/sx) (beta: [rata-beta.digitraffic.fi](https://rata-beta.digitraffic.fi/api/v1/siri/sx)). OpenAPI: [openapi.json](https://rata.digitraffic.fi/swagger/openapi.json).
- Jokainen vastaus kantaa headerit `x-is-fresh` (alle 5 minuuttia vanha), `x-timestamp` (milloin data viimeksi tarkistettiin) ja sisällön pituuden. Vastauksen saa välimuistittaa 30 sekuntia.
- Asetukset: `updater.siri.sx.enabled`, `avoindataserver.siri.sx.enabled`, `updater.siri.sx.fixed-rate-ms` (oletus 60000).

## 4. Kentät

Tila: ✅ mukana, ❌ ei toteutettu (ei dataa tai ei tarpeen). Kardinaliteetti on profiilista (`1:1` pakollinen, `0:1` valinnainen, `1:*` yksi tai useampi).

### 4.1 Kuori

Rakenne: `Siri` › `ServiceDelivery` (yhteinen ET:n ja VM:n kanssa) › `SituationExchangeDelivery` › `Situations` › `PtSituationElement` (yksi per viesti).

| Kenttä | Kard. | Tila | Arvo |
| --- | --- | --- | --- |
| `ServiceDelivery/ResponseTimestamp` | 1:1 | ✅ | Hetki, jolloin sisältö viimeksi muuttui |
| `ServiceDelivery/ProducerRef` | 1:1 | ✅ | `FTR` |
| `SituationExchangeDelivery/ResponseTimestamp` | 1:1 | ✅ | Hetki, jolloin sisältö viimeksi muuttui |
| `SituationExchangeDelivery/@version` | 0:1 | ✅ | Toimituksen SIRI-versio |
| `SituationExchangeDelivery/Situations/PtSituationElement` | 1:* | ✅ | Yksi per RAMI-viesti |

`ResponseTimestamp` muuttuu vain, kun situaatiot muuttuvat. Header `x-timestamp` kertoo, milloin data tarkistettiin viimeksi.

### 4.2 Viesti — `PtSituationElement`

| Kenttä | Kard. | Tila | Arvo / syy |
| --- | --- | --- | --- |
| `SituationNumber` | 1:1 | ✅ | `FTR:SituationNumber:<RAMI-viestin tunnus>` |
| `Version` | 0:1 | ✅ | RAMI:n `version` |
| `CreationTime` | 1:1 | ✅ | Ensimmäisen version luontiaika |
| `VersionedAtTime` | 0:1 | ✅ | Uusimman version luontiaika |
| `ParticipantRef` | 1:1 | ✅ | `FTR` |
| `Source/SourceType` | 1:1 | ✅ | `directReport` (formaatti vaatii, ei käytössä) |
| `Progress` | 1:1 | ✅ | `closed`, jos viesti on poistettu, muuten `open` |
| `ValidityPeriod` | 1:* | ✅ | `start_validity` – `end_validity`. Poistettu viesti päättyy 5 tuntia poistohetken jälkeen; jos poisto tapahtui ennen alkua, alku on poistohetki |
| `UndefinedReason` | 1:1 | ✅ | Aina tyhjä (formaatti vaatii, ei käytössä) |
| `ReportType` | 1:1 | ✅ | Aina `general` |
| `Summary` | 1:* | ✅ | Ruututeksti katkaistuna 160 merkkiin, yksi per kieli |
| `Description` | 0:* | ✅ | Koko teksti, vain kielelle, jonka `Summary` katkaistiin |
| `Affects` | 1:1 | ✅ | Ks. 4.3 |
| `Severity` | 0:1 | ❌ | Ei dataa. Oletus `normal` |
| `Priority` | 0:1 | ❌ | Ei dataa |
| `Planned` | 0:1 | ❌ | Ei dataa (RAMI ei kerro onko häiriö suunniteltu) |
| `Advice` | 0:* | ❌ | RAMI-tekstit eivät erota ohjetta viestistä |
| `InfoLinks` | 0:1 | ❌ | Linkit ovat vain tekstin sisällä |

### 4.3 Viestin kohde — `Affects`

Jokaiselle viestille käytetään täsmälleen yhtä vaihtoehtoa.

| Kenttä | Tila | Käyttö |
| --- | --- | --- |
| `StopPlaces/AffectedStopPlace/StopPlaceRef` | ✅ | Asemaviestit. Jokaisen viestin aseman PETI-pysäkki |
| `VehicleJourneys/AffectedVehicleJourney` | ✅ | Junaviestit. `FramedVehicleJourneyRef` (päivä + vuoron tunniste), `Operator`, `LineRef` kuten ET:ssä ja vaadittu tyhjä `Route` |
| `Networks` | ❌ | RAMI ei kohdista viestiä linjaan tai koko verkkoon |
| `StopPoints/AffectedStopPoint` | ❌ | Laituritaso. RAMI ei kerro laituria |
| `VehicleJourneys/.../Route/RouteRef` | ❌ | Tarvitaan vain, kun viesti koskee osaa reitistä. Reittitunnusta ei tallenneta julkaistujen matkojen mukana |
| `VehicleJourneys/.../Route/StopPoints`, `Sections` | ❌ | Junaviestin asemalista kertoo missä viesti näytetään, ei mitä osuutta se koskee |
| `AccessibilityAssessment`, `AffectedComponents` | ❌ | Ei esteettömyys- tai asemaosatietoa |

## 5. Tunnetut rajoitukset ja avoimet asiat

- Vain RAMI on lähteenä. Muut tutkitut lähteet (viivästymissyyt, ratatyöilmoitukset, liikennerajoitukset) eivät sovellu tai päällekkäistyvät RAMI:n kanssa. Uusi lähde voidaan lisätä myöhemmin.
- `ReportType` on aina `general`.
- Junaviestit kauas tulevaisuuteen (yli 14 päivää) eivät näy ennen kuin matkaviitteet on tallennettu.
- Linjan vaihteille (esim. `KTN`, `OLLI`) ei todennäköisesti ole PETI-pysäkkiä, joten niihin kohdistuvat asemaviestit pudotetaan.
- SIRI-vastausten muotoja ei ole vielä dokumentoitu [digitraffic.fi](https://www.digitraffic.fi/):n SIRI-sivulla.

## 6. Seuranta

- Lokirivi per kierros: `event=rail.siri.generation`, `rail.siri.service=sx`, `outcome=success|partial|error`.
- `partial`, kun yksikin viesti pudotetaan (esim. ei pysäkkiä tai matkaa) tai NeTEx-matkat eivät ole saatavilla.
- `SIRI_SX_STATIONS` ja `SIRI_SX_TRAINS` päivittyvät rajapinnassa `/api/v1/last-updated`.

## 7. Viitteet

- [Nordic SIRI-SX](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370605/SIRI-SX)
- [Nordic SIRI -profiili — General information SIRI](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373/General+information+SIRI)
