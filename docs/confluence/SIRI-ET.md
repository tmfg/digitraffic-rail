# SIRI-ET

*Tämän dokumentin kirjoittamisessa on käytetty AI:ta*

*Lähde: [docs/SIRI-ET.md](https://github.com/tmfg/digitraffic-rail-private/blob/master/docs/SIRI-ET.md) (GitHub). Päivitä tämä sivu, kun lähde muuttuu.*

# SIRI-ET — Estimated Timetable

**SIRI-ET** (Estimated Timetable) kuvaa poikkeamia suunnitelmasta kuluvan liikennöintipäivän aikana: myöhästymiset, peruutukset, osittaiset peruutukset ja laiturimuutokset. Se on julkaistun NeTEx-aikataulun reaaliaikainen vastine. Yleiskuva on sivulla SIRI.

- Rajapinta: `GET /api/v1/siri/et` (`application/xml`)
- Profiili: Nordic SIRI-ET (SIRI 2.0 -skeema)

## 1. Miten SIRI-ET tuotetaan

### 1.1 Kuinka usein dokumentti generoidaan

- Päivittäjä generoi koko SIRI-ET-dokumentin uudelleen kiinteällä aikataululla, kerran 60 sekunnissa (`updater.siri.et.fixed-rate-ms`).
- Generointi ja REST-API ovat omien asetustensa takana:
    - `updater.siri.et.enabled`
    - `avoindataserver.siri.et.enabled`
- Generoitu XML validoidaan SIRI 2.0 -skeemaa vasten. Virheellistä dokumenttia ei tallenneta.

### 1.2 Mitkä junat dokumenttiin haetaan

Dokumenttiin haetaan täsmälleen ne junat, joihin julkaistu NeTEx viittaa liikennöintipäivän ikkunassa (`[eilen, tänään]`, ks. 1.4). SIRI-ET lukee ensin julkaistut vuoroviittaukset tietokannasta (ks. 1.3) ja hakee sitten juuri näiden junien reaaliaikaiset rivit. Juna, jota ei ole julkaistu NeTEx:issä (esim. ei-matkustajajuna), ohitetaan. Se on normaalia.

### 1.3 Kunkin junan sitominen julkaistuun NeTEx-aikatauluun ja PETI-pysäkkeihin

Junat sidotaan julkaistuun NeTEx-aikatauluun junanumeron ja lähtöpäivän perusteella. Sidonta luetaan tietokannasta: NeTEx-generointi tallentaa jokaiselle `(junanumero, liikennöintipäivä)`-parille SIRI:n tarvitsemat viittaukset (vuoron tunniste, linja, liikennöitsijä, journey pattern ja suunnitellut laiturit). SIRI-ET lukee nämä rivit takaisin liikennöintipäivän ikkunalle (ks. 1.4). Näin SIRI-ET käyttää täsmälleen samoja tunnisteita kuin julkaistu NeTEx. Jos junan aikataulusta on osa pysähdyksistä peruttu (osaväliperuminen) tietylle päivälle, NeTEx sisältää sille päivälle erillisen lyhennetyn vuoron ilman peruttuja pysähdyksiä, ja SIRI-ET viittaa tuon päivän osalta siihen.

Pysäkit ja laiturit haetaan PETI-pysäkkirekisteristä (asema + laituri → `FSR:Quay`).

- **Riippuvuus julkaistuun NeTEx:iin:** jos julkaistuja vuoroja ei ole saatavilla, ikkunalle ei ole yhtään, tai uusin niistä on yli 30 tuntia vanha, generointikierros epäonnistuu eikä tallenna mitään. Viimeisin toimiva dokumentti pysyy tarjolla (`x-is-fresh` muuttuu epätodeksi 5 minuutin kuluttua).
- **Riippuvuus PETI-pysäkkitietoihin:** jos PETI-tiedot eivät ole saatavilla (tyhjä snapshot), generointikierros epäonnistuu samoin. Viimeisin toimiva dokumentti jää tarjolle.

### 1.4 Liikennöintipäivä

SIRI-ET kuvaa poikkeamia saman liikennöintipäivän sisällä. Nordic-profiili toteaa, että liikennöintipäivä "voi poiketa kalenteripäivästä". Tulkitsemme liikennöintipäivän ikkunan sisältävän sekä kuluvan päivän että eilisen.

- Eilinen otetaan mukaan, koska osa raidevuoroista liikennöi keskiyön yli, joten juna, joka on vielä liikkeellä "nyt", on lähtenyt joko tänään tai eilen.
- Eiliselle päivätty juna säilytetään vain niin kauan kuin se on vielä liikkeellä: se pudotaan pois, kun sen viimeisellä kaupallisella pysähdyksellä on toteutunut aika, tai kun tämän pysähdyksen arvioitu aika on yli 15 minuuttia menneisyydessä (vanhentunut data). Kuluvalle päivälle päivätyt junat säilytetään aina.

### 1.5 Yhden junan tulkinta (ydinsäännöt)

Kullekin reaaliaikaiselle junalle päätellään, mitä siitä julkaistaan, seuraavasti:

1. **Sido suunniteltuun vuoroon.** Juna (junanumero + lähtöpäivä) sovitetaan julkaistuun NeTEx-vuoroon. Tästä saadaan vuoron tunniste, linja, liikennöitsijä ja journey pattern. Jos junaa ei löydy, se ohitetaan (`unresolved_journey`).
2. **Rakenna pysähdyssekvenssi.** Junan aikataulurivit järjestetään ajan mukaan ja yhdistetään pysähdyksiksi: lähtöasema (vain lähtö), väliasemat (saapuminen + lähtö) ja pääteasema (vain saapuminen).
3. **Pudota loppuun ajetut edellisen päivän vuorot.** Ks. 1.4.
4. **Säilytä vain kaupalliset pysähdykset.** Vain pysähdykset, joissa matkustaja voi nousta kyytiin tai poistua, säilytetään. Käytetään samaa kaupallisen pysähdyksen sääntöä kuin NeTEx-aikataulussa, jotta reaaliaikainen ja suunniteltu pysähdyssekvenssi vastaavat toisiaan.
5. **Ratkaise kullekin pysähdykselle laituritunniste.** Jokainen kaupallinen pysähdys muunnetaan PETI `FSR:Quay` -tunnisteeksi. Käytetään reaaliaikaista laituria. Jos se on tuntematon (`unknownTrack` tai tyhjä), käytetään NeTEx:in suunniteltua laituria (jos asema käydään kahdesti, oikean käynnin suunniteltua laituria). Jos yhdenkin pysähdyksen laituria ei voida ratkaista, koko vuoro ohitetaan, koska profiili vaatii täydellisen pysäkkisekvenssin. Ohitus kirjataan syyllä `unresolved_stop.no_stop` (asemalle ei PETI-pysäkkiä) tai `unresolved_stop.no_quay` (pysäkki löytyy, laituria ei).
6. **Päätä kullekin pysähdykselle, onko siinä jo pysähdytty vai onko se tulevaisuudessa.** Etsitään uusin pysähdys, jolla on toteutunut aika. Se ja kaikki sitä edeltävät pysähdykset ovat menneisyyttä (`RecordedCall`), loput tulevia (`EstimatedCall`, joka kantaa arvioitua aikaa). Jos menneisyyden pysähdyksellä ei ole omaa toteutunutta aikaa, se on **"missed"**-pysähdys: se pysyy `RecordedCall`ina, mutta kantaa toteutuneen ajan sijaan arvion (`Expected…Time`). `RecordedCall`it ovat siis aina sekvenssin alku ja `EstimatedCall`it sen loppu.
7. **Johda kunkin ajan poikkeamastatus.** Arvioitu/toteutunut aika vs. suunniteltu aika: alle 60 sekunnin ero on `onTime`, vähintään 60 sekuntia myöhässä `delayed`, vähintään 60 sekuntia etuajassa `early`. Peruttu pysähdys on `cancelled`. `ArrivalStatus`/`DepartureStatus` lähetetään vain **tulevilla** pysähdyksillä (`EstimatedCall`). Jo palvelluilla pysähdyksillä (`RecordedCall`) poikkeama näkyy suunnitellun ja toteutuneen ajan erotuksesta, koska SIRI 2.0 -profiili ei tue statusta `RecordedCall`illa (ks. 3.5).
8. **Käsittele peruutukset.** Koko vuoron peruutus merkitään vuorolle. Yksittäiset perutut pysähdykset merkitään pysähdyskohtaisesti. **Osittaisen peruutuksen** rajalla viimeisen vielä palveltavan pysähdyksen lähtö ennen peruttua osuutta merkitään peruutetuksi.
9. **Merkitse epävarmat ennusteet.** Kun lähdedata merkitsee viiveen tuntemattomaksi (`unknownDelay`, RAMI), tuleva pysähdys saa `PredictionInaccurate=true`.
10. **Lähtö ei ole ennen saapumista.** Saapumisen jälkeen tallennettu lähdön arvio voi hetken olla vanhempi kuin toteutunut saapuminen. Tällainen arvio julkaistaan saapumisaikana.
11. **Laiturimuutos.** Kun suunniteltu laituri on tiedossa, reaaliaikainen laituri on tiedossa ja ne eroavat, tulevalle pysähdykselle lisätään `StopAssignment` (`AimedQuayRef` = suunniteltu, `ExpectedQuayRef` = todellinen). Se lähetetään saapumispuolelle, tai lähtöpuolelle jos pysähdyksellä ei ole saapumista (lähtöasema).
12. **Merkitse onko vuoro monitoroitu.** Jos millä tahansa pysähdyksellä on arvio tai toteutunut aika, vuoro merkitään monitoroiduksi.
13. **Tallenna lähtö- ja määränpäänimet.** Ensimmäisen ja viimeisen kaupallisen pysähdyksen aseman nimet lisätään luettavuuden vuoksi.

Vuoron `RecordedAtTime` on hetki, jolloin vuoron oma data muuttui viimeksi: junan ja sen rivien uusin `modified`-aika (yksittäiset rivit muuttuvat itsenäisesti, esim. kun RAMI merkitsee tuntemattoman viiveen tai laiturin), korkeintaan generointihetki. Jos `modified`-aikaa ei ole, käytetään generointihetkeä. Vuorojen ympärillä oleva kehys ja toimitus pitävät generointihetken.

Tyhjä dokumentti on sallittu: jos yhtään junaa ei jää jäljelle, kirjoitetaan toimitus ilman kehystä, koska tyhjä `EstimatedJourneyVersionFrame` ei ole skeemavalidi.

### 1.6 Miten käyttäjät saavat dokumentin

- Uusin dokumentti tarjoillaan osoitteessa `GET /api/v1/siri/et` muodossa `application/xml`.
- Jokainen vastaus kantaa headerit: `x-is-fresh` (onko dokumentti alle 5 minuuttia vanha), `x-timestamp` (milloin se generoitiin) ja sisällön pituuden. Vastauksen saa välimuistittaa 30 sekuntia.

---

## 2. Kentät jotka sisällytämme, ja mistä tieto tulee

Tämä osio listaa **jokaisen SIRI-ET-kentän, jonka täytämme**, ryhmiteltynä profiilin rakenteiden mukaan, kunkin arvon lähteen kanssa. "Vakio" tarkoittaa, että lähetämme aina saman arvon. "Johdettu" tarkoittaa, että laskemme sen reaaliaikaisesta datasta. Ajat ovat Helsingin aikaa täysinä sekunteina.

### 2.1 Kuori ja toimitus

| Kenttä | Mistä tieto tulee |
| --- | --- |
| `Siri` version (attribuutti) | Vakio — `2.0` (Nordic-profiili perustuu SIRI 2.0 -skeemaan, emmekä lähetä 2.1-kenttiä) |
| `ServiceDelivery` › `ResponseTimestamp` | Generointihetki |
| `ServiceDelivery` › `ProducerRef` | Vakio — codespace `FTR` |
| `EstimatedTimetableDelivery` version (attribuutti) | Vakio — `2.0` |
| `EstimatedTimetableDelivery` › `ResponseTimestamp` | Generointihetki |
| `EstimatedJourneyVersionFrame` › `RecordedAtTime` | Generointihetki |

### 2.2 Per vuoro — `EstimatedVehicleJourney`

| Kenttä | Mistä tieto tulee |
| --- | --- |
| `RecordedAtTime` | Vuoron oman datan viimeisin muutosaika (ks. 1.5) |
| `LineRef` | Julkaistun vuoron linjatunniste, sama kuin NeTEx-aikataulussa |
| `DirectionRef` | Vakio — `0`. Nordic-profiili käskee käyttää nollaa, kun suunta ei ole tiedossa/käytössä |
| `FramedVehicleJourneyRef` › `DataFrameRef` | Junan lähtöpäivä |
| `FramedVehicleJourneyRef` › `DatedVehicleJourneyRef` | Julkaistun vuoron tunniste, sama kuin NeTEx-aikataulussa |
| `JourneyPatternRef` | Julkaistun vuoron NeTEx-`JourneyPattern`-tunniste, sama kuin NeTEx-aikataulussa. Jätetään pois, jos sitä ei ole tallennettu |
| `DataSource` | Vakio — codespace `FTR` |
| `VehicleMode` | Vakio — `rail` |
| `OperatorRef` | Julkaistun vuoron liikennöitsijä, sama kuin NeTEx-aikataulussa. Jätetään pois, jos sitä ei ole tallennettu |
| `Cancellation` | `true`, kun koko juna on peruttu, muuten ei kirjoiteta |
| `Monitored` | Johdettu — tosi, kun jollakin pysähdyksellä on reaaliaikainen arvio tai toteutunut aika |
| `OriginName` | Ensimmäisen kaupallisen pysähdyksen aseman nimi |
| `DestinationName` | Viimeisen kaupallisen pysähdyksen aseman nimi |
| `IsCompleteStopSequence` | Vakio — `true` (Nordic-profiili vaatii tämän) |
| `RecordedCalls` | Säiliö jo palvelluille pysähdyksille (ks. 2.3) |
| `EstimatedCalls` | Säiliö tuleville pysähdyksille (ks. 2.4) |

### 2.3 Per palveltu pysähdys — `RecordedCall`

| Kenttä | Mistä tieto tulee |
| --- | --- |
| `StopPointRef` | Pysähdyksen PETI `FSR:Quay` (laituri) -tunniste |
| `Order` | Juokseva järjestysnumero sekvenssissä, alkaen 1:stä |
| `StopPointName` | Aseman nimi |
| `Cancellation` | `true`, kun pysähdys on peruttu, muuten ei kirjoiteta |
| `AimedArrivalTime` | Suunniteltu saapumisaika. Ei lähtöasemalla |
| `ActualArrivalTime` | Toteutunut saapumisaika. Ei lähtöasemalla |
| `ExpectedArrivalTime` | Vain varasuunnitelmana, kun palvellulla pysähdyksellä ei ole toteutunutta saapumisaikaa ("missed") |
| `AimedDepartureTime` | Suunniteltu lähtöaika. Ei pääteasemalla |
| `ActualDepartureTime` | Toteutunut lähtöaika. Ei pääteasemalla |
| `ExpectedDepartureTime` | Vain varasuunnitelmana, kun palvellulla pysähdyksellä ei ole toteutunutta lähtöaikaa ("missed") |

### 2.4 Per tuleva pysähdys — `EstimatedCall`

| Kenttä | Mistä tieto tulee |
| --- | --- |
| `StopPointRef` | Pysähdyksen PETI `FSR:Quay` (laituri) -tunniste |
| `Order` | Juokseva järjestysnumero sekvenssissä |
| `StopPointName` | Aseman nimi |
| `Cancellation` | `true`, kun pysähdys on peruttu, muuten ei kirjoiteta |
| `PredictionInaccurate` | `true`, kun lähde merkitsee viiveen tuntemattomaksi (RAMI `unknownDelay`) |
| `RequestStop` | Vakio — `false`. Junia ei pysäytetä pyynnöstä |
| `AimedArrivalTime` | Suunniteltu saapumisaika. Ei lähtöasemalla |
| `ExpectedArrivalTime` | Arvioitu saapumisaika |
| `ArrivalStatus` | Johdettu — `onTime` / `delayed` / `early` / `cancelled` |
| `ArrivalBoardingActivity` | Johdettu — `alighting`, tai `noAlighting` kun peruttu |
| `ArrivalStopAssignment` | Laiturimuutos: `AimedQuayRef` (suunniteltu `FSR:Quay`) + `ExpectedQuayRef` (todellinen `FSR:Quay`). Lähetetään saapumispuolella. SIRI 2.0 sallii tämän `EstimatedCall`illa, mutta ei `RecordedCall`illa (ks. 3.5) |
| `AimedDepartureTime` | Suunniteltu lähtöaika. Ei pääteasemalla |
| `ExpectedDepartureTime` | Arvioitu lähtöaika |
| `DepartureStatus` | Johdettu kuten `ArrivalStatus` |
| `DepartureBoardingActivity` | Johdettu — `boarding`, tai `noBoarding` kun peruttu |
| `DepartureStopAssignment` | Laiturimuutos pysähdyksellä, jolla ei ole saapumista (lähtöasema) |

### 2.5 Vuoroviittaus — `FramedVehicleJourneyRef`

| Kenttä | Mistä tieto tulee |
| --- | --- |
| `DataFrameRef` | Junan lähtöpäivä |
| `DatedVehicleJourneyRef` | Julkaistun vuoron tunniste |

---

## 3. Kentät jotka jätämme pois, ja miksi

Tämä osio listaa kentät, jotka Nordic-profiili määrittää mutta joita emme täytä, ryhmiteltynä syyn mukaan. Yhdessä osion 2 kanssa tämä kattaa kaikki profiilin kentät.

### 3.1 Kentät joita käytetään vain lisävuoroihin (ei lähetetä)

Emme lähetä lisä-/korvausvuoroja ("extra"), koska Suomessa näitä ei ole matkustajapuolella. Seuraavat kentät ovat olemassa vain lisävuorojen kuvaamiseen, joten ne jätetään pois:

| Kenttä (kohteessa `EstimatedVehicleJourney`, ellei toisin mainita) | Miksi jätetty pois |
| --- | --- |
| `ExtraJourney` | Merkitsee lisä-/korvausvuoron |
| `EstimatedVehicleJourneyCode` | Uusi tunniste, jonka lisävuoro tarvitsisi |
| `RouteRef` | Vaaditaan vain lisävuoroille |
| `PublishedLineName` | Käytetään vain kun lisävuoro tuo uuden linjan |
| `GroupOfLinesRef` | Vaaditaan vain lisävuoroille |
| `ExternalLineRef` | Tunnistaa linjan, jonka lisävuoro korvaa |
| `VehicleJourneyRef` | Viittaa korvattavaan vuoroon |
| `ExtraCall` (kohteissa `RecordedCall` ja `EstimatedCall`) | Merkitsee ajon aikana suunnitelman ulkopuolelle lisätyn pysähdyksen |

### 3.2 Kentät joille ei ole datalähdettä

| Kenttä | Miksi jätetty pois |
| --- | --- |
| `VehicleRef` (kohteessa `EstimatedVehicleJourney`) | Reaaliaikadatassa ei ole junayksikkötunnistetta |
| `AdditionalVehicleJourneyRef` | Viittaa muihin vaikutettuihin vuoroihin. Tällaista linkkiä ei ole reaaliaikadatassa |
| `BlockRef` | Spesifikaation mukaan sisäinen (ei-julkinen) viittaus. Ei siis julkaista |
| `Occupancy` (kohteissa `EstimatedVehicleJourney`, `RecordedCall`, `EstimatedCall`) | Matkustajadataa ei ole saatavilla |
| `PublicContact` / `OperationsContact` | Ei vuorokohtaisia yhteystietoja |
| `DestinationDisplay` (kohteessa `EstimatedCall`) | Ei ajoneuvon määränpäätekstin lähdettä |
| `PredictionInaccurate` (kohteessa `EstimatedVehicleJourney`) | Lähdedata ei kerro, onko koko vuoron ennuste epävarma, joten emme aseta tätä vuorotasolla. Epävarmuus merkitään pysähdyskohtaisesti (`EstimatedCall`) `unknownDelay`-lipun perusteella |

### 3.3 Kentät jotka riippuvat SIRI-SX (häiriöt) -palvelusta

| Kenttä | Miksi jätetty pois |
| --- | --- |
| `SituationRef` (kohteissa `EstimatedVehicleJourney` ja `EstimatedCall`) | Linkittäisi vuoron tai pysähdyksen SIRI-SX-häiriötiedotteeseen. SIRI-SX viittaa vuoroon, mutta ET ei linkitä takaisin SX:ään |

### 3.4 Uudemmat SIRI 2.1 -relaatiopiirteet (profiilissa viimeistelemättä)

Seuraavat ovat SIRI 2.1 -piirteitä, joita profiili ei ole vielä viimeistellyt. Mitään niistä ei lähetetä:

| Kenttä / rakenne | Miksi jätetty pois |
| --- | --- |
| `JourneyRelations` (kohteessa `EstimatedVehicleJourney`) | Vuorojen väliset relaatiot |
| `DepartureFormationAssignment`, `RecordedDepartureOccupancy`, `RecordedDepartureCapacities` (kohteessa `RecordedCall`) | Ei kokoonpano-/kuormitus-/kapasiteettidataa |
| `ArrivalFormationAssignment`, `ExpectedDepartureOccupancy`, `ExpectedDepartureCapacities` (kohteessa `EstimatedCall`) | Sama |

### 3.5 Tilan ja laiturimuutoksen rajoitukset `RecordedCall`illa

`ArrivalStatus`, `DepartureStatus`, boarding activity -kentät ja `StopAssignment` ovat `RecordedCall`illa SIRI 2.1 -piirteitä, joten jo palvelluilla pysähdyksillä emme lähetä niitä. Laiturimuutos lähetetään vain tulevilla pysähdyksillä (ks. 2.4 ja Enturin [Changed platform -esimerkki](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370726/SIRI-ET+-+Changed+platform)).

---

## 4. Seuranta ja lokit

- Päivittäjä kirjoittaa jokaisesta kierroksesta yhden rivin: `event=rail.siri.generation`, `rail.siri.service=et`, `outcome=success|partial|error`.
- `partial`, kun yhtään vuoroa ei julkaistu vaikka junia saatiin, tai kun jokin pysähdys ei löytänyt PETI-laituria.
- Laskurit: saadut junat, julkaistut ja perutut vuorot, ohitussyyt (`unresolved_journey`, `unresolved_stop.*`, `completed_carryover`), pysähdykset yhteensä / recorded / estimated, PETI-osumaprosentti.
- NeTEx-lähde: `rail.siri.journey_source.dataset_version`, `rail.siri.journey_source.age` ja virhetilanteessa `rail.siri.journey_source.unavailable_reason` (`MISSING`, `EMPTY`, `STALE`, `PETI_EMPTY`).
- `SIRI_ET` päivittyy rajapinnassa `/api/v1/last-updated`.

## 5. Viitteet

- [Nordic SIRI -profiili — General information SIRI](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373/General+information+SIRI)
- [Nordic SIRI -profiili — SIRI-ET](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370392/SIRI-ET)
- [Enturin SIRI-esimerkit](https://github.com/entur/profile-norway-examples/tree/master/siri)
