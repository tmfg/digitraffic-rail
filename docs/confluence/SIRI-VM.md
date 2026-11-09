# SIRI-VM

*Tämän dokumentin kirjoittamisessa on käytetty AI:ta*

*Lähde: [docs/SIRI-VM.md](https://github.com/tmfg/digitraffic-rail-private/blob/master/docs/SIRI-VM.md) (GitHub). Päivitä tämä sivu, kun lähde muuttuu.*

# SIRI-VM — Vehicle Monitoring

**SIRI-VM** (Vehicle Monitoring) julkaisee jokaisen matkustajajunan senhetkisen sijainnin sekä sen viiveen ja pysäkin, jota lähestytään tai jossa seisotaan. Yleiskuva on sivulla SIRI.

- Rajapinta: `GET /api/v1/siri/vm` (`application/xml`)
- Profiili: Nordic SIRI-VM (SIRI 2.0 -skeema). Profiili odottaa VM:n kantavan sekä sijainnin että reaaliaikaisen viiveen.
- GTFS-Realtime-vastine: `VehiclePosition`

## 1. Miten SIRI-VM tuotetaan

### 1.1 Datalähteet

- **Sijainnit:** junien sijaintitiedot (GPS-piste ja nopeus), samat kuin GTFS-Realtime `VehiclePosition`. Jokaiselle matkustajajunalle käytetään uusinta sijaintia, joka on enintään 30 minuuttia vanha (sama ikkuna kuin GTFS-Realtimessa). Matkustajajunia ovat junanumerot alle 2000 ja 8000–9999 (väliaikainen sääntö).
- **Seuraava pysäkki ja viive:** yksi kysely lisää jokaiseen sijaintiin junan nykyisen tai tulevan kaupallisen pysäkin (asema, laituri, viive ja tuntematon-viive-lippu) sekä tiedon, onko juna pysäkillä.
- **Suunnitelma:** julkaistut NeTEx-matkat tietokannassa (vuoron tunniste, linja, liikennöitsijä, suunnitellut laiturit), samat kuin ET:ssä.
- **Pysäkit:** PETI-pysäkkirekisteri (`FSR:Quay`), samoin kuin ET:ssä.

ET ja VM käyttävät samoja ratkaisijaluokkia, joten ne antavat junalle aina saman linjan, vuoron ja laiturin tunnisteen.

### 1.2 Generointi

Päivittäjä generoi koko dokumentin uudelleen 60 sekunnin välein (`updater.siri.vm.fixed-rate-ms`), samoissa vaiheissa kuin ET:n:

1. **Valmistelu.** Luetaan julkaistut matkat liikennöintipäiville (eilinen ja tänään), tarkistetaan PETI ja haetaan matkustajajunien uusimmat sijainnit ja seuraavan pysäkin tiedot.
2. **Rakennus.** Jokainen sijainti muunnetaan pieneksi tietueeksi ja kirjoitetaan SIRI XML:ksi.
3. **Validointi.** XML validoidaan SIRI 2.0 -skeemaa vasten. Virheellistä dokumenttia ei tallenneta.
4. **Tallennus.** XML tallennetaan ja `SIRI_VM` päivittyy rajapinnassa `/api/v1/last-updated`.
5. **Loki.** Yksi rivi `event=rail.siri.generation`, `rail.siri.service=vm`.

### 1.3 Riippuvuudet

- **Julkaistut NeTEx-matkat.** Jos ne puuttuvat, liikennöintipäiville ei ole yhtään tai uusin on yli 30 tuntia vanha, kierros epäonnistuu eikä tallenna mitään. Viimeisin toimiva dokumentti pysyy tarjolla.
- **PETI-pysäkit.** Jos PETI-snapshot on tyhjä, kierros epäonnistuu samoin.

Profiili vaatii, että aikataulu on olemassa ennen sijaintien lähettämistä. Tämä taataan yllä olevilla ehdoilla sekä pudottamalla sijainti, jonka juna ei ole julkaistuissa matkoissa. PETI-snapshotin ikä vain kirjataan lokiin, se ei ole epäonnistumisen syy.

### 1.4 Säännöt yhdelle sijainnille

1. **Yksi `VehicleActivity` per juna**, uusimmasta sijainnista.
2. **Sido suunnitelmaan.** Juna (junanumero + lähtöpäivä) täytyy löytyä julkaistuista NeTEx-matkoista, muuten sijainti pudotetaan. Keksittyä matkaviitettä ei julkaista. Kierros on `partial` vain, jos koko dokumentti on tyhjä vaikka sijainteja saatiin.
3. **Seuraava pysäkki (`MonitoredCall`).** Pysäkki, jota juna lähestyy tai jossa se seisoo, ei koskaan juuri jätetty pysäkki. Se on ensimmäinen kaupallinen, ei-peruttu pysäkki, jolla ei ole toteutunutta aikaa ja jonka arvio on vielä tulevaisuudessa. Jos juna on asemalla ja sekä saapuminen että lähtö ovat vielä auki, lähtö voittaa, koska juna ei ole vielä lähtenyt.
4. **Seuraavan pysäkin laituri.** Käytetään reaaliaikaista laituria. Jos se on tuntematon (`unknownTrack`) tai tyhjä, käytetään NeTEx:in suunniteltua laituria. Jos sama asema käydään kahdesti, oikea käynti ratkaistaan käyntinumerolla. Jos laituria ei voida ratkaista PETI `FSR:Quay` -tunnisteeksi, `MonitoredCall` jätetään pois, mutta ajoneuvo julkaistaan silti.
5. **Juna päätepysäkillä.** Kun juna on saapunut viimeiselle pysäkilleen, tulevaa pysäkkiä ei ole. Tällöin saavutettu pääteasema julkaistaan `MonitoredCall`ina (`VehicleAtStop=true`), jotta juna ei yhtäkkiä menetä pysäkkiään.
6. **`VehicleAtStop`.** Tosi, kun seuraava pysäkki on lähtö ja sen saapuminen on tapahtunut tai sitä ei ole (lähtöasema). Epätosi, kun seuraava rivi on saapuminen tai saapuminen odottaa vielä. `VehicleLocationAtStop` kirjoitetaan vain kun `VehicleAtStop` on tosi, ja se on ajoneuvon oma viimeisin GPS-piste.
7. **Viive (`Delay`).** Seuraavan pysäkin arvio miinus suunniteltu aika täysinä sekunteina. Jos sitä ei tiedetä, `PT0S` (kenttä on profiilissa pakollinen).
8. **Epävarma viive (`InCongestion`).** Kun lähde merkitsee viiveen tuntemattomaksi (RAMI `unknownDelay`), ajoneuvo saa `InCongestion=true`. Profiilissa ei ole kenttää "arvio ei ole luotettava", ja tämä on lähin vastine. Tämä on päätös, ei oikea ruuhkasignaali. `Delay` kirjoitetaan silti normaalisti.
9. **Lähtö- ja määränpää.** Julkaistun matkan ensimmäinen ja viimeinen pysäkki suunniteltuine laitureineen. Vain asema, jota PETI ei tunne tai jolla ei ole laitureita PETI:ssä, julkaistaan ilman laituriviittausta.
10. **Nopeus.** Lähteen nopeus (km/h) muunnetaan m/s:ksi ja pyöristetään kokonaisluvuksi (skeema vaatii).
11. **Voimassaolo.** `ValidUntilTime` on `RecordedAtTime` + 5 minuuttia. Tallennusaika on GPS-pisteen aika, ei generointihetki.

## 2. Virhetilanteet

| Tilanne | Mitä tapahtuu |
| --- | --- |
| NeTEx-matkat puuttuvat tai ovat yli 30 h vanhoja | Kierros epäonnistuu, edellinen tiedosto pysyy tarjolla |
| PETI-snapshot on tyhjä | Kierros epäonnistuu samoin |
| Junaa ei ole julkaistuissa matkoissa | Sijainti pudotetaan |
| Seuraavan pysäkin laituria ei voi ratkaista | `MonitoredCall` jätetään pois, ajoneuvo julkaistaan |
| Sijainteja saatiin, mutta yhtään ei julkaistu | Kierros on `partial` |

## 3. Käyttö

- Rajapinta: [rata.digitraffic.fi/api/v1/siri/vm](https://rata.digitraffic.fi/api/v1/siri/vm) (beta: [rata-beta.digitraffic.fi](https://rata-beta.digitraffic.fi/api/v1/siri/vm)). OpenAPI: [openapi.json](https://rata.digitraffic.fi/swagger/openapi.json).
- Vastaus kantaa headerit `x-is-fresh` (alle 5 minuuttia vanha), `x-timestamp` ja sisällön pituuden. Vastauksen saa välimuistittaa 30 sekuntia.
- Asetukset: `updater.siri.vm.enabled`, `avoindataserver.siri.vm.enabled`, `updater.siri.vm.fixed-rate-ms` (oletus 60000). Liput ovat riippumattomia ET:n ja SX:n lipuista.

## 4. Kentät

Tila: ✅ mukana, ❌ ei toteutettu. Kardinaliteetti on profiilista (`1:1` pakollinen, `0:1` valinnainen). Jokainen ❌-kenttä on tarkistettu päivittäjän käytössä olevaa dataa vasten; yhdellekään ei ole nyt valmista lähdettä.

### 4.1 Toimitus

| Kenttä | Kard. | Tila | Arvo |
| --- | --- | --- | --- |
| `Siri/@version` | | ✅ | Vakio `2.0` |
| `ServiceDelivery/ResponseTimestamp` | 1:1 | ✅ | Generointihetki |
| `ServiceDelivery/ProducerRef` | 1:1 | ✅ | Vakio `FTR` |
| `VehicleMonitoringDelivery/@version` | | ✅ | Vakio `2.0` |
| `VehicleMonitoringDelivery/ResponseTimestamp` | 1:1 | ✅ | Generointihetki |

### 4.2 Ajoneuvo — `VehicleActivity`

| Kenttä | Kard. | Tila | Arvo |
| --- | --- | --- | --- |
| `RecordedAtTime` | 1:1 | ✅ | GPS-pisteen aika, junakohtainen |
| `ValidUntilTime` | 1:1 | ✅ | Tallennusaika + 5 minuuttia |
| `MonitoredVehicleJourney/LineRef` | 1:1 | ✅ | Julkaistun matkan linja, sama kuin NeTEx |
| `…/DirectionRef` | 0:1 | ❌ | VM:ssä valinnainen (toisin kuin ET:ssä), joten jätetään pois merkityksettömän `0`:n sijaan |
| `…/FramedVehicleJourneyRef` | 0:1 | ✅ | `DataFrameRef` = lähtöpäivä, `DatedVehicleJourneyRef` = vuoron tunniste, sama kuin NeTEx |
| `…/VehicleJourneyRef` | 0:1 | ➖ | Ei käytössä, käytetään kehystettyä muotoa |
| `…/VehicleMode` | 0:1 | ✅ | Vakio `rail` |
| `…/OperatorRef` | 0:1 | ✅ | Julkaistun matkan liikennöitsijä, sama kuin NeTEx |
| `…/OriginRef`, `OriginName` | 0:1 | ✅ | Matkan ensimmäinen pysäkki: suunniteltu `FSR:Quay` ja aseman nimi |
| `…/DestinationRef`, `DestinationName` | 0:1 | ✅ | Matkan viimeinen pysäkki: suunniteltu `FSR:Quay` ja aseman nimi |
| `…/Monitored` | 0:1 | ✅ | Vakio `true` (sijainti on olemassa) |
| `…/DataSource` | 1:1 | ✅ | Vakio `FTR` |
| `…/VehicleLocation` (`Longitude`, `Latitude`) | 1:1 | ✅ | GPS-piste, WGS84 (EPSG:4326) |
| `…/Bearing` | 0:1 | ❌ | Sijaintidatassa ei ole suuntaa, vain sijainti ja nopeus |
| `…/Velocity` | 0:1 | ✅ | Nopeus kokonaisina m/s |
| `…/Occupancy` | 0:1 | ❌ | Ei matkustajadataa |
| `…/Delay` | 1:1 | ✅ | Kirjoitetaan aina. `PT0S`, jos viivettä ei tiedetä |
| `…/InCongestion` | 0:1 | ✅ | `true` vain kun viive on merkitty tuntemattomaksi (ks. 1.4 kohta 8), muuten ei kirjoiteta |
| `…/VehicleStatus` | 0:1 | ❌ | Datassa ei ole käyttötilaa (vika, ei liikenteessä) |
| `…/ProgressBetweenStops` | 0:1 | ❌ | Ks. 5 |
| `…/VehicleRef` | 1:1 | ✅ | Junanumero |
| `…/MonitoredCall` | 0:1 | ✅ | Vain seuraava pysäkki. Jätetään pois, jos laituria ei voi ratkaista |
| `…/MonitoredCall/StopPointRef` | 1:1 | ✅ | Seuraavan pysäkin PETI `FSR:Quay` |
| `…/MonitoredCall/StopPointName` | 0:1 | ✅ | Aseman nimi |
| `…/MonitoredCall/VehicleAtStop` | 0:1 | ✅ | Ks. 1.4 kohta 6 |
| `…/MonitoredCall/VehicleLocationAtStop` | 0:1 | ✅ | GPS-piste, vain kun ollaan pysäkillä |
| `…/MonitoredCall/DestinationDisplay` | 0:1 | ✅ | Matkan viimeisen pysäkin nimi |
| `…/IsCompleteStopSequence` | 1:1 | ✅ | Aina `false`, koska raportoidaan vain yksi pysäkki |

## 5. Tunnetut rajoitukset ja avoimet asiat

- **`ProgressBetweenStops` puuttuu.** Etäisyys edellisestä ja seuraavaan pysäkkiin vaatisi ratakilometrisijainnin (`ratakmsijainti`), jota ei lueta tänään eikä ole tiedossa onko se tuotannossa luotettava. Suoraviivainen GPS-arvio hylättiin, koska se ohittaa kaarteet ja tunnelit ja antaisi julkiseen dokumenttiin harhaanjohtavia lukuja. Palataan asiaan, jos `ratakmsijainti` osoittautuu luotettavaksi tai asema–ratakilometri-viite löytyy.
- **`InCongestion` on päätös, ei ruuhkatieto.** Ks. 1.4 kohta 8.
- **Matkustajajunien rajaus** (junanumero < 2000 tai 8000–9999) on väliaikainen sääntö.
- Päätepysäkin laitturia ei välttämättä ratkea, jos PETI ei tunne asemaa. Se on dataongelma PETI:n ylläpitäjille.
- SIRI-vastausten muotoja ei ole vielä dokumentoitu [digitraffic.fi](https://www.digitraffic.fi/):n SIRI-sivulla.

## 6. Seuranta

- Lokirivi per kierros: `event=rail.siri.generation`, `rail.siri.service=vm`, `outcome=success|partial|error`.
- `partial`, kun sijainteja saatiin mutta yhtään ei julkaistu.
- Laskurit: `rail.siri.locations.received` ja `rail.siri.activities.emitted`. Erotus on sijainnit, joiden junalla ei ole julkaistua matkaa.
- NeTEx-lähde: `rail.siri.journey_source.dataset_version`, `rail.siri.journey_source.age` ja virhetilanteessa `rail.siri.journey_source.unavailable_reason` (`MISSING`, `EMPTY`, `STALE`, `PETI_EMPTY`).

## 7. Viitteet

- [Nordic SIRI-VM](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370425/SIRI-VM)
- [Enturin SIRI-VM-esimerkit](https://github.com/entur/profile-norway-examples/tree/master/siri/vehicle-monitoring)
- [Nordic SIRI -profiili — General information SIRI](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373/General+information+SIRI)
